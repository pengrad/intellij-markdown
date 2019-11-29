package org.intellij.markdown.parser.constraints

import org.intellij.markdown.lexer.Compat.assert
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.markerblocks.providers.HorizontalRuleProvider
import kotlin.math.min

open class MarkdownConstraints protected constructor(private val indents: IntArray,
                                                            private val types: CharArray,
                                                            private val isExplicit: BooleanArray,
                                                            private val charsEaten: Int) {

    open val base: MarkdownConstraints
        get() = BASE

    open fun createNewConstraints(indents: IntArray,
                                         types: CharArray,
                                         isExplicit: BooleanArray,
                                         charsEaten: Int): MarkdownConstraints {
        return MarkdownConstraints(indents, types, isExplicit, charsEaten)
    }

    fun eatItselfFromString(s: CharSequence): CharSequence {
        if (s.length < charsEaten) {
            return ""
        } else {
            return s.subSequence(charsEaten, s.length)
        }
    }

    fun getIndent(): Int {
        if (indents.isEmpty()) {
            return 0
        }

        return indents.last()
    }

    fun getCharsEaten(s: CharSequence): Int {
        return min(charsEaten, s.length)
    }

    open fun getLastType(): Char? {
        return types.lastOrNull()
    }

    fun getLastExplicit(): Boolean? {
        return isExplicit.lastOrNull()
    }

    fun upstreamWith(other: MarkdownConstraints): Boolean {
        return other.startsWith(this) && !containsListMarkers()
    }

    fun extendsPrev(other: MarkdownConstraints): Boolean {
        return startsWith(other) && !containsListMarkers(other.types.size)
    }

    fun extendsList(other: MarkdownConstraints): Boolean {
        if (other.types.isEmpty()) {
            throw IllegalArgumentException("List constraints should contain at least one item")
        }
        return startsWith(other) && !containsListMarkers(other.types.size - 1)
    }

    private fun startsWith(other: MarkdownConstraints): Boolean {
        val n = indents.size
        val m = other.indents.size

        if (n < m) {
            return false
        }
        return (0..m - 1).none { types[it] != other.types[it] }
    }

    private fun containsListMarkers(): Boolean {
        return containsListMarkers(types.size)
    }

    private fun containsListMarkers(upToIndex: Int): Boolean {
        return (0..upToIndex - 1).any { types[it] != BQ_CHAR && isExplicit[it] }
    }

    fun addModifierIfNeeded(pos: LookaheadText.Position?): MarkdownConstraints? {
        if (pos == null || pos.offsetInCurrentLine == -1)
            return null
        if (HorizontalRuleProvider.isHorizontalRule(pos.currentLine, pos.offsetInCurrentLine)) {
            return null
        }
        return tryAddListItem(pos) ?: tryAddBlockQuote(pos)
    }

    protected open fun fetchListMarker(pos: LookaheadText.Position): ListMarkerInfo? {
        val c = pos.char
        if (c == '*' || c == '-' || c == '+') {
            return ListMarkerInfo(1, c, 1)
        }

        val line = pos.currentLine
        var offset = pos.offsetInCurrentLine
        while (offset < line.length && line[offset] in '0'..'9') {
            offset++
        }
        if (offset > pos.offsetInCurrentLine
                && offset - pos.offsetInCurrentLine <= 9
                && offset < line.length
                && (line[offset] == '.' || line[offset] == ')')) {
            return ListMarkerInfo(offset + 1 - pos.offsetInCurrentLine,
                    line[offset],
                    offset + 1 - pos.offsetInCurrentLine)
        } else {
            return null
        }
    }

    protected data class ListMarkerInfo(val markerLength: Int, val markerType: Char, val markerIndent: Int)


    private fun tryAddListItem(pos: LookaheadText.Position): MarkdownConstraints? {
        val line = pos.currentLine

        var offset = pos.offsetInCurrentLine
        var spacesBefore = if (offset > 0 && line[offset - 1] == '\t')
            (4 - getIndent() % 4) % 4
        else
            0
        // '\t' can be omitted here since it'll add at least 4 indent
        while (offset < line.length && line[offset] == ' ' && spacesBefore < 3) {
            spacesBefore++
            offset++
        }
        if (offset == line.length)
            return null

        val markerInfo = fetchListMarker(pos.nextPosition(offset - pos.offsetInCurrentLine)!!)
                ?: return null

        offset += markerInfo.markerLength
        var spacesAfter = 0

        val markerEndOffset = offset
        afterSpaces@
        while (offset < line.length) {
            when (line[offset]) {
                ' ' -> spacesAfter++
                '\t' -> spacesAfter += 4 - spacesAfter % 4
                else -> break@afterSpaces
            }
            offset++
        }

        // By the classification http://spec.commonmark.org/0.20/#list-items
        // 1. Basic case
        if (spacesAfter > 0 && spacesAfter < 5 && offset < line.length) {
            return MarkdownConstraints(this, spacesBefore + markerInfo.markerIndent + spacesAfter, markerInfo.markerType, true, offset)
        }
        if (spacesAfter >= 5 && offset < line.length // 2. Starts with an indented code
                || offset == line.length) {
            // 3. Starts with an empty string
            return MarkdownConstraints(this, spacesBefore + markerInfo.markerIndent + 1, markerInfo.markerType, true,
                    min(offset, markerEndOffset + 1))
        }

        return null
    }

    private fun tryAddBlockQuote(pos: LookaheadText.Position): MarkdownConstraints? {
        val line = pos.currentLine

        var offset = pos.offsetInCurrentLine
        var spacesBefore = 0
        // '\t' can be omitted here since it'll add at least 4 indent
        while (offset < line.length && line[offset] == ' ' && spacesBefore < 3) {
            spacesBefore++
            offset++
        }
        if (offset == line.length || line[offset] != BQ_CHAR) {
            return null
        }
        offset++

        var spacesAfter = 0
        if (offset >= line.length || line[offset] == ' ' || line[offset] == '\t') {
            spacesAfter = 1

            if (offset < line.length) {
                offset++
            }
        }

        return MarkdownConstraints(this, spacesBefore + 1 + spacesAfter, BQ_CHAR, true, offset)
    }

    override fun toString(): String {
        return "MdConstraints: " + types + "(" + getIndent() + ")"
    }

    companion object {
        val BASE: MarkdownConstraints = MarkdownConstraints(IntArray(0), CharArray(0), BooleanArray(0), 0)

        val BQ_CHAR: Char = '>'

        private fun MarkdownConstraints(parent: MarkdownConstraints,
                                        newIndentDelta: Int,
                                        newType: Char,
                                        newExplicit: Boolean,
                                        newOffset: Int): MarkdownConstraints {
            val n = parent.indents.size
            val _indents = parent.indents.copyOf(n + 1)
            val _types = parent.types.copyOf(n + 1)
            val _isExplicit = parent.isExplicit.copyOf(n + 1)

            _indents[n] = parent.getIndent() + newIndentDelta
            _types[n] = newType
            _isExplicit[n] = newExplicit
            return parent.createNewConstraints(_indents, _types, _isExplicit, newOffset)
        }

        fun fromBase(pos: LookaheadText.Position, prevLineConstraints: MarkdownConstraints): MarkdownConstraints {
            assert(pos.offsetInCurrentLine == -1)

            var result = fillFromPrevious(pos, prevLineConstraints)
            val line = pos.currentLine

            while (true) {
                val offset = result.getCharsEaten(line)
                result = result.addModifierIfNeeded(pos.nextPosition(1 + offset))
                        ?: break
            }

            return result
        }

        fun fillFromPrevious(pos: LookaheadText.Position?,
                             prevLineConstraints: MarkdownConstraints): MarkdownConstraints {
            if (pos == null) {
                return prevLineConstraints.base
            }
            assert(pos.offsetInCurrentLine == -1, { "given $pos" })

            val line = pos.currentLine
            val startOffset = 0
            val prevN = prevLineConstraints.indents.size
            var indexPrev = 0

            val getBlockQuoteIndent = { startOffset: Int ->
                var offset = startOffset
                var blockQuoteIndent = 0

                // '\t' can be omitted here since it'll add at least 4 indent
                while (blockQuoteIndent < 3 && offset < line.length && line[offset] == ' ') {
                    blockQuoteIndent++
                    offset++
                }

                if (offset < line.length && line[offset] == BQ_CHAR) {
                    blockQuoteIndent + 1
                } else {
                    null
                }
            }

            val fillMaybeBlockquoteAndListIndents = fun(constraints: MarkdownConstraints): MarkdownConstraints {
                if (indexPrev >= prevN) {
                    return constraints
                }

                var offset = startOffset + constraints.getCharsEaten(line)
                var totalSpaces = 0
                var spacesSeen = 0
                val hasKMoreSpaces = { k: Int ->
                    val oldSpacesSeen = spacesSeen
                    val oldOffset = offset
                    afterSpaces@
                    while (spacesSeen < k && offset < line.length) {
                        val deltaSpaces = when (line[offset]) {
                            ' ' -> 1
                            '\t' -> 4 - totalSpaces % 4
                            else -> break@afterSpaces
                        }
                        spacesSeen += deltaSpaces
                        totalSpaces += deltaSpaces
                        offset++
                    }
                    if (offset == line.length) {
                        spacesSeen = Int.MAX_VALUE
                    }

                    if (k <= spacesSeen) {
                        spacesSeen -= k
                        true
                    } else {
                        offset = oldOffset
                        spacesSeen = oldSpacesSeen
                        false
                    }
                }

                val bqIndent: Int?
                if (prevLineConstraints.types[indexPrev] == BQ_CHAR) {
                    bqIndent = getBlockQuoteIndent(offset)
                            ?: return constraints
                    offset += bqIndent
                    indexPrev++
                } else {
                    bqIndent = null
                }

                val oldIndexPrev = indexPrev
                while (indexPrev < prevN && prevLineConstraints.types[indexPrev] != BQ_CHAR) {
                    val deltaIndent = prevLineConstraints.indents[indexPrev] -
                            if (indexPrev == 0)
                                0
                            else
                                prevLineConstraints.indents[indexPrev - 1]

                    if (!hasKMoreSpaces(deltaIndent)) {
                        break
                    }

                    indexPrev++
                }

                var result = constraints
                if (bqIndent != null) {
                    val bonusForTheBlockquote = if (hasKMoreSpaces(1)) 1 else 0
                    result = MarkdownConstraints(result, bqIndent + bonusForTheBlockquote, BQ_CHAR, true, offset)
                }
                for (index in oldIndexPrev..indexPrev - 1) {
                    val deltaIndent = prevLineConstraints.indents[index] -
                            if (index == 0)
                                0
                            else
                                prevLineConstraints.indents[index - 1]
                    result = MarkdownConstraints(result, deltaIndent, prevLineConstraints.types[index], false, offset)
                }
                return result
            }

            var result = prevLineConstraints.base
            while (true) {
                val nextConstraints = fillMaybeBlockquoteAndListIndents(result)
                if (nextConstraints == result) {
                    return result
                }
                result = nextConstraints
            }
        }

    }

}
