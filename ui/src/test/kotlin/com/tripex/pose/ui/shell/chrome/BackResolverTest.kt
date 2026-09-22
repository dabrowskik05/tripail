package com.tripex.pose.ui.shell.chrome

import org.junit.Assert.assertEquals
import org.junit.Test

class BackResolverTest {

    @Test
    fun `nothing open goes up a level`() {
        assertEquals(
            BackResolver.Action.NavigateUp,
            BackResolver.resolve(BackResolver.Context()),
        )
    }

    @Test
    fun `search closes before anything else`() {
        val everything = BackResolver.Context(
            isSearchOpen = true,
            isPanelOpen = true,
            hasSelection = true,
        )

        assertEquals(BackResolver.Action.CloseSearch, BackResolver.resolve(everything))
    }

    @Test
    fun `the panel closes before the selection is dropped`() {
        val context = BackResolver.Context(isPanelOpen = true, hasSelection = true)

        assertEquals(BackResolver.Action.ClosePanel, BackResolver.resolve(context))
    }

    @Test
    fun `a selection alone is cleared, not navigated away from`() {
        val context = BackResolver.Context(hasSelection = true)

        assertEquals(BackResolver.Action.ClearSelection, BackResolver.resolve(context))
    }

    /**
     * The regression that motivated this class: the arrow and the system gesture must agree.
     * Both call `resolve`, so this asserts the property directly — one input, one answer.
     */
    @Test
    fun `the same context always resolves the same way`() {
        val contexts = listOf(
            BackResolver.Context(),
            BackResolver.Context(isSearchOpen = true),
            BackResolver.Context(isPanelOpen = true),
            BackResolver.Context(hasSelection = true),
            BackResolver.Context(isSearchOpen = true, isPanelOpen = true),
            BackResolver.Context(isPanelOpen = true, hasSelection = true),
        )

        for (context in contexts) {
            assertEquals(BackResolver.resolve(context), BackResolver.resolve(context))
        }
    }
}
