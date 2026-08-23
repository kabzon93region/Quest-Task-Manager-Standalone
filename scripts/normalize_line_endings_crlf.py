#!python
"""
Нормализация окончаний строк и кодировки по правилам .gitattributes.

Правила (Torero .gitattributes):
  - .cmd / .bat / .ps1 : CRLF (требование Windows cmd.exe)
  - Все остальные текстовые файлы : LF
  - Бинарные файлы : пропуск
  - Кодировка : UTF-8 без BOM

Оптимизации:
  - os.walk вместо sorted rglob (без сбора всех путей в память)
  - ThreadPoolExecutor для параллельного I/O (по умолчанию 8 воркеров)
  - Быстрый byte-level check: файлы уже в нужном EOL пропускаются без decode
  - mtime-кэх (.eol_cache.json): тёплый прогон проверяет только изменённые файлы
  - Прогрессбар: спиннер при сканировании, бар при обработке

Запуск из корня проекта:
  python scripts/normalize_line_endings_crlf.py
  python scripts/normalize_line_endings_crlf.py --dry-run
  python scripts/normalize_line_endings_crlf.py --workers 16
  python scripts/normalize_line_endings_crlf.py --no-cache
  python scripts/normalize_line_endings_crlf.py --no-bar
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

# Корень проекта (скрипт в scripts/)
PROJECT_ROOT = Path(__file__).resolve().parent.parent

# Файл кэша mtime
CACHE_FILE = ".eol_cache.json"

# === EOL правила из .gitattributes ===
# CRLF — только Windows-скрипты (cmd.exe требует \r\n)
CRLF_EXTENSIONS = {".cmd", ".bat", ".ps1"}

# LF — всё остальное
LF_EXTENSIONS = {
    ".py", ".js", ".ts", ".tsx", ".jsx",
    ".html", ".css", ".proto",
    ".yaml", ".yml", ".json", ".toml",
    ".md", ".mdc", ".txt", ".sql", ".sh",
    ".cfg", ".ini", ".env", ".xml", ".svg",
}

# Все текстовые расширения (CRLF + LF)
TEXT_EXTENSIONS = CRLF_EXTENSIONS | LF_EXTENSIONS

# Файлы без расширения, которые тоже обрабатываем
EXTENSIONLESS_FILES = {"Makefile", "Dockerfile", ".gitignore", ".gitattributes"}

# Каталоги, которые не сканируем
SKIP_DIRS = {
    ".git", "venv", ".venv", "env", "__pycache__", ".mypy_cache",
    "node_modules", ".tox", "dist", "build", "SDK", "logs",
    ".cocoindex_code", ".ruff_cache", ".pytest_cache", ".ipynb_checkpoints",
    ".vscode", ".idea", "3.11", "release", "bin", "obj",
}

# Бинарные расширения — пропускаем
BINARY_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp",
    ".woff", ".woff2", ".ttf", ".eot",
    ".exe", ".dll", ".so", ".dylib",
    ".zip", ".tar", ".gz", ".7z", ".rar", ".vsix", ".pdf",
    ".db", ".sqlite", ".sqlite3", ".parquet",
    ".pyc", ".pyo", ".pyd",
}

# UTF-8 BOM: EF BB BF
_BOM = b"\xef\xbb\xbf"

# Максимальный размер файла (5 MiB)
MAX_FILE_SIZE = 5 * 1024 * 1024

# Символы спиннера
_SPINNER = "|/-\\"
_CLEAR_LINE = "\r\033[K"


def _enable_ansi_windows() -> None:
    """Включает ANSI escape-коды в Windows консоли."""
    if sys.platform == "win32":
        try:
            import ctypes
            kernel32 = ctypes.windll.kernel32
            kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7)
        except Exception:
            pass


def _should_skip_dir(name: str) -> bool:
    if name in SKIP_DIRS:
        return True
    if name.startswith(".") and name not in (".env", ".env.example"):
        return True
    if name.endswith(".egg-info"):
        return True
    return False


def _get_target_eol(ext: str) -> str:
    """Возвращает целевой EOL для расширения ('lf' или 'crlf')."""
    return "crlf" if ext in CRLF_EXTENSIONS else "lf"


def _has_bom(raw: bytes) -> bool:
    """Проверяет наличие UTF-8 BOM."""
    return raw[:3] == _BOM


def _is_correct_eol(raw: bytes, target_eol: str) -> bool:
    """
    Быстрая проверка на байтовом уровне:
    True если файл уже в нужном EOL.
    """
    stripped = raw.replace(b"\r\n", b"")
    if target_eol == "crlf":
        # Для CRLF: не должно быть \n или \r без пары
        return b"\n" not in stripped and b"\r" not in stripped
    else:
        # Для LF: не должно быть \r
        return b"\r" not in stripped


def _normalize_file(path: Path, target_eol: str, dry_run: bool) -> tuple[bool, bool]:
    """
    Нормализует один файл.
    Возвращает (eol_changed, bom_changed).
    """
    try:
        stat = path.stat()
    except OSError:
        return False, False

    # Пропуск пустых и слишком больших
    if stat.st_size == 0 or stat.st_size > MAX_FILE_SIZE:
        return False, False

    try:
        raw = path.read_bytes()
    except OSError:
        return False, False

    # Пропуск бинарников (нулевые байты)
    if b"\0" in raw:
        return False, False

    # Проверка BOM
    has_bom = _has_bom(raw)
    # Для LF-файлов удаляем BOM; для CRLF-файлов (.cmd/.bat/.ps1) BOM допустим
    need_strip_bom = has_bom and target_eol == "lf"

    # Проверка EOL
    eol_ok = _is_correct_eol(raw, target_eol)

    if eol_ok and not need_strip_bom:
        return False, False

    # Декодируем (utf-8-sig автоматически убирает BOM)
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        try:
            text = raw.decode("cp1251")
        except UnicodeDecodeError:
            return False, False

    eol_changed = False
    bom_changed = False

    # Конвертация EOL
    if not eol_ok:
        normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        if target_eol == "crlf":
            normalized = normalized.replace("\n", "\r\n")
        if normalized != text:
            text = normalized
            eol_changed = True

    # Удаление BOM
    if need_strip_bom and text and text[0] == "\ufeff":
        text = text[1:]
        bom_changed = True

    if not eol_changed and not bom_changed:
        return False, False

    if not dry_run:
        path.write_text(text, encoding="utf-8", newline="")
    return eol_changed, bom_changed


def _collect_files_with_mtime(root: Path) -> list[tuple[Path, float, str]]:
    """
    Сбор файлов + mtime + target_eol за один проход os.walk.
    Возвращает [(path, mtime, target_eol), ...].
    """
    files = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if not _should_skip_dir(d)]
        dirnames.sort()

        for fname in filenames:
            ext = os.path.splitext(fname)[1].lower()

            # Пропуск бинарников
            if ext in BINARY_EXTENSIONS:
                continue

            # Текстовые файлы с расширением
            if ext in TEXT_EXTENSIONS:
                target_eol = _get_target_eol(ext)
            # Файлы без расширения (Makefile, Dockerfile и т.д.)
            elif not ext and fname in EXTENSIONLESS_FILES:
                target_eol = "lf"
            else:
                continue

            full = Path(dirpath) / fname
            try:
                mtime = os.path.getmtime(full)
            except OSError:
                continue
            files.append((full, mtime, target_eol))
    return files


def _load_cache(root: Path) -> dict[str, float]:
    """Загружает mtime-кэх из .eol_cache.json."""
    cache_path = root / CACHE_FILE
    if not cache_path.exists():
        return {}
    try:
        data = json.loads(cache_path.read_text(encoding="utf-8"))
        return data.get("mtimes", {})
    except (OSError, json.JSONDecodeError, KeyError):
        return {}


def _save_cache(root: Path, mtimes: dict[str, float]) -> None:
    """Сохраняет mtime-кэх в .eol_cache.json."""
    cache_path = root / CACHE_FILE
    try:
        cache_path.write_text(
            json.dumps({"mtimes": mtimes}, indent=None, separators=(",", ":")),
            encoding="utf-8",
        )
    except OSError:
        pass


class ProgressBar:
    """
    Прогрессбар с перезаписью строки через \\r.
    """

    def __init__(self, enabled: bool = True, file=None):
        self.enabled = enabled
        self._file = file or sys.stderr
        self._lock = threading.Lock()
        self._spinner_idx = 0
        self._last_len = 0

    def _write(self, text: str) -> None:
        if not self.enabled:
            return
        with self._lock:
            pad = max(0, self._last_len - len(text))
            self._file.write(_CLEAR_LINE + text + " " * pad)
            self._file.flush()
            self._last_len = len(text)

    def spinner(self, found: int, elapsed: float) -> None:
        ch = _SPINNER[self._spinner_idx % len(_SPINNER)]
        self._spinner_idx += 1
        self._write(f"[{ch}] Найдено файлов: {found} ({elapsed:.1f}s)")

    def bar(self, done: int, total: int, changed: int, elapsed: float) -> None:
        if total == 0:
            return
        pct = done / total
        width = 30
        filled = int(width * pct)
        bar_str = "#" * filled + "." * (width - filled)
        self._write(
            f"[{bar_str}] {pct * 100:5.1f}% "
            f"| {done}/{total} "
            f"| +{changed} fixed "
            f"({elapsed:.1f}s)"
        )

    def finish_line(self) -> None:
        if self.enabled:
            self._file.write("\n")
            self._file.flush()
            self._last_len = 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Нормализация EOL по правилам .gitattributes (LF/CRLF)"
    )
    parser.add_argument("--dry-run", action="store_true", help="Только показать файлы, не менять")
    parser.add_argument("--root", default=None, help="Корень для сканирования")
    parser.add_argument("-q", "--quiet", action="store_true", help="Минимум вывода")
    parser.add_argument("-w", "--workers", type=int, default=8, help="Потоков I/O (по умолчанию 8)")
    parser.add_argument("--no-cache", action="store_true", help="Игнорировать mtime-кэх")
    parser.add_argument("--no-bar", action="store_true", help="Без прогрессбара")
    args = parser.parse_args()

    root = Path(args.root).resolve() if args.root else PROJECT_ROOT
    if not root.is_dir():
        print(f"Каталог не найден: {root}", file=sys.stderr)
        return 1

    _enable_ansi_windows()

    use_bar = not args.no_bar and not args.quiet and sys.stderr.isatty()
    bar = ProgressBar(enabled=use_bar)

    print(f"Корень сканирования: {root}")
    print(f"CRLF: {', '.join(sorted(CRLF_EXTENSIONS))}")
    print(f"LF:   {', '.join(sorted(LF_EXTENSIONS))}")
    print(f"Потоков: {args.workers}")

    # Загружаем mtime-кэх
    cache = {} if args.no_cache else _load_cache(root)
    use_cache = bool(cache) and not args.no_cache
    if use_cache:
        print(f"Кэх загружен: {len(cache)} записей")

    # === Этап 1: Сканирование ===
    if use_bar:
        bar._write("[>] Сканирование файлов...")
    else:
        print("Сканирование файлов...", flush=True)

    t0 = time.time()
    scan_done = threading.Event()

    def _scan_spinner():
        idx = 0
        while not scan_done.is_set():
            ch = _SPINNER[idx % len(_SPINNER)]
            idx += 1
            elapsed = time.time() - t0
            bar._write(f"[{ch}] Сканирование... ({elapsed:.1f}s)")
            scan_done.wait(0.1)

    if use_bar:
        spinner_thread = threading.Thread(target=_scan_spinner, daemon=True)
        spinner_thread.start()

    all_files = _collect_files_with_mtime(root)
    t_scan = time.time() - t0
    scan_done.set()

    if use_bar:
        bar._write(f"[OK] Найдено файлов: {len(all_files)} ({t_scan:.1f}s)")
        bar.finish_line()
    else:
        print(f"Найдено файлов: {len(all_files)} ({t_scan:.1f}s)", flush=True)

    if not all_files:
        print("Нет файлов для проверки.")
        return 0

    # Фильтруем по mtime-кэху
    if use_cache:
        files_to_check = []
        skipped_cached = 0
        for p, mtime, target_eol in all_files:
            rel = str(p.relative_to(root))
            cached_mtime = cache.get(rel)
            if cached_mtime is not None and abs(cached_mtime - mtime) < 0.001:
                skipped_cached += 1
            else:
                files_to_check.append((p, target_eol))
        print(f"Пропуск по кэху: {skipped_cached}, к проверке: {len(files_to_check)}")
    else:
        files_to_check = [(p, te) for p, _, te in all_files]

    if not files_to_check:
        print("Все файлы уже нормализованы (кэх актуален).")
        return 0

    # === Этап 2: Обработка ===
    if use_bar:
        bar._write(f"[>] Обработка {len(files_to_check)} файлов...")
        bar.finish_line()

    changed: list[Path] = []
    checked = 0
    lock = threading.Lock()
    t0 = time.time()

    def _process(item: tuple[Path, str]) -> tuple[bool, Path | None]:
        path, target_eol = item
        try:
            eol_ok, bom_ok = _normalize_file(path, target_eol, args.dry_run)
            if eol_ok or bom_ok:
                return True, path
        except Exception as e:
            print(f"\nОшибка {path}: {e}", file=sys.stderr)
        return False, None

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(_process, item): item for item in files_to_check}
        for future in as_completed(futures):
            ok, path = future.result()
            with lock:
                checked += 1
                if ok and path is not None:
                    changed.append(path)
                if use_bar:
                    if checked % 50 == 0 or ok:
                        bar.bar(checked, len(files_to_check), len(changed), time.time() - t0)
                elif not args.quiet and checked % 100 == 0:
                    pct = checked / len(files_to_check) * 100
                    print(f"  {checked}/{len(files_to_check)} ({pct:.0f}%)", flush=True)

    t_write = time.time() - t0

    if use_bar:
        bar.bar(len(files_to_check), len(files_to_check), len(changed), t_write)
        bar.finish_line()

    # Обновляем mtime-кэх
    if not args.dry_run:
        new_cache = {str(p.relative_to(root)): mtime for p, mtime, _ in all_files}
        _save_cache(root, new_cache)

    print()
    if args.dry_run:
        print("Режим --dry-run: файлы не изменялись.")
    print(f"Проверено файлов: {checked} ({t_write:.1f}s)")
    print(f"Исправлено: {len(changed)}")
    if changed:
        changed.sort()
        if args.quiet or len(changed) <= 30:
            for p in changed:
                try:
                    print(f"  {p.relative_to(root)}")
                except ValueError:
                    print(f"  {p}")
        else:
            print("Список изменённых файлов см. выше по выводу.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
