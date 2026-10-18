package app.goodboy13.bignotes.core.math.normalizer

import java.util.Locale

/**
 * Normalizes raw handwritten OCR text into a clean mathematical expression string
 * ready for the parser/evaluator.
 */
object MathNormalizer {

    /**
     * Normalizes recognized handwritten text.
     * Handles common OCR substitutions, operators, implicit multiplication, and trailing '='.
     */
    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // 1. Remove trailing '=' or question mark if present (even if multiple or separated by spaces)
        text = text.replace(Regex("[\\s=\\?]+$"), "").trim()

        // 2. Normalize unicode operator symbols
        text = text
            .replace('×', '*')
            .replace('✕', '*')
            .replace('✖', '*')
            .replace('·', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('＋', '+')
            .replace('﹢', '+')
            .replace('➕', '+')
            .replace('†', '+')
            .replace('‡', '+')
            .replace('十', '+')
            .replace("π", "pi")

        // Normalize verbal math operators
        text = text.replace(Regex("(?<=[0-9\\)])\\s*plus\\s*(?=[0-9\\(])", RegexOption.IGNORE_CASE), " + ")
        text = text.replace(Regex("(?<=[0-9\\)])\\s*minus\\s*(?=[0-9\\(])", RegexOption.IGNORE_CASE), " - ")
        text = text.replace(Regex("(?<=[0-9\\)])\\s*times\\s*(?=[0-9\\(])", RegexOption.IGNORE_CASE), " * ")

        // 3. Normalize square root: √16 or √(16) -> sqrt(16)
        text = text.replace("√", "sqrt")
        text = text.replace(Regex("sqrt\\s*([0-9]+(?:\\.[0-9]+)?)")) { match ->
            "sqrt(${match.groupValues[1]})"
        }

        // 4. Resolve 'x' or 'X' as multiplication operator when surrounded by digits/spaces
        // Preserve surrounding spacing if present, e.g. "12 x 4" -> "12 * 4", "12x4" -> "12*4"
        text = text.replace(Regex("(?<=[0-9\\)])\\s+[xX]\\s+(?=[0-9\\(]|pi|e|sqrt)"), " * ")
        text = text.replace(Regex("(?<=[0-9\\)])\\s*[xX]\\s*(?=[0-9\\(]|pi|e|sqrt)"), "*")

        // 4b. Resolve 't' or 'T' misrecognized as '+' when flanked by digits/spaces (e.g. "12 t 4" -> "12 + 4")
        text = text.replace(Regex("(?<=[0-9\\)])\\s*[tT]\\s*(?=[0-9\\(]|pi|e|sqrt)"), " + ")
        text = text.replace(Regex("^[tT]\\s*(?=[0-9\\(])"), "+ ")

        // 5. Implicit multiplication:
        // E.g., "2(3+4)" -> "2*(3+4)"
        text = text.replace(Regex("(?<=[0-9])\\s*\\("), "*(")
        // E.g., "(2+3)(4+5)" -> "(2+3)*(4+5)"
        text = text.replace(Regex("\\)\\s*\\("), ")*(")
        // E.g., ")4" -> ")*4"
        text = text.replace(Regex("\\)\\s*(?=[0-9])"), ")*")
        // E.g., "3pi" -> "3*pi", "3e" -> "3*e", "2sqrt" -> "2*sqrt"
        text = text.replace(Regex("(?<=[0-9])\\s*(?=(?:pi|e|sqrt|cbrt|sin|cos|tan|asin|acos|atan|ln|log|abs|exp)\\b)"), "*")

        // 6. Percentage: "50%" -> "(50/100)"
        text = text.replace(Regex("([0-9]+(?:\\.[0-9]+)?)\\s*%")) { match ->
            "(${match.groupValues[1]}/100)"
        }

        // 7. Thousand separators: e.g. "1,000" -> "1000"
        text = text.replace(Regex("(?<=[0-9]),(?=[0-9]{3}\\b)"), "")

        // 8. Decimal commas: e.g. "3,5" -> "3.5" (if not followed by 3 digits)
        text = text.replace(Regex("(?<=[0-9]),(?=[0-9])"), ".")

        // 9. Clean up redundant whitespace around operators
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }

    /**
     * Checks if the text looks like an evaluatable mathematical expression.
     */
    fun isPotentialMath(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return false

        // Check for presence of numbers and common math operators
        val hasDigits = trimmed.any { it.isDigit() }
        val hasOperators = trimmed.any { it in "+-*/^%=÷×" } ||
                trimmed.contains("sqrt", ignoreCase = true) ||
                trimmed.contains("sin", ignoreCase = true) ||
                trimmed.contains("cos", ignoreCase = true) ||
                trimmed.contains("tan", ignoreCase = true) ||
                trimmed.contains("log", ignoreCase = true) ||
                trimmed.contains("ln", ignoreCase = true)

        return hasDigits && (hasOperators || trimmed.endsWith("="))
    }
}
