package org.intellij.markdown.parser.markerblocks.impl

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkdownParserUtil
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl

class ListItemMarkerBlock(myConstraints: MarkdownConstraints,
                                 marker: ProductionHolder.Marker) : MarkerBlockImpl(myConstraints, marker) {
    override fun allowsSubBlocks(): Boolean = true

    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = pos.offsetInCurrentLine == -1

    override fun getDefaultAction(): MarkerBlock.ClosingAction {
        return MarkerBlock.ClosingAction.DONE
    }

    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int {
        return pos.nextLineOffset ?: -1
    }

    override fun doProcessToken(pos: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
        assert(pos.offsetInCurrentLine == -1)

        val eolN = MarkdownParserUtil.calcNumberOfConsequentEols(pos, constraints)
        if (eolN >= 3) {
            return MarkerBlock.ProcessingResult.DEFAULT
        }

        val nonemptyPos = MarkdownParserUtil.getFirstNonWhitespaceLinePos(pos, eolN)
                ?: return MarkerBlock.ProcessingResult.DEFAULT
        val nextLineConstraints = MarkdownConstraints.fromBase(nonemptyPos, constraints)
        if (!nextLineConstraints.extendsPrev(constraints)) {
            return MarkerBlock.ProcessingResult.DEFAULT
        }

        return MarkerBlock.ProcessingResult.CANCEL
    }

    override fun getDefaultNodeType(): IElementType {
        return MarkdownElementTypes.LIST_ITEM
    }
}
