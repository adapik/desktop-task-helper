package com.taskhelper

import com.taskhelper.config.Config
import com.taskhelper.config.SettingsDialog
import com.taskhelper.notification.NotificationManager
import com.taskhelper.service.PollingService
import com.taskhelper.tray.TrayManager
import kotlinx.coroutines.*
import java.awt.Desktop

private lateinit var trayManager: TrayManager

fun main() {
    System.setProperty("java.awt.headless", "false")

    var config = Config.load()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val notificationManager = NotificationManager()
    var pollingService: PollingService? = null

    trayManager = TrayManager(
        onItemClick = { item -> openInBrowser(item.url) },
        onSettings = {
            SettingsDialog(config) { newConfig ->
                config = newConfig
                pollingService?.stop()
                pollingService = PollingService(newConfig, trayManager, notificationManager)
                pollingService!!.start(scope)
                println("Settings saved and applied.")
            }.show()
        },
        onQuit = {
            scope.cancel()
            System.exit(0)
        }
    )

    trayManager.init()

    trayManager.onRefreshListener = {
        scope.launch { pollingService?.poll() }
    }

    pollingService = PollingService(config, trayManager, notificationManager)
    pollingService.start(scope)

    println("Desktop Task Helper is running. Check the system tray.")
    Thread.currentThread().join()
}

private fun openInBrowser(url: String) {
    try {
        val os = System.getProperty("os.name").lowercase()
        when {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) -> {
                Desktop.getDesktop().browse(java.net.URI(url))
            }
            os.contains("linux") -> {
                Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            }
            os.contains("mac") -> {
                Runtime.getRuntime().exec(arrayOf("open", url))
            }
        }
    } catch (e: Exception) {
        System.err.println("Failed to open browser: ${e.message}")
    }
}
