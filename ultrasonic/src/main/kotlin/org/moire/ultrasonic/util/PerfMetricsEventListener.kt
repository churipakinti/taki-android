/*
 * PerfMetricsEventListener.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.SystemClock
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import timber.log.Timber

/**
 * Baseline connection-level instrumentation (Fase 0). Separates DNS lookup, TCP connect, and TLS
 * handshake from the request/response time that [PerfMetricsInterceptor] already measures --
 * needed to tell apart "the server is slow" from "establishing the connection is slow" (e.g. a
 * VPN/tunnel's first-packet cost). Only ever attached to the debug-build OkHttpClients, see
 * MusicServiceModule.kt. Never logs the resolved address itself, only elapsed durations.
 */
class PerfMetricsEventListener : EventListener() {
    private var callStart = 0L
    private var dnsStart = 0L
    private var connectStart = 0L
    private var secureConnectStart = 0L

    override fun callStart(call: Call) {
        callStart = SystemClock.elapsedRealtime()
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStart = SystemClock.elapsedRealtime()
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<java.net.InetAddress>
    ) {
        Timber.tag(TAG).d("CONN dns = %dms", SystemClock.elapsedRealtime() - dnsStart)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStart = SystemClock.elapsedRealtime()
    }

    override fun secureConnectStart(call: Call) {
        secureConnectStart = SystemClock.elapsedRealtime()
    }

    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) {
        Timber.tag(TAG).d("CONN tls = %dms", SystemClock.elapsedRealtime() - secureConnectStart)
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?
    ) {
        Timber.tag(TAG).d("CONN tcp_connect = %dms", SystemClock.elapsedRealtime() - connectStart)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?,
        ioe: java.io.IOException
    ) {
        Timber.tag(TAG).d(
            "CONN connect_failed after %dms: %s",
            SystemClock.elapsedRealtime() - connectStart,
            ioe.javaClass.simpleName
        )
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        Timber.tag(TAG).d(
            "CONN acquired (new_connection_setup = %dms since call start)",
            SystemClock.elapsedRealtime() - callStart
        )
    }

    private companion object {
        const val TAG = "PerfMetrics"
    }
}
