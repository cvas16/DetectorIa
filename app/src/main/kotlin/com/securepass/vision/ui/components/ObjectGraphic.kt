package com.securepass.vision.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.securepass.vision.ui.components.GraphicOverlay.Graphic
import com.google.mlkit.vision.objects.DetectedObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Dibuja la información del objeto detectado en la vista previa. */
class ObjectGraphic(
  overlay: GraphicOverlay,
  private val detectedObject: DetectedObject,
  private val prohibitedLabels: List<String> = emptyList()
) : Graphic(overlay) {

  private val numColors = COLORS.size

  private val boxPaints = Array(numColors) { Paint() }
  private val textPaints = Array(numColors) { Paint() }
  private val labelPaints = Array(numColors) { Paint() }

  init {
    for (i in 0 until numColors) {
      textPaints[i] = Paint()
      textPaints[i].color = COLORS[i][0]
      textPaints[i].textSize = TEXT_SIZE
      boxPaints[i] = Paint()
      boxPaints[i].color = COLORS[i][1]
      boxPaints[i].style = Paint.Style.STROKE
      boxPaints[i].strokeWidth = STROKE_WIDTH
      labelPaints[i] = Paint()
      labelPaints[i].color = COLORS[i][1]
      labelPaints[i].style = Paint.Style.FILL
    }
  }

  override fun draw(canvas: Canvas) {
    // Determinamos si el objeto está prohibido
    var isProhibited = false
    for (label in detectedObject.labels) {
      val labelText = label.text
      if (prohibitedLabels.any { it.trim().equals(labelText, ignoreCase = true) }) {
        isProhibited = true
        break
      }
    }

    // Rojo para prohibidos
    var colorID: Int
    if (isProhibited) {
      colorID = 3 // El índice 3 es {Blanco, Rojo}
      boxPaints[colorID].strokeWidth = STROKE_WIDTH * 3.0f
    } else {
      colorID = if (detectedObject.trackingId == null) 0
      else abs(detectedObject.trackingId!! % NUM_COLORS)
      if (colorID == 3) colorID = 0 // Reservar el Rojo para alertas
      boxPaints[colorID].strokeWidth = STROKE_WIDTH
    }

    var textWidth =
      textPaints[colorID].measureText("Tracking ID: " + detectedObject.trackingId)
    val lineHeight = TEXT_SIZE + STROKE_WIDTH
    var yLabelOffset = -lineHeight

    // Calcular el ancho y alto del cuadro de la etiqueta
    for (label in detectedObject.labels) {
      val labelText = if (isProhibited) "⚠️ PROHIBIDO: ${label.text}" else label.text
      textWidth = max(textWidth, textPaints[colorID].measureText(labelText))
      textWidth = max(
        textWidth,
        textPaints[colorID].measureText(
          String.format(
            Locale.US,
            LABEL_FORMAT,
            label.confidence * 100,
            label.index
          )
        )
      )
      yLabelOffset -= 2 * lineHeight
    }

    // Dibuja el cuadro delimitador (Bounding Box).
    val rect = RectF(detectedObject.boundingBox)
    val x0 = translateX(rect.left)
    val x1 = translateX(rect.right)
    rect.left = min(x0, x1)
    rect.right = max(x0, x1)
    rect.top = translateY(rect.top)
    rect.bottom = translateY(rect.bottom)
    canvas.drawRect(rect, boxPaints[colorID])

    // Dibuja otra información del objeto (ID de rastreo y etiquetas).
    canvas.drawRect(
      rect.left - STROKE_WIDTH,
      rect.top + yLabelOffset,
      rect.left + textWidth + 2 * STROKE_WIDTH,
      rect.top,
      labelPaints[colorID]
    )
    yLabelOffset += TEXT_SIZE
    canvas.drawText(
      "Tracking ID: " + detectedObject.trackingId,
      rect.left,
      rect.top + yLabelOffset,
      textPaints[colorID]
    )
    yLabelOffset += lineHeight
    for (label in detectedObject.labels) {
      val labelToDraw = if (isProhibited) "⚠️ PROHIBIDO: ${label.text}" else label.text
      canvas.drawText(
        labelToDraw,
        rect.left,
        rect.top + yLabelOffset,
        textPaints[colorID]
      )
      yLabelOffset += lineHeight
      canvas.drawText(
        String.format(
          Locale.US,
          LABEL_FORMAT,
          label.confidence * 100,
          label.index
        ),
        rect.left,
        rect.top + yLabelOffset,
        textPaints[colorID]
      )
      yLabelOffset += lineHeight
    }
  }

  companion object {
    private const val TEXT_SIZE = 54.0f
    private const val STROKE_WIDTH = 4.0f
    private const val NUM_COLORS = 10
    private val COLORS =
      arrayOf(
        intArrayOf(Color.BLACK, Color.WHITE),
        intArrayOf(Color.WHITE, Color.MAGENTA),
        intArrayOf(Color.BLACK, Color.LTGRAY),
        intArrayOf(Color.WHITE, Color.RED),
        intArrayOf(Color.WHITE, Color.BLUE),
        intArrayOf(Color.WHITE, Color.DKGRAY),
        intArrayOf(Color.BLACK, Color.CYAN),
        intArrayOf(Color.BLACK, Color.YELLOW),
        intArrayOf(Color.WHITE, Color.BLACK),
        intArrayOf(Color.BLACK, Color.GREEN)
      )
    private const val LABEL_FORMAT = "%.2f%% confidence (index: %d)"
  }
}
