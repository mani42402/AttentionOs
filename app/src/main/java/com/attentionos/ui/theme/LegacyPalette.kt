package com.attentionos.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Temporary bridge from the old hand-picked palette to the new scheme.
 *
 * Every screen is being rebuilt on the design system; this exists only so the build stays green
 * and each screen can be verified on device as it is converted, instead of leaving the app
 * uncompilable through a multi-screen rewrite.
 *
 * Entries are deleted as their last caller is rewritten. When this file is empty, the migration
 * is done — so it doubles as the progress tracker for the redesign.
 */
@Deprecated("Legacy palette: use MaterialTheme.colorScheme or PriorityColors.")
internal val Forest950 = Color(0xFF0A0B2E)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme or PriorityColors.")
internal val Forest900 = Color(0xFF191A4A)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme.primary.")
internal val Forest800 = Color(0xFF4045A8)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme.secondary.")
internal val Mint500 = Color(0xFF006C59)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme.secondaryContainer.")
internal val Mint300 = Color(0xFF74F8D7)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme.tertiary.")
internal val Sun500 = Color(0xFF855400)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme.error or PriorityColors.critical.")
internal val Coral500 = Color(0xFFBA1A1A)

@Deprecated("Legacy palette: use MaterialTheme.colorScheme.primary.")
internal val Ice500 = Color(0xFFBEC0FF)

@Deprecated("Legacy palette: use PriorityColors.medium.")
internal val Violet400 = Color(0xFF5257C7)
