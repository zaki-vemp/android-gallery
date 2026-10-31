package com.gallery.android.ml

/**
 * Turns a natural-language search like "my wife standing besides the tree"
 * into weighted concept tokens that can match cached vision labels and OCR.
 */
object ConceptLexicon {

    data class QueryTerm(
        val word: String,
        val aliases: Set<String>,
        val weight: Float,
    )

    private val stopWords = setOf(
        "a", "an", "the", "my", "our", "your", "his", "her", "their", "this", "that",
        "in", "on", "at", "of", "to", "for", "from", "with", "and", "or", "is", "are",
        "was", "were", "be", "been", "by", "near", "next", "beside", "besides", "besides",
        "alongside", "along", "into", "onto", "over", "under", "about", "like",
        "me", "i", "we", "you", "it", "its", "some", "any", "all", "of",
        "picture", "photo", "image", "pic", "shot", "showing", "show", "where",
        "who", "which", "there", "here", "please", "find", "look", "looking",
    )

    private val weakVerbs = setOf(
        "standing", "stand", "sitting", "sit", "walking", "walk", "posing", "pose",
        "holding", "hold", "smiling", "smile", "playing", "play", "running", "run",
        "lying", "sleeping", "eating", "drinking",
    )

    private val groups = listOf(
        setOf(
            "person", "people", "human", "man", "men", "woman", "women", "lady", "ladies",
            "guy", "girl", "girls", "boy", "boys", "child", "children", "kid", "kids",
            "baby", "babies", "adult", "face", "portrait", "selfie", "someone",
            "wife", "husband", "mom", "mum", "mother", "dad", "father", "papa",
            "sister", "brother", "son", "daughter", "family", "friend", "friends",
            "couple", "wedding", "bride", "groom", "grandma", "grandpa",
        ),
        setOf("tree", "trees", "plant", "plants", "forest", "woods", "wood", "oak", "palm", "garden", "park", "leaf", "leaves"),
        setOf("flower", "flowers", "rose", "tulip", "bouquet", "petal"),
        setOf("beach", "sea", "ocean", "shore", "seashore", "sand", "waves", "coast"),
        setOf("mountain", "mountains", "hill", "hills", "alp", "valley", "cliff", "peak"),
        setOf("sky", "cloud", "clouds", "sunset", "sunrise", "dusk", "dawn", "sun"),
        setOf("water", "lake", "lakeside", "river", "pond", "waterfall", "pool"),
        setOf("dog", "dogs", "puppy", "puppies", "canine", "pet"),
        setOf("cat", "cats", "kitten", "kittens", "feline"),
        setOf("car", "cars", "vehicle", "auto", "automobile", "jeep", "truck", "bus"),
        setOf("food", "meal", "pizza", "burger", "cake", "coffee", "dinner", "lunch", "breakfast", "restaurant", "plate"),
        setOf("building", "house", "home", "apartment", "architecture", "castle", "church", "temple"),
        setOf("night", "dark", "lights", "neon", "city", "street"),
        setOf("snow", "winter", "ice", "cold"),
        setOf("rain", "umbrella", "wet"),
        setOf("bike", "bicycle", "motorcycle", "cycle"),
        setOf("airplane", "plane", "flight", "airport", "airliner"),
        setOf("boat", "ship", "yacht", "ocean liner"),
        setOf("document", "receipt", "screenshot", "text", "paper", "note", "invoice"),
        setOf("grass", "lawn", "field", "meadow"),
        setOf("birthday", "party", "celebration", "cake"),
    )

    private val lookup: Map<String, Set<String>> = buildMap {
        groups.forEach { group ->
            group.forEach { word -> put(word, group) }
        }
    }

    fun parse(query: String): List<QueryTerm> {
        val words = query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
        return words.map { word ->
            val aliases = (lookup[word] ?: setOf(word)) + word
            val weight = when {
                word in weakVerbs -> 0.35f
                lookup[word]?.any { it in setOf("person", "people") } == true &&
                    word !in setOf("person", "people", "human") -> 1.15f
                else -> 1f
            }
            QueryTerm(word = word, aliases = aliases, weight = weight)
        }
    }

    fun conceptsForLabels(labels: Collection<String>): String {
        val out = linkedSetOf<String>()
        labels.forEach { raw ->
            raw.lowercase().split(Regex("[,/|]")).map { it.trim() }.filter { it.isNotBlank() }.forEach { label ->
                out += label
                lookup[label]?.let { out += it }
                label.split(' ').filter { it.length >= 3 }.forEach { part ->
                    out += part
                    lookup[part]?.let { out += it }
                }
            }
        }
        return out.joinToString(" ")
    }
}
