package com.taskhelper.config

import java.io.File
import java.util.Properties

data class Config(
    val jiraBaseUrl: String,
    val jiraEmail: String,
    val jiraApiToken: String,
    val jiraJql: String,
    val pollIntervalSeconds: Long,
    val notificationsEnabled: Boolean
) {
    companion object {
        private val configDir = File(System.getProperty("user.home"), ".desktop-task-helper")
        private val configFile = File(configDir, "config.properties")

        fun load(): Config {
            if (!configFile.exists()) {
                createDefaultConfig()
                error(
                    "Config file created at ${configFile.absolutePath}. " +
                    "Please fill in your credentials and restart the application."
                )
            }

            val props = Properties()
            configFile.inputStream().use { props.load(it) }

            return Config(
                jiraBaseUrl = props.getProperty("jira.base_url", "").trimEnd('/'),
                jiraEmail = props.getProperty("jira.email", ""),
                jiraApiToken = props.getProperty("jira.api_token", ""),
                jiraJql = props.getProperty(
                    "jira.jql",
                    "assignee = currentUser() AND status in (\"In Review\", \"Code Review\", \"Waiting for review\")"
                ),
                pollIntervalSeconds = props.getProperty("poll.interval_seconds", "120").toLong(),
                notificationsEnabled = props.getProperty("notifications.enabled", "true").toBoolean()
            )
        }

        fun save(config: Config) {
            configDir.mkdirs()
            val props = Properties()
            props.setProperty("jira.base_url", config.jiraBaseUrl)
            props.setProperty("jira.email", config.jiraEmail)
            props.setProperty("jira.api_token", config.jiraApiToken)
            props.setProperty("jira.jql", config.jiraJql)
            props.setProperty("poll.interval_seconds", config.pollIntervalSeconds.toString())
            props.setProperty("notifications.enabled", config.notificationsEnabled.toString())
            configFile.outputStream().use { props.store(it, "Desktop Task Helper Configuration") }
        }

        private fun createDefaultConfig() {
            configDir.mkdirs()
            configFile.writeText(
                """
                |# Jira Configuration
                |jira.base_url=https://your-company.atlassian.net
                |jira.email=your-email@company.com
                |jira.api_token=your-jira-api-token
                |jira.jql=assignee = currentUser() AND status in ("In Review", "Code Review", "Waiting for review")
                |
                |# GitHub: uses 'gh' CLI (must be installed and authenticated via 'gh auth login')
                |
                |# Polling interval in seconds
                |poll.interval_seconds=120
                |
                |# Desktop notifications
                |notifications.enabled=true
                """.trimMargin()
            )
        }
    }
}
