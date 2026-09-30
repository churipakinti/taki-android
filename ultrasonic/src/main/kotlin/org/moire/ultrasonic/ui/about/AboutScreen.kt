/*
 * AboutScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.text.HtmlCompat
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Kept off - mirrors the legacy `help_webpage` button, which was hard-coded
 * `android:visibility="gone"` and never toggled by any code path in production. [AboutActions
 * .onWebsite] stays wired (hide-don't-delete); this flag is the Compose equivalent of that
 * "present but off" state, not a new feature flag.
 */
private const val SHOW_WEBSITE_ACTION = false

/**
 * About (post-issue-#10 residual migration, phase 5A1): a small, quiet screen - app identity,
 * version, tagline and the free-text blurb (the same content the legacy `help.xml` showed), now
 * on the Taki canvas, then one action row, "Report a problem". No credits, licenses, social
 * links or update checker are added - none of those existed in the legacy screen.
 */
@Composable
fun AboutScreen(
    versionName: String,
    actions: AboutActions,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            TakiScreenHeader(onBack = actions.onBack, title = stringResource(R.string.menu_about))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TakiTheme.spacing.xl),
            ) {
                Spacer(Modifier.height(TakiTheme.spacing.lg))
                Text(
                    text = stringResource(R.string.about_appname),
                    style = TakiTheme.type.hero,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(TakiTheme.spacing.xxs))
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = TakiTheme.type.caption,
                )
                Spacer(Modifier.height(TakiTheme.spacing.lg))
                Text(text = stringResource(R.string.about_tagline), style = TakiTheme.type.title)
                Spacer(Modifier.height(TakiTheme.spacing.md))
                Text(text = aboutBodyText(), style = TakiTheme.type.body)
                Spacer(Modifier.height(TakiTheme.spacing.xl))

                if (SHOW_WEBSITE_ACTION) {
                    AboutActionRow(stringResource(R.string.about_webpage), actions.onWebsite)
                    Spacer(Modifier.height(TakiTheme.spacing.sm))
                }
                AboutActionRow(stringResource(R.string.about_report), actions.onReportBug)
                Spacer(Modifier.height(TakiTheme.spacing.xl))
            }
        }
    }
}

/** `about.text` carries one inline `<b>Taki</b>` tag; stripped exactly like every other
 *  HTML-bearing domain string in the app (album/artist notes, playlist comments) rather than
 *  rendered as styled text - see [androidx.core.text.HtmlCompat] usages in
 *  `ArtistDetailViewModel`/`AlbumDetailViewModel`/`TrackCollectionFragment`. */
@Composable
private fun aboutBodyText(): String {
    val raw = stringResource(R.string.about_text)
    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}

@Composable
private fun AboutActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.rowSm)
            .clip(TakiTheme.shapes.xs)
            .background(TakiTheme.colors.surfaceHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = TakiTheme.type.title)
    }
}
