package de.olus.touchfix

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app ring-buffer event log visible in the console UI.
 * Thread-safe singleton; listeners are notified on every new entry.
 */
object EventLog {

    enum class Level { INFO, OK, WARN, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val message: String
    ) {
        private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMAN)
        val prefix: String get() = when (level) {
            Level.INFO  -> "⚪"
            Level.OK    -> "🟢"
            Level.WARN  -> "🟡"
            Level.ERROR -> "🔴"
        }
        val formatted: String get() = "${fmt.format(Date(timestamp))} $prefix $message"
    }

    private const val MAX_ENTRIES = 200
    private val entries = mutableListOf<Entry>()
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun log(level: Level, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, message)
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        android.util.Log.i("TouchFix", "${entry.prefix} $message")
        notifyListeners()
    }

    fun i(msg: String) = log(Level.INFO, msg)
    fun ok(msg: String) = log(Level.OK, msg)
    fun w(msg: String) = log(Level.WARN, msg)
    fun e(msg: String) = log(Level.ERROR, msg)

    @Synchronized
    fun getSnapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        notifyListeners()
    }

    @Synchronized
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }
}
