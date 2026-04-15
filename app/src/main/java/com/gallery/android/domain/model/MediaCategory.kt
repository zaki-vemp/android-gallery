package com.gallery.android.domain.model

enum class MediaCategory(val displayName: String, val icon: String) {
    PEOPLE("People", "person"),
    DOCUMENTS("Documents", "description"),
    SCREENSHOTS("Screenshots", "screenshot"),
    FOOD("Food", "restaurant"),
    TRAVEL("Travel", "flight_takeoff"),
    VIDEOS("Videos", "videocam"),
    FAVORITES("Favorites", "favorite"),
    OTHERS("Others", "photo"),
}
