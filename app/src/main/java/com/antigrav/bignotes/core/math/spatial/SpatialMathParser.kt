package com.antigrav.bignotes.core.math.spatial

import com.antigrav.bignotes.core.math.spatial.detector.ExponentDetector
import com.antigrav.bignotes.core.math.spatial.detector.FractionDetector
import com.antigrav.bignotes.core.model.Stroke

/**
 * Orchestrates multi-pass spatial layout parsing for handwritten math:
 * 1. Isolates 2D vertical fractions into (Numerator) / (Denominator).
 * 2. Isolates superscript exponents into (Base) ^ (Exponent).
 * 3. Synthesizes a clean, standard linear mathematical string ready for MathEvaluator.
 */
object SpatialMathParser {

    /**
     * Parses the handwritten [strokes] by detecting spatial 2D relationships (fractions, exponents)
     * and calls [recognizeStrokes] on individual sub-expression stroke groups.
     *
     * @param strokes The raw handwritten strokes selected on the canvas.
     * @param recognizeStrokes A suspending function that runs ML Kit OCR on a sub-cluster of strokes.
     * @return A synthesized linear math expression string, e.g. "(10 + 2) / 3" or "(2)^(3) + 4".
     */
    suspend fun parseAndRecognize(
        strokes: List<Stroke>,
        recognizeStrokes: suspend (List<Stroke>) -> String
    ): String {
        if (strokes.isEmpty()) return ""

        // Pass 1: 2D Fraction Detection
        val fraction = FractionDetector.detectFraction(strokes)
        if (fraction != null) {
            val prefixText = if (fraction.prefixStrokes.isNotEmpty()) {
                parseLinearGroup(fraction.prefixStrokes, recognizeStrokes)
            } else ""

            val numText = parseLinearGroup(fraction.numeratorStrokes, recognizeStrokes)
            val denText = parseLinearGroup(fraction.denominatorStrokes, recognizeStrokes)

            val suffixText = if (fraction.suffixStrokes.isNotEmpty()) {
                parseLinearGroup(fraction.suffixStrokes, recognizeStrokes)
            } else ""

            val sb = StringBuilder()
            if (prefixText.isNotBlank()) sb.append(prefixText.trim()).append(" ")
            sb.append("(($numText) / ($denText))")
            if (suffixText.isNotBlank()) sb.append(" ").append(suffixText.trim())

            return sb.toString().trim()
        }

        // Pass 2: Exponent / Linear Detection
        return parseLinearGroup(strokes, recognizeStrokes)
    }

    /**
     * Processes a horizontally aligned group of strokes (e.g. a single line, numerator, or denominator)
     * checking for base-superscript exponent relationships.
     */
    private suspend fun parseLinearGroup(
        strokes: List<Stroke>,
        recognizeStrokes: suspend (List<Stroke>) -> String
    ): String {
        if (strokes.isEmpty()) return ""

        val segments = ExponentDetector.segmentExponents(strokes)
        val hasPower = segments.any { it is ExponentDetector.MathSegment.Power }

        if (!hasPower) {
            // Standard linear OCR
            return recognizeStrokes(strokes)
        }

        val sb = StringBuilder()
        for (seg in segments) {
            when (seg) {
                is ExponentDetector.MathSegment.Normal -> {
                    val txt = recognizeStrokes(seg.strokes)
                    if (txt.isNotBlank()) {
                        sb.append(txt.trim()).append(" ")
                    }
                }
                is ExponentDetector.MathSegment.Power -> {
                    val baseText = recognizeStrokes(seg.baseStrokes).trim()
                    val expText = recognizeStrokes(seg.exponentStrokes).trim()
                    if (baseText.isNotBlank() && expText.isNotBlank()) {
                        sb.append("(${baseText})^(${expText}) ")
                    } else if (baseText.isNotBlank()) {
                        sb.append(baseText).append(" ")
                    }
                }
            }
        }

        return sb.toString().trim()
    }
}
