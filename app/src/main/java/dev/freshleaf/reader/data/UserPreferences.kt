package dev.freshleaf.reader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class RowSwipeAction { MARK_READ, TOGGLE_STAR, DISABLED }

data class ReaderPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val swipeStart: RowSwipeAction = RowSwipeAction.MARK_READ,
    val swipeEnd: RowSwipeAction = RowSwipeAction.TOGGLE_STAR,
)

class UserPreferences(context: Context) {
    private val store = context.getSharedPreferences("reader_preferences", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ReaderPreferences> = _state.asStateFlow()

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }
    fun setSwipeStart(value: RowSwipeAction) = update { it.copy(swipeStart = value) }
    fun setSwipeEnd(value: RowSwipeAction) = update { it.copy(swipeEnd = value) }

    private fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        val updated = transform(_state.value)
        store.edit().putString("theme", updated.themeMode.name)
            .putString("swipe_start", updated.swipeStart.name)
            .putString("swipe_end", updated.swipeEnd.name).apply()
        _state.value = updated
    }

    private fun load() = ReaderPreferences(
        themeMode = enum("theme", ThemeMode.SYSTEM),
        swipeStart = enum("swipe_start", RowSwipeAction.MARK_READ),
        swipeEnd = enum("swipe_end", RowSwipeAction.TOGGLE_STAR),
    )

    private inline fun <reified T : Enum<T>> enum(key: String, fallback: T): T =
        store.getString(key, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
