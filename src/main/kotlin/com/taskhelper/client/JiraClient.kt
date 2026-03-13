package com.taskhelper.client

import com.taskhelper.config.Config
import com.taskhelper.model.ItemSource
import com.taskhelper.model.TaskItem
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

class JiraClient(private val config: Config) {

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val authHeader: String by lazy {
        val credentials = "${config.jiraEmail}:${config.jiraApiToken}"
        "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    fun fetchTasks(): List<TaskItem> {
        if (config.jiraBaseUrl.isBlank() || config.jiraApiToken.isBlank()) {
            return emptyList()
        }

        val url = "${config.jiraBaseUrl}/rest/api/3/search/jql"

        val requestBody = buildJsonObject {
            put("jql", config.jiraJql)
            putJsonArray("fields") {
                add("summary")
                add("status")
                add("updated")
            }
            put("maxResults", 50)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    System.err.println("Jira API error: ${response.code} ${response.message}")
                    System.err.println("Jira API response: $errorBody")
                    return emptyList()
                }

                val body = response.body?.string() ?: return emptyList()
                parseIssues(body)
            }
        } catch (e: Exception) {
            System.err.println("Jira request failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseIssues(responseBody: String): List<TaskItem> {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val issues = root["issues"]?.jsonArray ?: return emptyList()

        return issues.mapNotNull { element ->
            try {
                val issue = element.jsonObject
                val key = issue["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val fields = issue["fields"]?.jsonObject ?: return@mapNotNull null
                val summary = fields["summary"]?.jsonPrimitive?.content ?: ""
                val status = fields["status"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                val updated = fields["updated"]?.jsonPrimitive?.content ?: ""

                TaskItem(
                    id = key,
                    title = summary,
                    url = "${config.jiraBaseUrl}/browse/$key",
                    source = ItemSource.JIRA,
                    status = status,
                    updatedAt = updated
                )
            } catch (e: Exception) {
                System.err.println("Failed to parse Jira issue: ${e.message}")
                null
            }
        }
    }
}
