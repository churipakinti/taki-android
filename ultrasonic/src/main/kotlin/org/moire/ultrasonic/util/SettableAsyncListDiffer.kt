/*
 * SettableAsyncListDiffer.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.DiffResult
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * This class is a variation of the AsyncListDiffer provided for the RecyclerView.
 * It is possible to set its List to a new value without triggering the diffing.
 * This is necessary when the changes in the List must be synchronous, and are
 * executed manually elsewhere in the code.
 * @see androidx.recyclerview.widget.AsyncListDiffer
 * More discussion about the necessity of this class is in the merge request:
 * https://gitlab.com/ultrasonic/ultrasonic/-/merge_requests/815
 * We opened an issue for Google and hope they will add an official solution, see:
 * https://issuetracker.google.com/issues/247351552
 */
class SettableAsyncListDiffer<T> {
    private var mUpdateCallback: ListUpdateCallback? = null
    var mConfig: AsyncDifferConfig<T>? = null
    private var mMainThreadExecutor: Executor? = null

    private class MainThreadExecutor : Executor {
        val mHandler = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable) {
            mHandler.post(command)
        }
    }

    // TODO: use MainThreadExecutor from supportlib once one exists
    private val sMainThreadExecutor: Executor = MainThreadExecutor()

    /**
     * Listener for when the current List is updated.
     *
     * @param <T> Type of items in List
     </T> */
    interface ListListener<T> {
        /**
         * Called after the current List has been updated.
         *
         * @param previousList The previous list.
         * @param currentList The new current list.
         */
        fun onCurrentListChanged(previousList: List<T>, currentList: List<T>)
    }

    private val mListeners: MutableList<ListListener<T>> = CopyOnWriteArrayList()

    /**
     * Convenience for
     * `AsyncListDiffer(new AdapterListUpdateCallback(adapter),
     * new AsyncDifferConfig.Builder().setDiffCallback(diffCallback).build());`
     *
     * @param adapter Adapter to dispatch position updates to.
     * @param diffCallback ItemCallback that compares items to dispatch appropriate animations when
     *
     * @see DiffUtil.DiffResult.dispatchUpdatesTo
     */
    constructor(
        adapter: RecyclerView.Adapter<*>,
        diffCallback: DiffUtil.ItemCallback<T>
    ) : this(
        AdapterListUpdateCallback(adapter),
        AsyncDifferConfig.Builder(diffCallback).build()
    )

    /**
     * Create a AsyncListDiffer with the provided config, and ListUpdateCallback to dispatch
     * updates to.
     *
     * @param listUpdateCallback Callback to dispatch updates to.
     * @param config Config to define background work Executor, and DiffUtil.ItemCallback for
     * computing List diffs.
     *
     * @see DiffUtil.DiffResult.dispatchUpdatesTo
     */
    @SuppressLint("RestrictedApi")
    constructor(
        listUpdateCallback: ListUpdateCallback,
        config: AsyncDifferConfig<T>
    ) {
        mUpdateCallback = listUpdateCallback
        mConfig = config
        mMainThreadExecutor = if (config.mainThreadExecutor != null) {
            config.mainThreadExecutor
        } else {
            sMainThreadExecutor
        }
    }

    private var mList: List<T>? = null

    /**
     * Non-null, unmodifiable version of mList.
     *
     *
     * Collections.emptyList when mList is null, wrapped by Collections.unmodifiableList otherwise
     */
    private var mReadOnlyList = emptyList<T>()

    // Max generation of currently scheduled runnable
    private var mMaxScheduledGeneration = 0

    /**
     * Get the current List - any diffing to present this list has already been computed and
     * dispatched via the ListUpdateCallback.
     *
     *
     * If a `null` List, or no List has been submitted, an empty list will be returned.
     *
     *
     * The returned list may not be mutated - mutations to content must be done through
     * [.submitList].
     *
     * @return current List.
     */

    val currentList: List<T>
        get() = mReadOnlyList

    /**
     * Sets the List to a new value without invoking the diffing
     */
    fun setList(newList: List<T>) {
        mList = newList
        mReadOnlyList = Collections.unmodifiableList(newList)
    }

    /**
     * Pass a new List to the AdapterHelper. Adapter updates will be computed on a background
     * thread.
     *
     *
     * If a List is already present, a diff will be computed asynchronously on a background thread.
     * When the diff is computed, it will be applied (dispatched to the [ListUpdateCallback]),
     * and the new List will be swapped in.
     *
     * @param newList The new List.
     */
    fun submitList(newList: List<T>?) {
        submitList(newList, null)
    }

    /**
     * Pass a new List to the AdapterHelper. Adapter updates will be computed on a background
     * thread.
     *
     *
     * If a List is already present, a diff will be computed asynchronously on a background thread.
     * When the diff is computed, it will be applied (dispatched to the [ListUpdateCallback]),
     * and the new List will be swapped in.
     *
     *
     * The commit callback can be used to know when the List is committed, but note that it
     * may not be executed. If List B is submitted immediately after List A, and is
     * committed directly, the callback associated with List A will not be run.
     *
     * @param newList The new List.
     * @param commitCallback Optional runnable that is executed when the List is committed, if
     * it is committed.
     */
    fun submitList(newList: List<T>?, commitCallback: Runnable?) {
        // incrementing generation means any currently-running diffs are discarded when they finish
        val runGeneration = ++mMaxScheduledGeneration
        if (newList === mList) {
            // nothing to do (Note - still had to inc generation, since may have ongoing work)
            commitCallback?.run()
            return
        }
        val previousList = mReadOnlyList

        // fast simple remove all
        if (newList == null) {
            val countRemoved = mList!!.size
            mList = null
            mReadOnlyList = emptyList()
            // notify last, after list is updated
            mUpdateCallback!!.onRemoved(0, countRemoved)
            onCurrentListChanged(previousList, commitCallback)
            return
        }

        // fast simple first insert
        if (mList == null) {
            mList = newList
            mReadOnlyList = Collections.unmodifiableList(newList)
            // notify last, after list is updated
            mUpdateCallback!!.onInserted(0, newList.size)
            onCurrentListChanged(previousList, commitCallback)
            return
        }
        val oldList: List<T> = mList!!
        mConfig!!.backgroundThreadExecutor.execute {
            val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldList.size

                override fun getNewListSize(): Int = newList.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem: T? = oldList[oldItemPosition]
                    val newItem: T? = newList[newItemPosition]
                    return if (oldItem != null && newItem != null) {
                        mConfig!!.diffCallback.areItemsTheSame(oldItem, newItem)
                    } else {
                        oldItem == null && newItem == null
                    }
                    // If both items are null we consider them the same.
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    val oldItem: T? = oldList[oldItemPosition]
                    val newItem: T? = newList[newItemPosition]
                    if (oldItem != null && newItem != null) {
                        return mConfig!!.diffCallback.areContentsTheSame(oldItem, newItem)
                    }
                    if (oldItem == null && newItem == null) {
                        return true
                    }
                    throw AssertionError()
                }

                override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                    val oldItem: T? = oldList[oldItemPosition]
                    val newItem: T? = newList[newItemPosition]
                    if (oldItem != null && newItem != null) {
                        return mConfig!!.diffCallback.getChangePayload(oldItem, newItem)
                    }
                    throw AssertionError()
                }
            })
            mMainThreadExecutor!!.execute {
                if (mMaxScheduledGeneration == runGeneration) {
                    latchList(newList, result, commitCallback)
                }
            }
        }
    }

    private fun latchList(newList: List<T>, diffResult: DiffResult, commitCallback: Runnable?) {
        val previousList = mReadOnlyList
        mList = newList
        // notify last, after list is updated
        mReadOnlyList = Collections.unmodifiableList(newList)
        diffResult.dispatchUpdatesTo(mUpdateCallback!!)
        onCurrentListChanged(previousList, commitCallback)
    }

    private fun onCurrentListChanged(previousList: List<T>, commitCallback: Runnable?) {
        // current list is always mReadOnlyList
        for (listener in mListeners) {
            listener.onCurrentListChanged(previousList, mReadOnlyList)
        }
        commitCallback?.run()
    }

    /**
     * Add a ListListener to receive updates when the current List changes.
     *
     * @param listener Listener to receive updates.
     *
     * @see .getCurrentList
     * @see .removeListListener
     */
    fun addListListener(listener: ListListener<T>) {
        mListeners.add(listener)
    }

    /**
     * Remove a previously registered ListListener.
     *
     * @param listener Previously registered listener.
     * @see .getCurrentList
     * @see .addListListener
     */
    fun removeListListener(listener: ListListener<T>) {
        mListeners.remove(listener)
    }
}
