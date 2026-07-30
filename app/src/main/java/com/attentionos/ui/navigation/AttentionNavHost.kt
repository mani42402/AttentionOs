package com.attentionos.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.attentionos.ui.theme.LocalMotionEnabled

/**
 * Navigation graph for the four top-level tabs.
 *
 * Replaces the previous enum-plus-AnimatedContent switch, which had no back stack: system back
 * exited the app from any tab, and switching tabs destroyed the outgoing screen along with its
 * scroll position. Navigation Compose restores both.
 */
@Composable
internal fun AttentionNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    home: @Composable () -> Unit,
    review: @Composable () -> Unit,
    insights: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    val motionEnabled = LocalMotionEnabled.current

    NavHost(
        navController = navController,
        startDestination = AppDestination.TODAY.route,
        modifier = modifier,
        enterTransition = {
            if (motionEnabled) {
                fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 16 }
            } else {
                fadeIn(tween(0))
            }
        },
        exitTransition = {
            if (motionEnabled) {
                fadeOut(tween(150)) + slideOutVertically(tween(180)) { -it / 18 }
            } else {
                fadeOut(tween(0))
            }
        },
    ) {
        tab(AppDestination.TODAY, home)
        tab(AppDestination.ACTIVITY, review)
        tab(AppDestination.INSIGHTS, insights)
        tab(AppDestination.SETTINGS, settings)
    }
}

private fun NavGraphBuilder.tab(
    destination: AppDestination,
    content: @Composable () -> Unit,
) {
    composable(destination.route) { content() }
}

/**
 * Switches tabs the way a bottom bar should: at most one entry per tab on the stack, each tab's
 * scrolled state preserved, and back always returning to the start destination rather than
 * walking a long history of taps.
 */
internal fun NavHostController.navigateToTab(destination: AppDestination) {
    if (currentDestination?.route == destination.route) return
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
