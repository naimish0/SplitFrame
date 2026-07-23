package com.rameshta.splitframe.presentation.merge

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateFavoriteOptimismTest {
    @Test
    fun pendingFavoriteChangesApplyImmediatelyWithoutDuplicates() {
        val optimistic = favoriteIdsWithOptimisticChanges(
            persistedIds = listOf("first", "second", "first"),
            pendingTargets = linkedMapOf(
                "first" to false,
                "third" to true,
            ),
        )

        assertEquals(listOf("third", "second"), optimistic)
    }

    @Test
    fun removingFailedOverrideRestoresPersistedFavorites() {
        val persisted = listOf("first", "second")
        val optimistic = favoriteIdsWithOptimisticChanges(
            persistedIds = persisted,
            pendingTargets = mapOf("first" to false),
        )
        val rolledBack = favoriteIdsWithOptimisticChanges(
            persistedIds = persisted,
            pendingTargets = emptyMap(),
        )

        assertEquals(listOf("second"), optimistic)
        assertEquals(persisted, rolledBack)
    }
}
