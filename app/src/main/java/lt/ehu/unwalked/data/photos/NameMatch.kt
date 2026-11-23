package lt.ehu.unwalked.data.photos

import java.text.Normalizer

object NameMatch {
    fun similar(a: String, b: String): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        if (left.length < MIN_LENGTH || right.length < MIN_LENGTH) return false
        if (left == right || left.contains(right) || right.contains(left)) return true
        val leftWords = left.split(' ').toSet()
        val rightWords = right.split(' ').toSet()
        return (leftWords intersect rightWords).size.toDouble() / minOf(leftWords.size, rightWords.size) >= SHARED_WORDS
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex(" +"), " ")
            .trim()

    private const val MIN_LENGTH = 3
    private const val SHARED_WORDS = 0.6
}
