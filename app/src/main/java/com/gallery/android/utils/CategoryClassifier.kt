package com.gallery.android.utils

import com.gallery.android.domain.model.MediaCategory

object CategoryClassifier {

    private val screenshotPaths = setOf(
        "screenshot", "screenshots", "screen recording", "screenrecord",
    )
    private val documentKeywords = setOf(
        "receipt", "invoice", "document", "scan", "contract", "pdf",
        "ticket", "boarding", "visa", "passport", "id card", "license",
        "certificate", "letter", "form", "note", "envelope", "menu",
        "web site", "website", "book jacket", "notebook", "binder",
        "crossword", "comic book", "packet",
    )
    private val foodKeywords = setOf(
        "food", "recipe", "menu", "restaurant", "cafe", "kitchen",
        "meal", "lunch", "dinner", "breakfast", "snack", "drink", "coffee",
        "pizza", "bagel", "pretzel", "hotdog", "hot dog", "cheeseburger",
        "hamburger", "guacamole", "ice cream", "espresso", "wine",
        "beer", "burrito", "carbonara", "broccoli", "strawberry",
        "banana", "orange", "lemon", "pineapple", "mushroom", "plate",
        "potpie", "french loaf", "mashed potato",
    )
    private val travelKeywords = setOf(
        "travel", "trip", "vacation", "holiday", "tour", "flight",
        "airport", "hotel", "beach", "mountain", "city", "road", "map",
        "landmark", "monument", "museum", "park", "seashore", "lakeside",
        "valley", "alp", "volcano", "cliff", "coral reef", "castle",
        "palace", "monastery", "church", "mosque", "lighthouse", "dock",
        "airliner", "ocean liner", "sandbar", "promontory", "geyser",
        "viaduct", "suspension bridge",
    )
    private val peopleKeywords = setOf(
        "selfie", "portrait", "people", "person", "face", "family",
        "friend", "wedding", "birthday", "graduation", "bridegroom",
        "suit", "gown", "bikini", "maillot", "jean", "sweatshirt",
        "academic gown", "lab coat", "military uniform", "lipstick",
        "wig", "bonnet", "mask",
    )

    fun classify(
        bucketName: String,
        mimeType: String,
        ocrText: String = "",
        labels: String = "",
    ): MediaCategory {
        val bucket = bucketName.lowercase()
        val haystack = "$ocrText $labels".lowercase()

        if (mimeType.startsWith("video/")) return MediaCategory.VIDEOS
        if (screenshotPaths.any { bucket.contains(it) }) return MediaCategory.SCREENSHOTS
        if (peopleKeywords.any { bucket.contains(it) || haystack.contains(it) }) return MediaCategory.PEOPLE
        if (documentKeywords.any { bucket.contains(it) || haystack.contains(it) }) return MediaCategory.DOCUMENTS
        if (foodKeywords.any { bucket.contains(it) || haystack.contains(it) }) return MediaCategory.FOOD
        if (travelKeywords.any { bucket.contains(it) || haystack.contains(it) }) return MediaCategory.TRAVEL
        return MediaCategory.OTHERS
    }
}
