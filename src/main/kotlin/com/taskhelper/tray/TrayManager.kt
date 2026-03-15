package com.taskhelper.tray

import com.taskhelper.model.ItemSource
import com.taskhelper.model.TaskItem
import com.sun.jna.*
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.UIManager

class TrayManager(
    private val onItemClick: (TaskItem) -> Unit,
    private val onSettings: () -> Unit,
    private val onQuit: () -> Unit
) {

    private var currentItems = emptyList<TaskItem>()
    private val iconDir = File(System.getProperty("java.io.tmpdir"), "taskhelper-icons").apply { mkdirs() }
    private var useNativeTray = false

    // Native AppIndicator via JNA (Linux only)
    private var indicator: Pointer? = null
    private var gtkMenu: Pointer? = null

    // AWT SystemTray (macOS and fallback)
    private var trayIcon: TrayIcon? = null

    fun init() {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        saveIcon(0)

        useNativeTray = tryInitAppIndicator()
        if (!useNativeTray) {
            initAwtTray()
        }
    }

    fun update(items: List<TaskItem>) {
        currentItems = items
        saveIcon(items.size)

        if (useNativeTray) updateAppIndicator(items)
        else updateAwtTray(items)
    }

    // ========== Native AppIndicator (Linux) ==========

    private fun tryInitAppIndicator(): Boolean {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return false
        return try {
            val gtk = Gtk.INSTANCE
            val appInd = AppIndicator.INSTANCE

            gtk.gtk_init(0, null)

            val iconPath = File(iconDir, "icon-0.png").absolutePath
            indicator = appInd.app_indicator_new(
                "desktop-task-helper",
                iconPath,
                0 // APP_INDICATOR_CATEGORY_APPLICATION_STATUS
            )

            appInd.app_indicator_set_status(indicator, 1) // APP_INDICATOR_STATUS_ACTIVE

            gtkMenu = gtk.gtk_menu_new()
            rebuildGtkMenu(emptyList())
            appInd.app_indicator_set_menu(indicator, gtkMenu)

            Thread({
                gtk.gtk_main()
            }, "gtk-main").apply {
                isDaemon = true
                start()
            }

            println("Using native AppIndicator for system tray")
            true
        } catch (e: Exception) {
            System.err.println("AppIndicator not available, falling back to AWT: ${e.message}")
            false
        }
    }

    private fun updateAppIndicator(items: List<TaskItem>) {
        val gtk = Gtk.INSTANCE
        val func = GSourceFunc { _ ->
            val iconPath = File(iconDir, "icon-${items.size}.png").absolutePath
            AppIndicator.INSTANCE.app_indicator_set_icon(indicator, iconPath)
            rebuildGtkMenu(items)
            0 // G_SOURCE_REMOVE
        }
        idleFuncRefs.add(func)
        gtk.g_idle_add(func, null)
    }

    private val idleFuncRefs = mutableListOf<GSourceFunc>()

    private fun rebuildGtkMenu(items: List<TaskItem>) {
        val gtk = Gtk.INSTANCE
        val appInd = AppIndicator.INSTANCE

        gtkMenu = gtk.gtk_menu_new()

        val jiraItems = items.filter { it.source == ItemSource.JIRA }
        val githubItems = items.filter { it.source == ItemSource.GITHUB }

        if (jiraItems.isNotEmpty()) {
            val header = gtk.gtk_menu_item_new_with_label("Jira Tasks (${jiraItems.size})")
            gtk.gtk_widget_set_sensitive(header, 0)
            gtk.gtk_menu_shell_append(gtkMenu, header)

            jiraItems.forEach { item ->
                val label = "${item.id}  \u2014  ${truncate(item.title, 55)}"
                val menuItem = gtk.gtk_menu_item_new_with_label(label)
                val callback = GtkCallback { _, _ -> Thread { onItemClick(item) }.start() }
                gtk.g_signal_connect_data(menuItem, "activate", callback, null, null, 0)
                callbackRefs.add(callback)
                gtk.gtk_menu_shell_append(gtkMenu, menuItem)
            }
        }

        if (githubItems.isNotEmpty()) {
            if (jiraItems.isNotEmpty()) {
                gtk.gtk_menu_shell_append(gtkMenu, gtk.gtk_separator_menu_item_new())
            }
            val header = gtk.gtk_menu_item_new_with_label("PR Reviews (${githubItems.size})")
            gtk.gtk_widget_set_sensitive(header, 0)
            gtk.gtk_menu_shell_append(gtkMenu, header)

            githubItems.forEach { item ->
                val label = "${item.id}  \u2014  ${truncate(item.title, 55)}"
                val menuItem = gtk.gtk_menu_item_new_with_label(label)
                val callback = GtkCallback { _, _ -> Thread { onItemClick(item) }.start() }
                gtk.g_signal_connect_data(menuItem, "activate", callback, null, null, 0)
                callbackRefs.add(callback)
                gtk.gtk_menu_shell_append(gtkMenu, menuItem)
            }
        }

        if (items.isEmpty()) {
            val empty = gtk.gtk_menu_item_new_with_label("No items need attention")
            gtk.gtk_widget_set_sensitive(empty, 0)
            gtk.gtk_menu_shell_append(gtkMenu, empty)
        }

        gtk.gtk_menu_shell_append(gtkMenu, gtk.gtk_separator_menu_item_new())

        val refresh = gtk.gtk_menu_item_new_with_label("Refresh")
        val refreshCb = GtkCallback { _, _ -> onRefreshListener?.invoke() }
        gtk.g_signal_connect_data(refresh, "activate", refreshCb, null, null, 0)
        callbackRefs.add(refreshCb)
        gtk.gtk_menu_shell_append(gtkMenu, refresh)

        val settings = gtk.gtk_menu_item_new_with_label("Settings")
        val settingsCb = GtkCallback { _, _ -> Thread { onSettings() }.start() }
        gtk.g_signal_connect_data(settings, "activate", settingsCb, null, null, 0)
        callbackRefs.add(settingsCb)
        gtk.gtk_menu_shell_append(gtkMenu, settings)

        gtk.gtk_menu_shell_append(gtkMenu, gtk.gtk_separator_menu_item_new())

        val quit = gtk.gtk_menu_item_new_with_label("Quit")
        val quitCb = GtkCallback { _, _ -> onQuit() }
        gtk.g_signal_connect_data(quit, "activate", quitCb, null, null, 0)
        callbackRefs.add(quitCb)
        gtk.gtk_menu_shell_append(gtkMenu, quit)

        gtk.gtk_widget_show_all(gtkMenu)
        appInd.app_indicator_set_menu(indicator, gtkMenu)
    }

    private val callbackRefs = mutableListOf<GtkCallback>()

    // ========== AWT SystemTray (macOS and fallback) ==========
    //
    // On macOS, AWT's TrayIcon is backed by a real NSStatusItem, and the attached
    // PopupMenu is converted to a native NSMenu. This means the menu auto-dismisses
    // on outside click just like any native macOS menu — no JNA/ObjC wrangling needed.

    private fun initAwtTray() {
        if (!SystemTray.isSupported()) {
            error("System tray is not supported on this platform")
        }

        val popup = PopupMenu()
        trayIcon = TrayIcon(loadIcon(0), "Task Helper", popup).apply {
            isImageAutoSize = true
        }
        SystemTray.getSystemTray().add(trayIcon!!)
        rebuildAwtPopup(emptyList())
        println("Using AWT SystemTray")
    }

    private fun updateAwtTray(items: List<TaskItem>) {
        trayIcon?.image = loadIcon(items.size)
        trayIcon?.toolTip = if (items.isEmpty()) {
            "Task Helper \u2014 No items"
        } else {
            "Task Helper \u2014 ${items.size} item(s) need attention"
        }
        // Rebuild on EDT — required for AWT component modifications.
        javax.swing.SwingUtilities.invokeLater { rebuildAwtPopup(items) }
    }

    private fun rebuildAwtPopup(items: List<TaskItem>) {
        val popup = trayIcon?.popupMenu ?: return
        popup.removeAll()

        val jiraItems = items.filter { it.source == ItemSource.JIRA }
        val githubItems = items.filter { it.source == ItemSource.GITHUB }

        if (jiraItems.isNotEmpty()) {
            popup.add(MenuItem("Jira Tasks (${jiraItems.size})").apply { isEnabled = false })
            jiraItems.forEach { item ->
                popup.add(MenuItem("   ${item.id}  \u2014  ${truncate(item.title, 55)}").apply {
                    addActionListener { Thread { onItemClick(item) }.start() }
                })
            }
        }

        if (githubItems.isNotEmpty()) {
            if (jiraItems.isNotEmpty()) popup.addSeparator()
            popup.add(MenuItem("PR Reviews (${githubItems.size})").apply { isEnabled = false })
            githubItems.forEach { item ->
                popup.add(MenuItem("   ${item.id}  \u2014  ${truncate(item.title, 55)}").apply {
                    addActionListener { Thread { onItemClick(item) }.start() }
                })
            }
        }

        if (items.isEmpty()) {
            popup.add(MenuItem("No items need attention").apply { isEnabled = false })
        }

        popup.addSeparator()
        popup.add(MenuItem("Refresh").apply { addActionListener { onRefreshListener?.invoke() } })
        popup.add(MenuItem("Settings").apply { addActionListener { Thread { onSettings() }.start() } })
        popup.addSeparator()
        popup.add(MenuItem("Quit").apply { addActionListener { onQuit() } })
    }

    // ========== Icon rendering ==========

    private fun saveIcon(count: Int) {
        val image = renderIcon(count, 128)
        val file = File(iconDir, "icon-$count.png")
        ImageIO.write(image, "PNG", file)
    }

    private fun loadIcon(count: Int): Image {
        return renderIcon(count, 128)
    }

    private fun renderIcon(count: Int, size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        val margin = 4
        val diameter = size - margin * 2

        g2d.color = if (count > 0) Color(220, 50, 50) else Color(100, 160, 100)
        g2d.fillOval(margin, margin, diameter, diameter)

        if (count > 0) {
            val text = if (count > 99) "99+" else count.toString()
            val fontSize = when {
                text.length >= 3 -> (size * 0.45f).toInt()
                text.length == 2 -> (size * 0.55f).toInt()
                else -> (size * 0.65f).toInt()
            }
            g2d.font = Font("SansSerif", Font.BOLD, fontSize)
            g2d.color = Color.WHITE
            val gv = g2d.font.createGlyphVector(g2d.fontRenderContext, text)
            val vb = gv.visualBounds
            val x = ((size - vb.width) / 2.0 - vb.x).toFloat()
            val y = ((size - vb.height) / 2.0 - vb.y).toFloat()
            g2d.drawGlyphVector(gv, x, y)
        } else {
            g2d.color = Color.WHITE
            g2d.stroke = BasicStroke(size / 10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val s = size / 24.0f
            g2d.drawLine((7 * s).toInt(), (12 * s).toInt(), (10 * s).toInt(), (16 * s).toInt())
            g2d.drawLine((10 * s).toInt(), (16 * s).toInt(), (17 * s).toInt(), (8 * s).toInt())
        }

        g2d.dispose()
        return image
    }

    private fun truncate(text: String, max: Int): String {
        return if (text.length > max) text.take(max - 1) + "\u2026" else text
    }

    var onRefreshListener: (() -> Unit)? = null

    fun destroy() {
        when {
            useNativeTray -> try { Gtk.INSTANCE.gtk_main_quit() } catch (_: Exception) {}
            else -> trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        }
    }

    // ========== JNA interfaces (Linux GTK only) ==========

    fun interface GtkCallback : Callback {
        fun invoke(widget: Pointer?, data: Pointer?)
    }

    fun interface GSourceFunc : Callback {
        fun invoke(data: Pointer?): Int
    }

    interface Gtk : Library {
        companion object {
            val INSTANCE: Gtk = Native.load("gtk-3", Gtk::class.java)
        }

        fun gtk_init(argc: Int, argv: Pointer?)
        fun gtk_main()
        fun gtk_main_quit()
        fun gtk_menu_new(): Pointer
        fun gtk_menu_item_new_with_label(label: String): Pointer
        fun gtk_separator_menu_item_new(): Pointer
        fun gtk_menu_shell_append(menu: Pointer?, item: Pointer?)
        fun gtk_widget_show_all(widget: Pointer?)
        fun gtk_widget_set_sensitive(widget: Pointer?, sensitive: Int)
        fun g_idle_add(function_: GSourceFunc, data: Pointer?): Int
        fun g_signal_connect_data(
            instance: Pointer?, signal: String, callback: Callback?,
            data: Pointer?, destroyData: Pointer?, flags: Int
        ): Long
    }

    interface AppIndicator : Library {
        companion object {
            val INSTANCE: AppIndicator = try {
                Native.load("ayatana-appindicator3", AppIndicator::class.java)
            } catch (e: Exception) {
                Native.load("appindicator3", AppIndicator::class.java)
            }
        }

        fun app_indicator_new(id: String, iconName: String, category: Int): Pointer
        fun app_indicator_set_status(indicator: Pointer?, status: Int)
        fun app_indicator_set_menu(indicator: Pointer?, menu: Pointer?)
        fun app_indicator_set_icon(indicator: Pointer?, iconName: String)
    }
}
