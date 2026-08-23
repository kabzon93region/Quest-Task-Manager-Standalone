package com.quest3.taskmanager.shell

interface ShellBackend {
    val id: String
    fun isReady(): Boolean
    fun run(command: String, timeoutSec: Long = 25): ShellResult
}
