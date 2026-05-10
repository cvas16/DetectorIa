package com.securepass.vision.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.securepass.vision.ui.components.GraphicOverlay

/** Instancia gráfica para renderizar información de inferencia (latencia, FPS, resolución) en una vista de superposición. */
class InferenceInfoGraphic(
    private val overlay: GraphicOverlay,
    private val frameLatency: Long = 0,
    private val detectorLatency: Long = 0,
    private val framesPerSecond: Int? = null,
    private var showLatencyInfo: Boolean = true
) : GraphicOverlay.Graphic(overlay) {

    private val textPaint: Paint = Paint().apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE
        setShadowLayer(5.0f, 0f, 0f, Color.BLACK)
    }

    init {
        postInvalidate()
    }

    @Synchronized
    override fun draw(canvas: Canvas) {
        val x = TEXT_SIZE * 0.5f
        val y = TEXT_SIZE * 1.5f

        canvas.drawText(
            "InputImage size: ${overlay.imageHeight}x${overlay.imageWidth}",
            x,
            y,
            textPaint
        )

        if (!showLatencyInfo) {
            return
        }

        // Dibuja FPS (si es válido) y latencia de inferencia
        if (framesPerSecond != null) {
            canvas.drawText(
                "FPS: $framesPerSecond, Frame latency: $frameLatency ms",
                x,
                y + TEXT_SIZE,
                textPaint
            )
        } else {
            canvas.drawText("Frame latency: $frameLatency ms", x, y + TEXT_SIZE, textPaint)
        }
        canvas.drawText(
            "Detector latency: $detectorLatency ms", x, y + TEXT_SIZE * 2, textPaint
        )
    }

    companion object {
        private const val TEXT_COLOR = Color.WHITE
        private const val TEXT_SIZE = 60.0f
    }
}
