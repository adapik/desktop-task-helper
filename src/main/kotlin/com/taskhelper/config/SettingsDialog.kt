package com.taskhelper.config

import java.awt.*
import javax.swing.*

class SettingsDialog(
    private val currentConfig: Config,
    private val onSave: (Config) -> Unit
) {

    fun show() {
        SwingUtilities.invokeLater { createAndShow() }
    }

    private fun createAndShow() {
        val dialog = JDialog(null as Frame?, "Task Helper \u2014 Settings", true)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.isResizable = false

        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        var row = 0

        // Section: Jira
        addSectionLabel(panel, gbc, "Jira", row++)

        val baseUrlField = JTextField(currentConfig.jiraBaseUrl, 35)
        addField(panel, gbc, "Base URL:", baseUrlField, row++)

        val emailField = JTextField(currentConfig.jiraEmail, 35)
        addField(panel, gbc, "Email:", emailField, row++)

        val tokenField = JPasswordField(currentConfig.jiraApiToken, 35)
        addField(panel, gbc, "API Token:", tokenField, row++)

        val jqlField = JTextField(currentConfig.jiraJql, 35)
        addField(panel, gbc, "JQL:", jqlField, row++)

        // Section: General
        addSectionLabel(panel, gbc, "General", row++)

        val pollField = JSpinner(SpinnerNumberModel(currentConfig.pollIntervalSeconds, 30L, 3600L, 10L))
        addField(panel, gbc, "Poll interval (sec):", pollField, row++)

        val notifCheckbox = JCheckBox("Enable desktop notifications", currentConfig.notificationsEnabled)
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2
        panel.add(notifCheckbox, gbc)
        gbc.gridwidth = 1

        // Note about GitHub
        val ghNote = JLabel("<html><i>GitHub: uses 'gh' CLI (configure via 'gh auth login')</i></html>")
        ghNote.foreground = Color.GRAY
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2
        panel.add(ghNote, gbc)
        gbc.gridwidth = 1

        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        buttonPanel.border = BorderFactory.createEmptyBorder(12, 0, 0, 0)

        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener { dialog.dispose() }

        val saveButton = JButton("Save")
        saveButton.addActionListener {
            val newConfig = Config(
                jiraBaseUrl = baseUrlField.text.trim().trimEnd('/'),
                jiraEmail = emailField.text.trim(),
                jiraApiToken = String(tokenField.password).trim(),
                jiraJql = jqlField.text.trim(),
                pollIntervalSeconds = (pollField.value as Long),
                notificationsEnabled = notifCheckbox.isSelected
            )
            Config.save(newConfig)
            onSave(newConfig)
            dialog.dispose()
        }

        buttonPanel.add(cancelButton)
        buttonPanel.add(saveButton)

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.EAST
        panel.add(buttonPanel, gbc)

        dialog.contentPane = panel
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }

    private fun addSectionLabel(panel: JPanel, gbc: GridBagConstraints, text: String, row: Int) {
        val label = JLabel(text)
        label.font = label.font.deriveFont(Font.BOLD, label.font.size + 2f)
        label.border = BorderFactory.createEmptyBorder(if (row > 0) 12 else 0, 0, 4, 0)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        panel.add(label, gbc)
        gbc.gridwidth = 1
    }

    private fun addField(panel: JPanel, gbc: GridBagConstraints, labelText: String, field: JComponent, row: Int) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel(labelText), gbc)

        gbc.gridx = 1; gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(field, gbc)
    }
}
