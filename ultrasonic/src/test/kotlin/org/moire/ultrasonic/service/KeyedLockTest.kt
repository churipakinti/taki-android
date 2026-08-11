/*
 * KeyedLockTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class KeyedLockTest {

    @Test
    fun `withLock returns the block's result`() {
        val lock = KeyedLock()

        val result = lock.withLock("a") { 42 }

        result shouldBeEqualTo 42
    }

    @Test
    fun `two concurrent callers for the same key are serialized`() {
        val lock = KeyedLock()
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = thread {
            lock.withLock("album-1") {
                firstEntered.countDown()
                releaseFirst.await()
                order.add(1)
            }
        }

        firstEntered.await(1, TimeUnit.SECONDS)

        val second = thread {
            lock.withLock("album-1") {
                order.add(2)
            }
        }

        // The second caller must still be blocked on the first one's lock at this point --
        // simulates a single-flight duplicate request arriving while the first is in flight.
        Thread.sleep(200)
        order.toList() shouldBeEqualTo emptyList()

        releaseFirst.countDown()
        first.join(1000)
        second.join(1000)

        order.toList() shouldBeEqualTo listOf(1, 2)
    }

    @Test
    fun `callers for different keys never block each other`() {
        val lock = KeyedLock()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)

        thread {
            lock.withLock("album-1") {
                firstEntered.countDown()
                releaseFirst.await()
            }
        }

        firstEntered.await(1, TimeUnit.SECONDS)

        thread {
            lock.withLock("album-2") {
                secondFinished.countDown()
            }
        }

        val completedWithoutWaiting = secondFinished.await(1, TimeUnit.SECONDS)
        completedWithoutWaiting shouldBeEqualTo true

        releaseFirst.countDown()
    }
}
