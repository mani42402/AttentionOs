package com.attentionos.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.attentionos.data.db.NotificationListItem
import com.attentionos.data.settings.AppSettings
import com.attentionos.domain.AttentionPriority
import com.attentionos.domain.NotificationCategory
import com.attentionos.ui.home.DashboardScreen
import com.attentionos.ui.home.NotificationRow
import com.attentionos.ui.theme.AttentionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Asserts the semantics a screen reader depends on, instead of trusting a manual TalkBack pass.
 *
 * A drawn chart, a `Box` used as a progress bar, and a row built from five `Text`s all look
 * finished on screen while being invisible or unintelligible to TalkBack. Those failures are
 * silent, so they are checked here where a regression breaks the build rather than being noticed
 * by a user who cannot see the screen.
 */
class AccessibilitySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = listOf(
        NotificationListItem(
            id = 1L,
            notificationKey = "key-1",
            appLabel = "Bank",
            title = "Payment failed",
            message = "Your card was declined",
            postedAt = 1_700_000_000_000L,
            priority = AttentionPriority.HIGH.name,
            category = NotificationCategory.FINANCE.name,
            explanation = "Finance signal",
            queued = false,
            action = null,
            hasEmbedding = true,
            personalProbability = null,
            personalModelApplied = false,
        ),
    )

    private fun renderHome() {
        compose.setContent {
            AttentionTheme {
                DashboardScreen(
                    state = MainUiState(
                        isLoading = false,
                        settings = AppSettings(focusMode = true, onboardingComplete = true),
                        events = events,
                        receivedToday = 4,
                        importantToday = 2,
                        queuedToday = 0,
                    ),
                    hasAccess = true,
                    onFocusChanged = {},
                    onOpenNotificationAccess = {},
                    onSeeActivity = {},
                )
            }
        }
    }

    @Test
    fun flowLanesDescribeTheirValueRatherThanBeingSilentGraphics() {
        renderHome()
        // The lanes are Canvas drawings. Without an explicit description a screen reader reaches
        // the card and finds only the headline number.
        val described = compose
            .onAllNodesWithContentDescription("classified as", substring = true)
            .fetchSemanticsNodes()
        assertTrue(
            "expected a spoken description per priority lane, found ${described.size}",
            described.size >= 3,
        )
    }

    @Test
    fun aZeroLaneAnnouncesZeroRatherThanBeingSilent() {
        renderHome()
        // queuedToday is 0 of 4 received. The lane draws as an empty track, and its description
        // has to carry that number — a silent lane would leave the reader unable to tell an
        // empty measurement from a missing one.
        val zero = compose
            .onAllNodesWithContentDescription("0 of 4", substring = true)
            .fetchSemanticsNodes()
        assertTrue("the quiet lane should announce 0 of 4", zero.isNotEmpty())
    }

    @Test
    fun sectionTitlesAreHeadings() {
        renderHome()
        val headings = compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes()
        assertTrue(
            "section titles must be headings so TalkBack can jump between sections",
            headings.isNotEmpty(),
        )
    }

    @Test
    fun theProtectionToggleExposesItsState() {
        renderHome()
        val tree = compose.onRoot().printToString(maxDepth = Int.MAX_VALUE)
        assertTrue(
            "a toggle with no role or state is unusable with a screen reader",
            tree.contains("Switch") || tree.contains("ToggleableState"),
        )
    }

    @Test
    fun aDecisionRowIsAnnouncedAsOneItem() {
        // Rendered on its own: inside the screen it sits in a LazyColumn below the fold, and
        // what is being checked is the row's own semantics rather than where it lands.
        compose.setContent {
            AttentionTheme { NotificationRow(event = events.first()) }
        }

        // Merging is the point. Unmerged, the title, time, message and priority chip are four
        // separate stops for a screen reader; merged, they are one announcement.
        val unmerged = compose.onRoot(useUnmergedTree = true)
            .printToString(maxDepth = Int.MAX_VALUE)
        assertTrue("the row should declare merged descendants", unmerged.contains("MergeDescendants"))
        assertTrue("the notification title should be present", unmerged.contains("Payment failed"))

        // The chip's own text is abbreviated ("Important"), so it carries a full sentence for
        // screen readers instead.
        assertTrue(
            "the priority chip should spell out what it means",
            unmerged.contains("Reaches you promptly"),
        )

        val merged = compose.onRoot(useUnmergedTree = false)
            .printToString(maxDepth = Int.MAX_VALUE)
        assertTrue(
            "the merged tree should collapse the row into fewer nodes",
            merged.count { it == '|' } < unmerged.count { it == '|' },
        )
    }
}
