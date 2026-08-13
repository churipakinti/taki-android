package org.moire.ultrasonic.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

/**
 * Logs the stack trace of uncaught exceptions to a file on the SD card.
 *
 * Installed once as the process-wide [Thread.setDefaultUncaughtExceptionHandler] (see
 * NavigationActivity.setUncaughtExceptionHandler(), which only installs a new instance if the
 * current default isn't already one of these) and never replaced or cleared afterwards -- so
 * whatever [Context] is passed in here is held for the lifetime of the process. Must be
 * [Context.getApplicationContext], not an Activity, or the very first Activity instance would
 * leak permanently (confirmed via StrictMode's InstanceCountViolation on repeated rotation:
 * TAKI_BETA_COMPLETION_PLAN.md P0.3 audit, 2026-08-12).
 */
class UncaughtExceptionHandler(context: Context) : Thread.UncaughtExceptionHandler {
    private val context: Context = context.applicationContext
    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        var file: File? = null
        var printWriter: PrintWriter? = null

        try {
            file = File(FileUtil.ultrasonicDirectory, STACKTRACE_NAME)
            printWriter = PrintWriter(file)
            val logMessage = String.format(
                "Android API level: %s\nUltrasonic version name: %s\n" +
                    "Ultrasonic version code: %s\n\n",
                Build.VERSION.SDK_INT,
                Util.getVersionName(context),
                Util.getVersionCode(context)
            )
            printWriter.println(logMessage)
            throwable.printStackTrace(printWriter)
            Timber.e(throwable, "Uncaught Exception! %s", logMessage)
            Timber.i("Stack trace written to %s", file)
        } catch (all: Throwable) {
            Timber.e(all, "Failed to write stack trace to %s", file)
        } finally {
            printWriter.safeClose()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val STACKTRACE_NAME = "ultrasonic-stacktrace.txt"
    }
}
