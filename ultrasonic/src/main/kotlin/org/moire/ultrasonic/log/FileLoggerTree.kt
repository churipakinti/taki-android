package org.moire.ultrasonic.log

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util.safeClose
import timber.log.Timber

/**
 * A Timber Tree which can be used to log to a file
 * Subclass of the DebugTree so it inherits the Tag handling
 */
@Suppress("MagicNumber")
class FileLoggerTree :
    Timber.DebugTree(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @OptIn(ObsoleteCoroutinesApi::class)
    private val logMessageActor = actor<LogMessage> {
        for (msg in channel) {
            writeLogToFile(msg.file, msg.priority, msg.tag, msg.message, msg.t)
        }
    }

    data class LogMessage(
        val file: File?,
        val priority: Int,
        val tag: String?,
        val message: String,
        val t: Throwable?
    )

    /**
     * Writes a log entry to file
     * This methods sends the log entry to the coroutine actor, which then processes the entries
     * in FIFO order on an IO-Thread
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        launch {
            getNextLogFile()
            logMessageActor.send(
                LogMessage(file, priority, tag, message, t)
            )
        }
    }

    private suspend fun writeLogToFile(
        file: File?,
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        val time: String = dateFormat.format(Date())
        val exceptionString = t?.toString() ?: ""
        withContext(Dispatchers.IO) {
            var pw: PrintWriter? = null
            try {
                pw = PrintWriter(FileWriter(file, true))
                pw.println("$time: ${logLevelToString(priority)} $tag $message $exceptionString\n")
                t?.printStackTrace(pw)
            } catch (all: Throwable) {
                // Using base class DebugTree here, we don't want to try to log this into file
                super.log(6, TAG, String.format("Failed to write log to %s", file), all)
            } finally {
                pw?.safeClose()
            }
        }
    }

    /**
     * Sets the file to log into
     * This function also rotates the log files periodically, when they reach the predefined size
     */
    private fun getNextLogFile() {
        if (file == null) {
            synchronized(this) {
                if (file != null) return
                getNumberedFile(false)
                // Using base class DebugTree here, we don't want to try to log this into file
                super.log(4, TAG, String.format("Logging into file %s", file?.name), null)
                return
            }
        }
        if (callNum % 100 == 0) {
            // Gain some performance by only executing this rarely
            if (file!!.length() > MAX_LOGFILE_LENGTH) {
                synchronized(this) {
                    if (file!!.length() <= MAX_LOGFILE_LENGTH) return
                    getNumberedFile(true)
                    // Using base class DebugTree here, we don't want to try to log this into file
                    super.log(
                        4,
                        TAG,
                        String.format("Log file rotated, logging into file %s", file?.name),
                        null
                    )
                }
            }
        }
    }

    /**
     * Checks the number of log files
     * @param next: if false, sets the current log file with the greatest number
     * if true, sets a new file for logging with the next number
     */
    private fun getNumberedFile(next: Boolean) {
        var fileNum = 1
        val fileList = getLogFileList()

        if (!fileList.isNullOrEmpty()) {
            fileList.sortByDescending { t -> t.name }
            val lastFile = fileList[0]
            val number = fileNumberRegex.find(lastFile.name)?.groups?.get(1)?.value
            if (number != null) {
                fileNum = number.toInt()
            }
        }

        if (next) fileNum++
        file = File(
            FileUtil.ultrasonicDirectory,
            FILENAME.replace("*", fileNum.toString())
        )
    }

    private fun logLevelToString(logLevel: Int): String = when (logLevel) {
        2 -> "V"
        3 -> "D"
        4 -> "I"
        5 -> "W"
        6 -> "E"
        7 -> "A"
        else -> "U"
    }

    companion object {
        val TAG = FileLoggerTree::class.simpleName

        @Volatile
        private var file: File? = null
        const val FILENAME = "ultrasonic.*.log"
        private val fileNameRegex = Regex(
            FILENAME.replace(".", "\\.").replace("*", "\\d*")
        )
        private val fileNumberRegex = Regex(
            FILENAME.replace(".", "\\.").replace("*", "(\\d*)")
        )
        const val MAX_LOGFILE_LENGTH = 10000000
        var callNum = 0

        fun plantToTimberForest() {
            if (!Timber.forest().any { t -> t is FileLoggerTree }) {
                Timber.plant(FileLoggerTree())
            }
        }

        fun uprootFromTimberForest() {
            val fileLoggerTree = Timber.forest().singleOrNull { t -> t is FileLoggerTree }
                ?: return
            Timber.uproot(fileLoggerTree)
            file = null
        }

        fun getLogFileNumber(): Int {
            val fileList = getLogFileList()
            if (!fileList.isNullOrEmpty()) return fileList.size
            return 0
        }

        fun getLogFileSizes(): Long {
            var sizeSum: Long = 0
            val fileList = getLogFileList()
            if (fileList.isNullOrEmpty()) return sizeSum
            for (file in fileList) {
                sizeSum += file.length()
            }
            return sizeSum
        }

        fun deleteLogFiles() {
            val fileList = getLogFileList()
            if (fileList.isNullOrEmpty()) return
            for (file in fileList) {
                file.delete()
            }
        }

        private fun getLogFileList(): Array<out File>? {
            val directory = FileUtil.ultrasonicDirectory
            return directory.listFiles { t -> t.name.matches(fileNameRegex) }
        }
    }
}
