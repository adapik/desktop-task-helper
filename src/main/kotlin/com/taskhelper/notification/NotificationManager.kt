package com.taskhelper.notification

import com.taskhelper.model.ItemSource
import com.taskhelper.model.TaskItem
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class NotificationManager(private val onNotificationClick: (String) -> Unit) {

    private val notifiedIds = mutableSetOf<String>()

    // Linux: notify-send
    private val notifySendAvailable: Boolean by lazy {
        try {
            ProcessBuilder("which", "notify-send").start().waitFor() == 0
        } catch (_: Exception) {
            false
        }.also { available ->
            if (!available) System.err.println("notify-send not found. Desktop notifications disabled.")
        }
    }

    // macOS: bundled native Swift notifier app extracted from JAR resources.
    // Uses bundle ID com.taskhelper (same as the main app) so existing notification
    // permissions apply without requiring a separate permission dialog.
    // Launched via `open -n` (LaunchServices) so UNUserNotificationCenter works correctly.
    private val macNotifierApp: String? by lazy { extractMacNotifier() }

    private fun extractMacNotifier(): String? {
        val binaryResource = javaClass.getResourceAsStream("/notifier-bin/notifier") ?: run {
            System.err.println("Bundled notifier binary not found in resources.")
            return null
        }
        val plistResource = javaClass.getResourceAsStream("/notifier-bin/notifier-Info.plist") ?: run {
            System.err.println("Bundled notifier Info.plist not found in resources.")
            return null
        }
        return try {
            // UNUserNotificationCenter requires a proper .app bundle on disk.
            // macOS identifies the bundle by traversing up from the binary path to find Contents/Info.plist.
            val contentsDir = File(System.getProperty("java.io.tmpdir"),
                "desktop-task-helper/Notifier.app/Contents")
            val macosDir     = File(contentsDir, "MacOS")
            val resourcesDir = File(contentsDir, "Resources")
            macosDir.mkdirs()
            resourcesDir.mkdirs()

            val binary = File(macosDir, "notifier")
            binaryResource.use { src -> binary.outputStream().use { src.copyTo(it) } }
            binary.setExecutable(true, false)

            plistResource.use { src ->
                File(contentsDir, "Info.plist").outputStream().use { src.copyTo(it) }
            }

            // Build AppIcon.icns so notifications display the Task Helper icon.
            buildNotifierIcon(resourcesDir)

            // Re-sign the bundle with an ad-hoc signature. swiftc signs the binary
            // for the location it was compiled in; moving it into a .app bundle
            // invalidates that signature and Gatekeeper refuses to launch the app.
            ProcessBuilder(
                "codesign", "--force", "--deep", "--sign", "-",
                contentsDir.parentFile.absolutePath
            ).start().waitFor()

            // Return the .app bundle path — must be launched via `open -n` so that
            // LaunchServices properly registers the process with UNUserNotificationCenter.
            contentsDir.parentFile.absolutePath
        } catch (e: Exception) {
            System.err.println("Failed to extract notifier bundle: ${e.message}")
            null
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
            ItemSource.JIRA   -> "Jira: Attention needed"
            ItemSource.GITHUB -> "GitHub: Review requested"
        }
        val body = "${item.id}: ${item.title}"

        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("linux") -> sendLinuxNotification(title, body, item.url)
            os.contains("mac")   -> sendMacNotification(title, body, item.url, item.id)
        }
    }

    private fun sendLinuxNotification(title: String, body: String, url: String) {
        if (!notifySendAvailable) return

        Thread({
            try {
                val process = ProcessBuilder(
                    "notify-send", title, body,
                    "-u", "normal",
                    "--action=default=Open",
                    "--wait"
                ).start()

                val action = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()

                if (action == "default") onNotificationClick(url)
            } catch (_: Exception) {}
        }, "notification-${url.hashCode()}").apply {
            isDaemon = true
            start()
        }
    }

    private fun sendMacNotification(title: String, body: String, url: String, groupId: String) {
        val appBundle = macNotifierApp ?: run {
            sendMacPlainNotification(title, body)
            return
        }

        Thread({
            try {
                // Launch via `open -n` so LaunchServices registers the process properly
                // with UNUserNotificationCenter. The notifier stays alive until the user
                // clicks or dismisses the notification, then it opens the URL and exits.
                ProcessBuilder(
                    "open", "-n", appBundle,
                    "--args", title, body, url, groupId
                ).start().waitFor()
            } catch (_: Exception) {}
        }, "notification-${url.hashCode()}").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Renders the app icon at each required iconset size and runs `iconutil` to produce
     * AppIcon.icns in [resourcesDir]. macOS uses this file to show the correct icon in
     * notification banners.
     */
    private fun buildNotifierIcon(resourcesDir: File) {
        try {
            val iconsetDir = File(resourcesDir, "AppIcon.iconset").also { it.mkdirs() }
            val sizes = listOf(16, 32, 64, 128, 256, 512)
            for (sz in sizes) {
                for (scale in listOf(1, 2)) {
                    val px = sz * scale
                    val img = renderAppIcon(px)
                    val name = if (scale == 1) "icon_${sz}x${sz}.png" else "icon_${sz}x${sz}@2x.png"
                    ImageIO.write(img, "PNG", File(iconsetDir, name))
                }
            }
            ProcessBuilder(
                "iconutil", "--convert", "icns",
                "--output", File(resourcesDir, "AppIcon.icns").absolutePath,
                iconsetDir.absolutePath
            ).start().waitFor()
        } catch (_: Exception) {}  // icon is optional — notifications still work without it
    }

    private fun renderAppIcon(size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val m = size / 16
        g.color = Color(100, 160, 100)
        g.fillOval(m, m, size - m * 2, size - m * 2)
        g.color = Color.WHITE
        g.stroke = BasicStroke(size / 10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val s = size / 24.0f
        g.drawLine((7*s).toInt(), (12*s).toInt(), (10*s).toInt(), (16*s).toInt())
        g.drawLine((10*s).toInt(), (16*s).toInt(), (17*s).toInt(), (8*s).toInt())
        g.dispose()
        return img
    }

    private fun sendMacPlainNotification(title: String, body: String) {
        try {
            val safeTitle = title.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeBody  = body.replace("\\", "\\\\").replace("\"", "\\\"")
            ProcessBuilder(
                "osascript", "-e",
                "display notification \"$safeBody\" with title \"$safeTitle\""
            ).start()
        } catch (_: Exception) {}
    }
}
