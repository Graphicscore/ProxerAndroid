package me.proxer.app.util.wrapper

/**
 * Represents a navigation section in the app's main navigation drawer.
 */
enum class DrawerItem(val id: Long) {
    NEWS(0L),
    CHAT(1L),
    MESSENGER(1L),
    BOOKMARKS(2L),
    ANIME(3L),
    SCHEDULE(4L),
    MANGA(5L),
    INFO(10L),
    SETTINGS(11L),
    ;

    companion object {
        fun fromIdOrNull(id: Long?) = values().firstOrNull { it.id == id }

        fun fromIdOrDefault(id: Long?) = fromIdOrNull(id) ?: NEWS
    }
}
