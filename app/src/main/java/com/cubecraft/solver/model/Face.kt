package com.cubecraft.solver.model

enum class Face(val symbol: Char) {
    U('U'), R('R'), F('F'), D('D'), L('L'), B('B');
    companion object { fun fromSymbol(c: Char) = entries.first { it.symbol == c.uppercaseChar() } }
}
