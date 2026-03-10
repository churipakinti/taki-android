/*
 * CoroutinePatterns.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.moire.ultrasonic.util.CommunicationError.getErrorMessage
import org.moire.ultrasonic.util.Util.toast
import timber.log.Timber

object CoroutinePatterns {
    val loggingExceptionHandler by lazy {
        CoroutineExceptionHandler { _, exception ->
            Handler(Looper.getMainLooper()).post {
                Timber.w(exception)
            }
        }
    }
}

fun Fragment.toastingExceptionHandler(prefix: String = ""): CoroutineExceptionHandler =
    CoroutineExceptionHandler {
            _,
            exception
        ->
        // Stop the spinner if applicable
        if (this is RefreshableFragment) {
            this.swipeRefresh?.isRefreshing = false
        }
        toast("$prefix ${getErrorMessage(exception)}", shortDuration = false)
    }

/*
* Launch a coroutine with a toast
* This extension can be only  started from a fragment
* because it needs the fragments scope to create the toast
 */
fun Fragment.launchWithToast(block: suspend CoroutineScope.() -> String?) {
    // Get the scope
    val scope = activity?.lifecycleScope ?: lifecycleScope

    // Launch the Job
    val deferred = scope.async(block = block)

    // Setup a handler when the job is done
    deferred.invokeOnCompletion {
        val toastString = if (it != null && it !is CancellationException) {
            getErrorMessage(it)
        } else {
            null
        }
        scope.launch(Dispatchers.Main) {
            val successString = toastString ?: deferred.await()
            if (successString != null) {
                this@launchWithToast.toast(successString)
            }
        }
    }
}

// Unused, kept commented for eventual later use
// fun CoroutineScope.executeTaskWithModalDialog(
//    fragment: Fragment,
//    task: suspend CoroutineScope.() -> String?
// ) {
//    // Create the job
//    val job = launchWithToast(task)
//
//    // Create the dialog
//    val builder = InfoDialog.Builder(fragment.requireContext())
//    builder.setTitle(R.string.background_task_wait)
//    builder.setMessage(R.string.background_task_loading)
//    builder.setOnCancelListener { job.cancel() }
//    builder.setPositiveButton(R.string.common_cancel) { _, _ -> job.cancel() }
//    val dialog = builder.create()
//    dialog.show()
//
//    // Add additional handler to close the dialog
//    job.invokeOnCompletion {
//        launch(Dispatchers.Main) {
//            dialog.dismiss()
//        }
//    }
// }
