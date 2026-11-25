package com.example.keyboardtouchdemo

object AppSettings {
    const val PREFS = "keyboard_touch_settings"
    const val BLACKLIST_PACKAGES = "blacklist_packages"

    fun parseBlacklist(value: String?): Set<String> =
        value.orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
}
