package com.taskhelper.service

import com.taskhelper.client.GitHubClient
import com.taskhelper.client.JiraClient
import com.taskhelper.config.Config
import com.taskhelper.model.TaskItem
import com.taskhelper.notification.NotificationManager
import com.taskhelper.tray.TrayManager
import kotlinx.coroutines.*

class PollingService(
    private val config: Config,
    private val trayManager: TrayManager,
    private val notificationManager: NotificationManager
) {

    private val jiraClient = JiraClient(config)
    private val githubClient = GitHubClient()
    private var previousItems = emptyList<TaskItem>()
    private var pollingJob: Job? = null

    fun start(scope: CoroutineScope) {
        pollingJob = scope.launch {
            while (isActive) {
                poll()
                delay(config.pollIntervalSeconds * 1000)
            }
        }
    }

    suspend fun poll() {
        try {
            val items = coroutineScope {
                val jiraDeferred = async(Dispatchers.IO) { jiraClient.fetchTasks() }
                val githubDeferred = async(Dispatchers.IO) { githubClient.fetchReviewRequests() }

                val jiraTasks = jiraDeferred.await()
                val githubPRs = githubDeferred.await()
                jiraTasks + githubPRs
            }

            // Update tray on EDT
            java.awt.EventQueue.invokeLater {
                trayManager.update(items)
            }

            // Send notifications for new items
            if (config.notificationsEnabled) {
                notificationManager.notifyNewItems(items, previousItems)
            }

            previousItems = items

            println("Polled: ${items.size} items (Jira: ${items.count { it.source == com.taskhelper.model.ItemSource.JIRA }}, GitHub: ${items.count { it.source == com.taskhelper.model.ItemSource.GITHUB }})")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("Polling error: ${e.message}")
        }
    }

    fun stop() {
        pollingJob?.cancel()
    }
}
