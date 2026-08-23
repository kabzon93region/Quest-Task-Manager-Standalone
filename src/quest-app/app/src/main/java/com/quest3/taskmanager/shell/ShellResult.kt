package com.quest3.taskmanager.shell

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val combined: String get() = if (stderr.isBlank()) stdout else "$stdout\n$stderr"
}
