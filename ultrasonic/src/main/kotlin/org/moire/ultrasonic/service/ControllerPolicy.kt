/*
 * ControllerPolicy.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * AA-05: who may change the user's persistent state (favorites, ratings) through the exported
 * `PlaybackService` session.
 *
 * `PlaybackService` has to stay exported so Android Auto, the system UI, Assistant and legacy
 * `MediaBrowser` clients can reach it, and Media3 lets *any* app that binds to it connect with the
 * default command set - including `COMMAND_SET_RATING`. Browsing and ordinary transport are
 * deliberately left open to every controller (that is the platform contract); what an untrusted
 * controller must never get is the right to star/unstar/rate, which writes to the user's server.
 *
 * "Trusted" is Media3's own, platform-verified notion ([MediaSession.ControllerInfo.isTrusted]:
 * the caller holds MEDIA_CONTENT_CONTROL or an enabled notification listener, or is the same UID or
 * the system) plus this app's own media notification controller. There is deliberately no package
 * allowlist: the check follows what Android attests, not what a caller claims.
 */
internal object ControllerPolicy {

    /** Custom actions that change persistent user/server state; everything else is transport. */
    private val mutatingCustomActions = setOf(
        PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON,
        PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF
    )

    @OptIn(UnstableApi::class)
    fun mayMutateUserState(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        controller.isTrusted ||
            controller.uid == Process.myUid() ||
            session.isMediaNotificationController(controller)

    /**
     * May this controller choose what Taki's player opens? Only Taki's own process: its items carry the
     * internal "id|bitrate|path" source (which resolves to a local file or the Subsonic stream), built by
     * the app itself. Android Auto and every other controller send media ids, which Taki resolves
     * against the active server; a source URI they supply is ignored (#46).
     */
    fun maySupplyPlaybackSource(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == Process.myUid()

    fun isMutatingCustomAction(action: String): Boolean = action in mutatingCustomActions

    /** Session commands for [controller]: untrusted controllers get no rating command. */
    fun restrict(commands: SessionCommands, mayMutate: Boolean): SessionCommands =
        if (mayMutate) {
            commands
        } else {
            commands.buildUpon().remove(SessionCommand.COMMAND_CODE_SESSION_SET_RATING).build()
        }

    @OptIn(UnstableApi::class)
    fun denied(): ListenableFuture<SessionResult> =
        Futures.immediateFuture(
            SessionResult(
                SessionError(
                    SessionError.ERROR_PERMISSION_DENIED,
                    "This controller may not change favorites or ratings"
                )
            )
        )
}
