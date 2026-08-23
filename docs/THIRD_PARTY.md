# Third-party software and licenses

Quest Task Manager (QTaskMgr) is distributed under the [MIT License](../LICENSE).
This document lists external components, their licenses, and how they relate to this project.

---

## Runtime dependencies (included in APK)

| Component | Version | License | Homepage |
|-----------|---------|---------|----------|
| [Kadb](https://github.com/flyfishxu/Kadb) | 2.1.1 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | https://github.com/flyfishxu/Kadb |
| AndroidX Core KTX | 1.12.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX AppCompat | 1.6.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Material Components for Android | 1.11.0 | Apache-2.0 | https://github.com/material-components/material-components-android |
| AndroidX ConstraintLayout | 2.1.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX ViewPager2 | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Fragment KTX | 1.6.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX RecyclerView | 1.3.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Lifecycle Runtime KTX | 2.7.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Kotlin Coroutines Android | 1.10.2 | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) | https://github.com/Kotlin/kotlinx.coroutines |
| Bouncy Castle (transitive via Kadb) | 1.83 | [MIT](https://www.bouncycastle.org/licence.html) | https://www.bouncycastle.org/ |

### Apache License 2.0 — summary

You may use, modify, and distribute these libraries in commercial and non-commercial
products. You must retain copyright notices and include a copy of the Apache-2.0
license (or a NOTICE file referencing it) in distributions that include them.

Full text: https://www.apache.org/licenses/LICENSE-2.0

---

## External apps (not bundled; user installs separately)

| App | Role | License (their project) | Notes |
|-----|------|-------------------------|-------|
| [No More Background](https://github.com/adil192/no_more_background) | — | GPL-3.0-or-later | **Not included.** QTaskMgr does not ship NMB code. Background policy uses the same public Android APIs (`appops`, `netpolicy`) documented in Android and in NMB’s README as the manual method. |

---

## Inspiration and compatibility (no code copied)

- **No More Background** ([adil192/no_more_background](https://github.com/adil192/no_more_background)) — GPL-3.0-or-later Flutter app. QTaskMgr is an independent Kotlin application. Using the same Android system settings does not make QTaskMgr a derivative work of NMB.
- **Quest Task Killer (QTKiller)** — separate MIT project by the same author. Optional; QTaskMgr v1.1+ includes its own notification-based rules cleanup.

---

## Trademarks

- **Meta**, **Meta Quest**, and **Horizon OS** are trademarks of Meta Platforms, Inc.
- **Android** is a trademark of Google LLC.
This project is not affiliated with Meta, Google, or the authors of No More Background.

---

## Build tools (not shipped in APK)

| Tool | License |
|------|---------|
| Gradle | Apache-2.0 |
| Kotlin compiler | Apache-2.0 |
| Android Gradle Plugin | Apache-2.0 |

---

## Attribution in distributions

When redistributing QTaskMgr (source or APK):

1. Include [LICENSE](../LICENSE) (MIT).
2. Include [NOTICE](../NOTICE) and this file (or equivalent third-party credits).
3. Do not imply endorsement by Meta, Google, RikkaApps, or adil192.

---

*Last updated for QTaskMgr Standalone v2.2.0*
