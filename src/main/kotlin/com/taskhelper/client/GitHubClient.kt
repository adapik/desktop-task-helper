package com.taskhelper.client

import com.taskhelper.model.ItemSource
import com.taskhelper.model.TaskItem
import kotlinx.serialization.json.*

class GitHubClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchReviewRequests(): List<TaskItem> {
        return try {
            val output = runGhCommand(
                "gh", "search", "prs",
                "--review-requested=@me",
                "--state=open",
                "--json", "number,title,url,repository,state,updatedAt",
                "--limit", "50"
            )

            if (output.isBlank()) return emptyList()
            parsePullRequests(output)
        } catch (e: Exception) {
            System.err.println("GitHub CLI request failed: ${e.message}")
            emptyList()
        }
    }

    private fun runGhCommand(vararg command: String): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            System.err.println("gh CLI error (exit $exitCode): $stderr")
            return ""
        }

        return stdout.trim()
    }

    private fun parsePullRequests(jsonOutput: String): List<TaskItem> {
        val items = json.parseToJsonElement(jsonOutput).jsonArray

        return items.mapNotNull { element ->
            try {
                val pr = element.jsonObject
                val number = pr["number"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val title = pr["title"]?.jsonPrimitive?.content ?: ""
                val url = pr["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val state = pr["state"]?.jsonPrimitive?.content ?: ""
                val updatedAt = pr["updatedAt"]?.jsonPrimitive?.content ?: ""

                val repo = pr["repository"]?.jsonObject
                val repoName = repo?.get("nameWithOwner")?.jsonPrimitive?.content
                    ?: repo?.get("name")?.jsonPrimitive?.content
                    ?: ""

                TaskItem(
                    id = "$repoName#$number",
                    title = title,
                    url = url,
                    source = ItemSource.GITHUB,
                    status = state,
                    updatedAt = updatedAt
                )
            } catch (e: Exception) {
                System.err.println("Failed to parse GitHub PR: ${e.message}")
                null
            }
        }
    }
}
