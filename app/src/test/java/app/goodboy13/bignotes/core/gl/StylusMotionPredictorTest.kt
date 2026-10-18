package app.goodboy13.bignotes.core.gl

import app.goodboy13.bignotes.core.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test

class StylusMotionPredictorTest {

    @Test
    fun testPrediction_straightLineMotion() {
        val predictor = StylusMotionPredictor(lookaheadMs = 10f, maxPredictionDistanceScreenPx = 30f)

        // Points moving along X at 1 px/ms
        predictor.addPoint(StrokePoint(0f, 100f, 0.5f, 1000L))
        predictor.addPoint(StrokePoint(10f, 100f, 0.5f, 1010L))
        predictor.addPoint(StrokePoint(20f, 100f, 0.5f, 1020L))

        val pred = predictor.predict()
        assertNotNull("Predictor should extrapolate along straight line", pred)
        assertEquals("X should be predicted ~10px ahead (at 30px)", 30f, pred!!.x, 1.0f)
        assertEquals("Y should remain 100", 100f, pred.y, 0.01f)
    }

    @Test
    fun testPrediction_sharpTurnSuppression() {
        val predictor = StylusMotionPredictor(lookaheadMs = 10f)

        // Moving right (+X), then sharply turning 90 degrees down (+Y)
        predictor.addPoint(StrokePoint(0f, 100f, 0.5f, 1000L))
        predictor.addPoint(StrokePoint(20f, 100f, 0.5f, 1010L))
        predictor.addPoint(StrokePoint(20f, 120f, 0.5f, 1020L)) // 90 degree turn

        val pred = predictor.predict()
        assertNull("Sharp 90-degree turn should suppress prediction to prevent overshoot", pred)
    }

    @Test
    fun testPrediction_stationarySuppression() {
        val predictor = StylusMotionPredictor(lookaheadMs = 10f)

        // Pen held still
        predictor.addPoint(StrokePoint(50f, 50f, 0.5f, 1000L))
        predictor.addPoint(StrokePoint(50f, 50f, 0.5f, 1010L))
        predictor.addPoint(StrokePoint(50f, 50f, 0.5f, 1020L))

        val pred = predictor.predict()
        assertNull("Stationary stylus should not produce predicted movement", pred)
    }

    @Test
    fun testPrediction_maxDistanceClamp() {
        // High speed: 10 px/ms with lookahead 10ms would be 100px
        // But max clamp is 20px
        val predictor = StylusMotionPredictor(lookaheadMs = 10f, maxPredictionDistanceScreenPx = 20f)

        predictor.addPoint(StrokePoint(0f, 0f, 0.5f, 1000L))
        predictor.addPoint(StrokePoint(100f, 0f, 0.5f, 1010L))
        predictor.addPoint(StrokePoint(200f, 0f, 0.5f, 1020L))

        val pred = predictor.predict()
        assertNotNull(pred)
        assertEquals("Prediction should clamp to maxPredictionDistance (200 + 20 = 220)", 220f, pred!!.x, 0.5f)
    }
}
