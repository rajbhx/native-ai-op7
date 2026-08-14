package com.engine.nativeai

import kotlin.math.pow

/**
 * Restricted arithmetic evaluator (spec §14): numbers, + - * / ^ ( ) only.
 * No variables, no functions, no arbitrary code — the model's expression is
 * never executed as code.
 */
object SafeExpr {

    fun evaluate(input: String): Double {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) throw IllegalArgumentException("empty expression")
        var pos = 0
        val value = parseExpr(tokens) { pos }
        if (pos != tokens.size) throw IllegalArgumentException("unexpected token: ${tokens[pos]}")
        return value
    }

    private enum class T { NUM, ADD, SUB, MUL, DIV, POW, LP, RP }

    private class Tok(val t: T, val num: Double = 0.0, val raw: String = "")

    private fun tokenize(input: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    val raw = input.substring(start, i)
                    out.add(Tok(T.NUM, raw.toDouble(), raw))
                }
                c == '+' -> { out.add(Tok(T.ADD)); i++ }
                c == '-' -> { out.add(Tok(T.SUB)); i++ }
                c == '*' -> { out.add(Tok(T.MUL)); i++ }
                c == '/' -> { out.add(Tok(T.DIV)); i++ }
                c == '^' -> { out.add(Tok(T.POW)); i++ }
                c == '(' -> { out.add(Tok(T.LP)); i++ }
                c == ')' -> { out.add(Tok(T.RP)); i++ }
                else -> throw IllegalArgumentException("unsupported character '$c'")
            }
        }
        return out
    }

    private fun parseExpr(tokens: List<Tok>, pos: Ref): Double {
        var value = parseTerm(tokens, pos)
        while (pos.value < tokens.size &&
            (tokens[pos.value].t == T.ADD || tokens[pos.value].t == T.SUB)
        ) {
            val op = tokens[pos.value].t
            pos.value++
            val rhs = parseTerm(tokens, pos)
            value = if (op == T.ADD) value + rhs else value - rhs
        }
        return value
    }

    private fun parseTerm(tokens: List<Tok>, pos: Ref): Double {
        var value = parseFactor(tokens, pos)
        while (pos.value < tokens.size &&
            (tokens[pos.value].t == T.MUL || tokens[pos.value].t == T.DIV)
        ) {
            val op = tokens[pos.value].t
            pos.value++
            val rhs = parseFactor(tokens, pos)
            value = if (op == T.MUL) value * rhs else {
                if (rhs == 0.0) throw IllegalArgumentException("division by zero")
                value / rhs
            }
        }
        return value
    }

    private fun parseFactor(tokens: List<Tok>, pos: Ref): Double {
        val base = parsePrimary(tokens, pos)
        if (pos.value < tokens.size && tokens[pos.value].t == T.POW) {
            pos.value++
            val exp = parseFactor(tokens, pos)
            return base.pow(exp)
        }
        return base
    }

    private fun parsePrimary(tokens: List<Tok>, pos: Ref): Double {
        if (pos.value >= tokens.size) throw IllegalArgumentException("unexpected end")
        return when (tokens[pos.value].t) {
            T.NUM -> tokens[pos.value++].num
            T.LP -> {
                pos.value++
                val v = parseExpr(tokens, pos)
                if (pos.value >= tokens.size || tokens[pos.value].t != T.RP) {
                    throw IllegalArgumentException("missing closing parenthesis")
                }
                pos.value++
                v
            }
            T.SUB -> { // unary minus
                pos.value++
                -parsePrimary(tokens, pos)
            }
            else -> throw IllegalArgumentException("unexpected token")
        }
    }

    private class Ref(var value: Int)
}
