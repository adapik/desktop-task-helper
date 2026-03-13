package com.taskhelper.model

enum class ItemSource { JIRA, GITHUB }

data class TaskItem(
    val id: String,
    val title: String,
    val url: String,
    val source: ItemSource,
    val status: String,
    val updatedAt: String
) {
    val menuLabel: String
        get() {
            val prefix = when (source) {
                ItemSource.JIRA -> "[JIRA]"
                ItemSource.GITHUB -> "[PR]"
            }
            val truncatedTitle = if (title.length > 60) title.take(57) + "..." else title
            return "$prefix $id: $truncatedTitle"
        }
}
