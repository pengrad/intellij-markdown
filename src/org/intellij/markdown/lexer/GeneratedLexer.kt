package org.intellij.markdown.lexer

import org.intellij.markdown.IElementType

interface GeneratedLexer {

    val tokenStart: Int

    val tokenEnd: Int
    fun reset(buffer: CharSequence, start: Int, end: Int, initialState: Int)

    fun advance(): IElementType?

}

fun <E> ArrayList<E>.pop(): E {
    val result = last()
    dropLast(1)
    return result;
}
