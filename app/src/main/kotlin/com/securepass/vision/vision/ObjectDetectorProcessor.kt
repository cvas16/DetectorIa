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
  private val currentEventId: String = "0",
  private val currentEventName: String = "No Event"
) : VisionProcessorBase<List<DetectedObject>>(context) {

  private val detector: ObjectDetector = ObjectDetection.getClient(options)
  private val dbHelper = DatabaseHelper(context)
  private val lastSavedTime = mutableMapOf<String, Long>()
  
  // Ámbito de corrutinas para tareas en segundo plano
  private val processorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  override fun stop() {
    super.stop()
    processorScope.cancel() // Cancelar todas las tareas en segundo plano pendientes
    try {
      detector.close()
    } catch (e: IOException) {
      Log.e(TAG, "Excepción lanzada al intentar cerrar el detector de objetos!", e)
    }
  }

  override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
    return detector.process(image)
  }

  override fun onSuccess(results: List<DetectedObject>, graphicOverlay: GraphicOverlay) {
    for (result in results) {
      graphicOverlay.add(ObjectGraphic(graphicOverlay, result, prohibitedLabels))
      
      // Verificar etiquetas para elementos prohibidos
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
    
    // Solo guardar si han pasado más de 5 segundos para evitar duplicados
    if (currentTime - lastTime > 5000) {
      lastSavedTime[label] = currentTime
      
      val detection = DetectionEvent(
        id = java.util.UUID.randomUUID().toString(),
        objectLabel = "⚠️ $label",
        confidence = confidence,
        timestamp = currentTime,
        userId = currentUserId,
        userName = currentUserName,
        eventId = currentEventId,
        eventName = currentEventName
      )

      // Iniciar corrutina para manejar la base de datos y la sincronización en la nube
      processorScope.launch {
        // Guardar en la DB Local (Hilo de Entrada/Salida - IO)
        withContext(Dispatchers.IO) {
          dbHelper.insertDetection(detection)
          Log.d(TAG, "Alerta guardada en SQLite: $label")
        }
        
        // Sincronizar con la nube
        uploadDetectionToCloud(detection)
      }
    }
  }

  private suspend fun uploadDetectionToCloud(detection: DetectionEvent) {
    val apiService = com.securepass.vision.data.api.RetrofitClient.instance
    
    try {
      val response = withContext(Dispatchers.IO) {
        apiService.postDetection(detection)
      }
      
      if (response.isSuccessful) {
        val cloudDetection = response.body()
        if (cloudDetection != null && detection.id != null) {
          withContext(Dispatchers.IO) {
            dbHelper.updateDetectionId(detection.id, cloudDetection.id!!)
          }
        }
        Log.d(TAG, "Detección sincronizada con la nube: ${detection.objectLabel}")
      } else {
        Log.e(TAG, "Error al sincronizar detección: ${response.code()}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Fallo de red al sincronizar detección", e)
    }
  }

  override fun onFailure(e: Exception) {
    Log.e(TAG, "Fallo en la detección de objetos!", e)
  }

  companion object {
    private const val TAG = "ObjectDetectorProcessor"
  }
}
