package com.bclaw.app.domain

class ThreadSubscriptionRegistry {
    private val threadIds = linkedSetOf<String>()

    fun subscribe(threadId: String) {
        threadIds += threadId
    }

    fun isSubscribed(threadId: String): Boolean = threadIds.contains(threadId)

    fun allKnown(additionalThreadIds: Collection<String>): Set<String> {
        return buildSet {
            addAll(threadIds)
            addAll(additionalThreadIds)
        }
    }
}
