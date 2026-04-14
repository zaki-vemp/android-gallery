package com.gallery.android.utils

import com.gallery.android.domain.model.MediaCategory

object CategoryClassifier {

    private val screenshotPaths = setOf(
        "screenshot", "screenshots", "screen recording", "screenrecord",
    )
    private val documentKeywords = setOf(
        "receipt", "invoice", "document", "scan", "contract", "pdf",
        "ticket", "boarding", "visa", "passport", "id card", "license",
        "certificate", "letter", "form", "note",
    )
    private val foodKeywords = setOf(
        "food", "recipe", "menu", "restaurant", "cafe", "kitchen",
        "meal", "lunch", "dinner", "breakfast", "snack", "drink", "coffee",
    )
    private val travelKeywords = setOf(
        "travel", "trip", "vacation", "holiday", "tour", "flight",
        "airport", "hotel", "beach", "mountain", "city", "road", "map",
        "landmark", "monument", "museum", "park",
    )
    private val peopleKeywords = setOf(
        "selfie", "portrait", "people", "person", "face", "family",
        "friend", "wedding", "birthday", "graduation",
    )

    /**
     * Classify a media item by bucket/folder name and optionally extracted OCR text.
     */
    fun classify(
        bucketName: String,
        mimeType: String,
        ocrText: String = "",
    ): MediaCategory {
        val bucket = bucketName.lowercase()
        val ocr = ocrText.lowercase()

        if (mimeType.startsWith("video/")) return MediaCategory.VIDEOS

        if (screenshotPaths.any { bucket.contains(it) }) return MediaCategory.SCREENSHOTS

        if (documentKeywords.any { bucket.contains(it) || ocr.contains(it) })
            return MediaCategory.DOCUMENTS

        if (foodKeywords.any { bucket.contains(it) || ocr.contains(it) })
            return MediaCategory.FOOD

        if (travelKeywords.any { bucket.contains(it) || ocr.contains(it) })
            return MediaCategory.TRAVEL

        if (peopleKeywords.any { bucket.contains(it) || ocr.contains(it) })
            return MediaCategory.PEOPLE

        return MediaCategory.OTHERS
    }
}
