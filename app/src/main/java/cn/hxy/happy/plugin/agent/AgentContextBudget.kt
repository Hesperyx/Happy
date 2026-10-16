package cn.hxy.happy.plugin.agent

/**
 * Keeps a chat request inside a character budget without cutting the messages that matter.
 *
 * Tool-heavy sessions grow fast (file listings plus whole scripts, several rounds deep), and a
 * request that exceeds the endpoint's context window fails as an opaque 400. Trimming the *oldest*
 * turns while pinning the system prompt and the current user request turns that failure into a
 * graceful degradation. Callers supply [sizeOf] so this stays independent of the message type.
 */
object AgentContextBudget {

    /** Roughly 6k tokens of mixed Chinese/code — comfortably inside common 8k–32k windows. */
    const val DEFAULT_CHAR_BUDGET = 24_000

    /** Newest items are never dropped, even when they alone overflow the budget. */
    const val DEFAULT_KEEP_TAIL = 6

    data class Trimmed<T>(
        val items: List<T>,
        val dropped: Int,
        val chars: Int,
    ) {
        val wasTrimmed: Boolean get() = dropped > 0

        fun notice(): String? =
            if (dropped == 0) null else "（较早的 $dropped 条会话记录已省略以节省上下文）"
    }

    /**
     * Keeps every item satisfying [pinned], then the newest items that fit [budget], then at least
     * [keepTail] newest items in total. Order is preserved.
     */
    fun <T> trim(
        items: List<T>,
        budget: Int = DEFAULT_CHAR_BUDGET,
        keepTail: Int = DEFAULT_KEEP_TAIL,
        pinned: (T) -> Boolean = { false },
        sizeOf: (T) -> Int,
    ): Trimmed<T> {
        if (items.isEmpty()) return Trimmed(emptyList(), 0, 0)

        val effectiveBudget = budget.coerceAtLeast(1)
        val pinnedIndices = items.indices.filter { pinned(items[it]) }.toSet()
        val tailStart = (items.size - keepTail.coerceAtLeast(0)).coerceAtLeast(0)
        val keepIndices = HashSet(pinnedIndices)

        var used = 0
        var walk = items.lastIndex
        while (walk >= 0) {
            val index = walk
            val cost = sizeOf(items[index]).coerceAtLeast(0)
            val mustKeep = index >= tailStart || index in pinnedIndices
            if (mustKeep || used + cost <= effectiveBudget) {
                keepIndices.add(index)
                used += cost
            }
            walk--
        }

        val kept = items.filterIndexed { index, _ -> index in keepIndices }
        return Trimmed(
            items = kept,
            dropped = items.size - kept.size,
            chars = kept.sumOf { sizeOf(it).coerceAtLeast(0) },
        )
    }

    /**
     * Middle-elides [text] to [maxChars]. Keeping both ends beats a hard cut: the head carries the
     * command or file header, the tail carries the error or the closing lines of a script.
     */
    fun truncate(text: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (text.length <= maxChars) return text
        val marker = "\n…（中间省略 ${text.length - maxChars} 字符）…\n"
        val available = maxChars - marker.length
        if (available <= 0) return text.take(maxChars)
        val head = available * 2 / 3
        val tail = available - head
        return text.take(head) + marker + text.takeLast(tail)
    }
}
