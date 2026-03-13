package com.taskhelper.notification

import com.taskhelper.model.ItemSource
import com.taskhelper.model.TaskItem
import java.awt.TrayIcon

class NotificationManager(private val trayIcon: TrayIcon? = null) {

    private val notifiedIds = mutableSetOf<String>()

    fun notifyNewItems(currentItems: List<TaskItem>, previousItems: List<TaskItem>) {
        val previousIds = previousItems.map { it.id }.toSet()
        val newItems = currentItems.filter { it.id !in previousIds && it.id !in notifiedIds }

        newItems.forEach { item ->
            notifiedIds.add(item.id)
            sendNotification(item)
        }

        // Clean up IDs that are no longer in the current list
        val currentIds = currentItems.map { it.id }.toSet()
        notifiedIds.removeAll { it !in currentIds }
    }

    private fun sendNotification(item: TaskItem) {
        val title = when (item.source) {
            ItemSource.JIRA -> "Jira: Attention needed"
            ItemSource.GITHUB -> "GitHub: Review requested"
        }

        // Try native notification first (works better on Linux), fall back to AWT
        if (!sendNativeNotification(title, item.menuLabel)) {
            sendAwtNotification(title, item.menuLabel)
        }
    }

    private fun sendNativeNotification(title: String, message: String): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            val process = when {
                os.contains("linux") -> ProcessBuilder("notify-send", title, message, "-u", "normal")
                os.contains("mac") -> ProcessBuilder(
                    "osascript", "-e",
                    "display notification \"$message\" with title \"$title\""
                )
                else -> return false
            }
            process.start()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun sendAwtNotification(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }
}
