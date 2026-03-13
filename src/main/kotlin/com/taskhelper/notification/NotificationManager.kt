package com.taskhelper.notification

import com.taskhelper.model.ItemSource
import com.taskhelper.model.TaskItem

class NotificationManager(private val onNotificationClick: (String) -> Unit) {

    private val notifiedIds = mutableSetOf<String>()
    private val notifySendAvailable: Boolean by lazy {
        try {
            ProcessBuilder("which", "notify-send").start().waitFor() == 0
        } catch (_: Exception) {
            false
        }.also { available ->
            if (!available) System.err.println("notify-send not found. Desktop notifications disabled.")
        }
    }

    fun notifyNewItems(currentItems: List<TaskItem>, previousItems: List<TaskItem>) {
        val previousIds = previousItems.map { it.id }.toSet()
        val newItems = currentItems.filter { it.id !in previousIds && it.id !in notifiedIds }

        newItems.forEach { item ->
            notifiedIds.add(item.id)
            sendNotification(item)
        }

        val currentIds = currentItems.map { it.id }.toSet()
        notifiedIds.removeAll { it !in currentIds }
    }

    private fun sendNotification(item: TaskItem) {
        val title = when (item.source) {
            ItemSource.JIRA -> "Jira: Attention needed"
            ItemSource.GITHUB -> "GitHub: Review requested"
        }
        val body = "${item.id}: ${item.title}"

        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("linux") -> sendLinuxNotification(title, body, item.url)
            os.contains("mac") -> sendMacNotification(title, body, item.url)
        }
    }

    private fun sendLinuxNotification(title: String, body: String, url: String) {
        if (!notifySendAvailable) return

        Thread({
            try {
                val process = ProcessBuilder(
                    "notify-send",
                    title, body,
                    "-u", "normal",
                    "--action=default=Open",
                    "--wait"
                ).start()

                val action = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()

                if (action == "default") {
                    onNotificationClick(url)
                }
            } catch (_: Exception) {}
        }, "notification-${url.hashCode()}").apply {
            isDaemon = true
            start()
        }
    }

    private fun sendMacNotification(title: String, body: String, url: String) {
        try {
            ProcessBuilder(
                "osascript", "-e",
                "display notification \"$body\" with title \"$title\""
            ).start()
        } catch (_: Exception) {}
    }
}
