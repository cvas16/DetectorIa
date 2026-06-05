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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ObjectDetectorProcessor(
  context: Context,
  options: ObjectDetectorOptionsBase,
  private val prohibitedLabels: List<String> = emptyList(),
  private val currentUserId: String = "0",
  private val currentUserName: String = "Unknown",
  private val currentEventId: Long = -1L,
  private val currentEventName: String = "No Event"
) : VisionProcessorBase<List<DetectedObject>>(context) {

  private val detector: ObjectDetector = ObjectDetection.getClient(options)
  private val dbHelper = DatabaseHelper(context)
  private val lastSavedTime = mutableMapOf<String, Long>()
  
  // Coroutine scope for background tasks
  private val processorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  override fun stop() {
    super.stop()
    processorScope.cancel() // Cancel all pending background tasks
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
      
      // Check labels for prohibited items
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
    
    if (currentTime - lastTime > 5000) {
      lastSavedTime[label] = currentTime
      
      val detection = DetectionEvent(
        objectLabel = "⚠️ $label",
        confidence = confidence,
        timestamp = currentTime,
        userId = currentUserId,
        userName = currentUserName,
        eventId = currentEventId,
        eventName = currentEventName
      )

      // Launch coroutine to handle DB and Cloud sync
      processorScope.launch {
        // Save to Local DB (IO Thread)
        withContext(Dispatchers.IO) {
          dbHelper.insertDetection(detection)
          Log.d(TAG, "Alerta guardada en SQLite: $label")
        }
        
        // Sync to Cloud (IO Thread)
        uploadDetectionToCloud(detection)
      }
    }
  }

  private suspend fun uploadDetectionToCloud(detection: DetectionEvent) {
    val apiService = com.securepass.vision.data.api.RetrofitClient.instance
    
    try {
      // Retrofit suspend functions handle switching to IO internally if using proper call adapter,
      // but wrapping in withContext(Dispatchers.IO) ensures safety.
      val response = withContext(Dispatchers.IO) {
        apiService.postDetection(detection)
      }
      
      if (response.isSuccessful) {
        Log.d(TAG, "Detección sincronizada con la nube: ${detection.objectLabel}")
      } else {
        Log.e(TAG, "Error al sincronizar detección: ${response.code()}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Fallo de red al sincronizar detección", e)
    }
  }

  override fun onFailure(e: Exception) {
    Log.e(TAG, "Object detection failed!", e)
  }

  companion object {
    private const val TAG = "ObjectDetectorProcessor"
  }
}
