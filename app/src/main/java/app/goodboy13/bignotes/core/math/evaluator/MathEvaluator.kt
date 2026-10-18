package app.goodboy13.bignotes.core.math.evaluator

import app.goodboy13.bignotes.core.math.model.MathResult
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.Stack
import kotlin.math.*

/**
 * Pure Kotlin mathematical expression evaluator using Dijkstra's Shunting-Yard algorithm.
 * Evaluates basic arithmetic, powers, scientific functions, and constants with zero external dependencies.
 */
object MathEvaluator {

    private val FUNCTIONS = setOf(
        "sqrt", "cbrt", "sin", "cos", "tan", "asin", "acos", "atan",
        "ln", "log", "log10", "abs", "exp", "floor", "ceil", "round"
    )

    private val CONSTANTS = mapOf(
        "pi" to Math.PI,
        "e" to Math.E
    )

    /**
     * Evaluates a mathematical expression string and returns a [MathResult].
     */
    fun evaluate(expression: String): MathResult {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) {
            return MathResult.Error(expression, "Empty expression")
        }

        return try {
            val tokens = tokenize(trimmed)
            if (tokens.isEmpty()) {
                return MathResult.Error(expression, "No valid tokens found")
            }
            val value = evaluateTokens(tokens)
            if (value.isNaN() || value.isInfinite()) {
                MathResult.Error(expression, "Result is undefined (e.g. division by zero)")
            } else {
                MathResult.Success(expression, value, formatResult(value))
            }
        } catch (e: Exception) {
            MathResult.Error(expression, e.message ?: "Invalid mathematical syntax")
        }
    }

    private sealed class Token {
        data class Number(val value: Double) : Token()
        data class Operator(val symbol: Char, val isUnary: Boolean = false) : Token()
        data class Function(val name: String) : Token()
        object LeftParen : Token()
        object RightParen : Token()
        object Comma : Token()
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var expectUnary = true

        while (i < expr.length) {
            val c = expr[i]

            when {
                c.isWhitespace() -> {
                    i++
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        i++
                    }
                    val numStr = expr.substring(start, i)
                    val num = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
                    tokens.add(Token.Number(num))
                    expectUnary = false
                }
                c.isLetter() -> {
                    val start = i
                    while (i < expr.length && expr[i].isLetterOrDigit()) {
                        i++
                    }
                    val word = expr.substring(start, i).lowercase(Locale.US)
                    if (CONSTANTS.containsKey(word)) {
                        tokens.add(Token.Number(CONSTANTS[word]!!))
                        expectUnary = false
                    } else if (FUNCTIONS.contains(word)) {
                        tokens.add(Token.Function(word))
                        expectUnary = true
                    } else {
                        throw IllegalArgumentException("Unknown identifier: $word")
                    }
                }
                c == '(' -> {
                    tokens.add(Token.LeftParen)
                    expectUnary = true
                    i++
                }
                c == ')' -> {
                    tokens.add(Token.RightParen)
                    expectUnary = false
                    i++
                }
                c == ',' -> {
                    tokens.add(Token.Comma)
                    expectUnary = true
                    i++
                }
                c in "+-*/^%" -> {
                    if (expectUnary && (c == '-' || c == '+')) {
                        tokens.add(Token.Operator(c, isUnary = true))
                    } else {
                        tokens.add(Token.Operator(c, isUnary = false))
                    }
                    expectUnary = true
                    i++
                }
                else -> {
                    throw IllegalArgumentException("Unexpected character: '$c'")
                }
            }
        }

        return tokens
    }

    private fun precedence(op: Token.Operator): Int = when {
        op.isUnary -> 3
        op.symbol == '^' -> 4
        op.symbol in "*/%" -> 2
        op.symbol in "+-" -> 1
        else -> 0
    }

    private fun isRightAssociative(op: Token.Operator): Boolean =
        op.isUnary || op.symbol == '^'

    private fun evaluateTokens(tokens: List<Token>): Double {
        val values = Stack<Double>()
        val ops = Stack<Token>()

        fun applyTopOp() {
            if (ops.isEmpty()) return
            when (val op = ops.pop()) {
                is Token.Operator -> {
                    if (op.isUnary) {
                        if (values.isEmpty()) throw IllegalArgumentException("Missing operand for unary '${op.symbol}'")
                        val a = values.pop()
                        val res = if (op.symbol == '-') -a else a
                        values.push(res)
                    } else {
                        if (values.size < 2) throw IllegalArgumentException("Missing operand for operator '${op.symbol}'")
                        val b = values.pop()
                        val a = values.pop()
                        val res = when (op.symbol) {
                            '+' -> a + b
                            '-' -> a - b
                            '*' -> a * b
                            '/' -> if (b == 0.0) Double.NaN else a / b
                            '%' -> if (b == 0.0) Double.NaN else a % b
                            '^' -> a.pow(b)
                            else -> throw IllegalArgumentException("Unknown operator '${op.symbol}'")
                        }
                        values.push(res)
                    }
                }
                is Token.Function -> {
                    if (values.isEmpty()) throw IllegalArgumentException("Missing argument for function '${op.name}'")
                    val a = values.pop()
                    val res = when (op.name) {
                        "sqrt" -> if (a < 0.0) Double.NaN else sqrt(a)
                        "cbrt" -> cbrt(a)
                        "sin" -> sin(a)
                        "cos" -> cos(a)
                        "tan" -> tan(a)
                        "asin" -> asin(a)
                        "acos" -> acos(a)
                        "atan" -> atan(a)
                        "ln" -> if (a <= 0.0) Double.NaN else ln(a)
                        "log", "log10" -> if (a <= 0.0) Double.NaN else log10(a)
                        "abs" -> abs(a)
                        "exp" -> exp(a)
                        "floor" -> floor(a)
                        "ceil" -> ceil(a)
                        "round" -> round(a)
                        else -> throw IllegalArgumentException("Unsupported function '${op.name}'")
                    }
                    values.push(res)
                }
                else -> throw IllegalArgumentException("Unexpected token in operator stack: $op")
            }
        }

        for (token in tokens) {
            when (token) {
                is Token.Number -> {
                    values.push(token.value)
                }
                is Token.Function -> {
                    ops.push(token)
                }
                is Token.LeftParen -> {
                    ops.push(token)
                }
                is Token.RightParen -> {
                    while (ops.isNotEmpty() && ops.peek() !is Token.LeftParen) {
                        applyTopOp()
                    }
                    if (ops.isEmpty() || ops.peek() !is Token.LeftParen) {
                        throw IllegalArgumentException("Mismatched parentheses")
                    }
                    ops.pop() // Remove '('
                    // If a function precedes the '(', apply it now
                    if (ops.isNotEmpty() && ops.peek() is Token.Function) {
                        applyTopOp()
                    }
                }
                is Token.Comma -> {
                    while (ops.isNotEmpty() && ops.peek() !is Token.LeftParen) {
                        applyTopOp()
                    }
                }
                is Token.Operator -> {
                    val currPrec = precedence(token)
                    while (ops.isNotEmpty()) {
                        val top = ops.peek()
                        if (top is Token.Operator) {
                            val topPrec = precedence(top)
                            if ((!isRightAssociative(token) && currPrec <= topPrec) ||
                                (isRightAssociative(token) && currPrec < topPrec)
                            ) {
                                applyTopOp()
                            } else {
                                break
                            }
                        } else if (top is Token.Function) {
                            applyTopOp()
                        } else {
                            break
                        }
                    }
                    ops.push(token)
                }
            }
        }

        while (ops.isNotEmpty()) {
            val top = ops.peek()
            if (top is Token.LeftParen || top is Token.RightParen) {
                throw IllegalArgumentException("Mismatched parentheses")
            }
            applyTopOp()
        }

        if (values.size != 1) {
            throw IllegalArgumentException("Invalid expression syntax")
        }

        return values.pop()
    }

    /**
     * Formats a floating-point result cleanly for display and ink insertion.
     */
    fun formatResult(value: Double): String {
        if (value.isNaN()) return "Undefined"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

        // Check if value is very close to an integer
        val rounded = round(value)
        if (abs(value - rounded) < 1e-9 && abs(rounded) < 1e14) {
            return rounded.toLong().toString()
        }

        val absVal = abs(value)
        // Use scientific notation for extremely large or tiny numbers
        if (absVal >= 1e12 || (absVal < 1e-4 && absVal != 0.0)) {
            val symbols = DecimalFormatSymbols(Locale.US)
            val df = DecimalFormat("0.######E0", symbols)
            return df.format(value)
        }

        // Standard decimal format, up to 6 decimal places, no trailing zeros
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("#,##0.######", symbols)
        df.isGroupingUsed = false
        return df.format(value)
    }
}
