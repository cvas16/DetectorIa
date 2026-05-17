package com.securepass.vision.vision

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.securepass.vision.ui.components.GraphicOverlay
import com.securepass.vision.ui.components.ObjectGraphic
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.model.DetectionEvent
import java.io.IOException

class ObjectDetectorProcessor(
  context: Context,
  options: ObjectDetectorOptionsBase,
  private val prohibitedLabels: List<String> = emptyList()
) : VisionProcessorBase<List<DetectedObject>>(context) {

  private val detector: ObjectDetector = ObjectDetection.getClient(options)
  private val dbHelper = DatabaseHelper(context)
  private val lastSavedTime = mutableMapOf<String, Long>()

  override fun stop() {
    super.stop()
    try {
      detector.close()
    } catch (e: IOException) {
      Log.e(TAG, "Exception thrown while trying to close object detector!", e)
    }
  }

  override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
    return detector.process(image)
  }

  override fun onSuccess(results: List<DetectedObject>, graphicOverlay: GraphicOverlay) {
    for (result in results) {
      graphicOverlay.add(ObjectGraphic(graphicOverlay, result, prohibitedLabels))
      
      // Lógica para guardar en el historial
      for (label in result.labels) {
        if (prohibitedLabels.any { it.trim().equals(label.text, ignoreCase = true) }) {
          saveDetectionIfNew(label.text, label.confidence)
        }
      }
    }
  }

  private fun saveDetectionIfNew(label: String, confidence: Float) {
    val currentTime = System.currentTimeMillis()
    val lastTime = lastSavedTime[label] ?: 0L
    
    // Solo guarda si han pasado más de 5 segundos desde la última vez para este objeto
    if (currentTime - lastTime > 5000) {
      dbHelper.insertDetection(
        DetectionEvent(
          objectLabel = "⚠️ $label",
          confidence = confidence,
          timestamp = currentTime
        )
      )
      lastSavedTime[label] = currentTime
      Log.d(TAG, "Alerta guardada en SQLite: $label")
    }
  }

  override fun onFailure(e: Exception) {
    Log.e(TAG, "Object detection failed!", e)
  }

  companion object {
    private const val TAG = "ObjectDetectorProcessor"
  }
}
