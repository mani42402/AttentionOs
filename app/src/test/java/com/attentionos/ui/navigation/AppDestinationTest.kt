package com.attentionos.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar and the navigation graph both derive from [AppDestination], so a mismatch
 * between a tab and its route would silently break tab selection rather than fail to compile.
 */
class AppDestinationTest {

    @Test
    fun `every destination has a unique non-blank route`() {
        val routes = AppDestination.entries.map { it.route }
        assertTrue("routes must not be blank", routes.none { it.isBlank() })
        assertEquals("routes must be unique", routes.size, routes.toSet().size)
    }

    @Test
    fun `every route round-trips back to its destination`() {
        for (destination in AppDestination.entries) {
            assertEquals(destination, AppDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun `unknown and missing routes fall back to the start destination`() {
        // A null route occurs on the first frame, before the graph resolves.
        assertEquals(AppDestination.TODAY, AppDestination.fromRoute(null))
        assertEquals(AppDestination.TODAY, AppDestination.fromRoute("nope"))
    }

    @Test
    fun `every destination has a label for the bottom bar`() {
        assertTrue(AppDestination.entries.none { it.label.isBlank() })
    }
}
