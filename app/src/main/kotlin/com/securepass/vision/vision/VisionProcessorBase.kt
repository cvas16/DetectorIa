package com.securepass.vision.vision

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.GuardedBy
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.vision.common.InputImage
import com.securepass.vision.utils.PreferenceUtils
import com.securepass.vision.utils.BitmapUtils
import com.securepass.vision.model.FrameMetadata
import com.securepass.vision.ui.components.GraphicOverlay
import com.securepass.vision.ui.components.CameraImageGraphic
import com.securepass.vision.ui.components.InferenceInfoGraphic
import kotlin.math.max
import kotlin.math.min
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executor

/**
 * Clase base abstracta para los procesadores de visión de ML Kit.
 */
abstract class VisionProcessorBase<T>(context: Context) : VisionImageProcessor {

  private var activityManager: ActivityManager =
    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  private val fpsTimer = Timer()
  private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)

  private var isShutdown = false

  private var numRuns = 0
  private var totalFrameMs = 0L
  private var maxFrameMs = 0L
  private var minFrameMs = Long.MAX_VALUE
  private var totalDetectorMs = 0L
  private var maxDetectorMs = 0L
  private var minDetectorMs = Long.MAX_VALUE

  private var frameProcessedInOneSecondInterval = 0
  private var framesPerSecond = 0

  @GuardedBy("this") private var latestImage: ByteBuffer? = null
  @GuardedBy("this") private var latestImageMetaData: FrameMetadata? = null
  @GuardedBy("this") private var processingImage: ByteBuffer? = null
  @GuardedBy("this") private var processingMetaData: FrameMetadata? = null

  init {
    fpsTimer.schedule(
      object : TimerTask() {
        override fun run() {
          framesPerSecond = frameProcessedInOneSecondInterval
          frameProcessedInOneSecondInterval = 0
        }
      },
      0,
      1000
    )
  }

  override fun processByteBuffer(
    data: ByteBuffer,
    frameMetadata: FrameMetadata,
    graphicOverlay: GraphicOverlay
  ) {
    synchronized(this) {
      latestImage = data
      latestImageMetaData = frameMetadata
    }
    if (processingImage == null && processingMetaData == null) {
      processLatestImage(graphicOverlay)
    }
  }

  @Synchronized
  private fun processLatestImage(graphicOverlay: GraphicOverlay) {
    processingImage = latestImage
    processingMetaData = latestImageMetaData
    latestImage = null
    latestImageMetaData = null
    if (processingImage != null && processingMetaData != null && !isShutdown) {
      processImage(processingImage!!, processingMetaData!!, graphicOverlay)
    }
  }

  private fun processImage(
    data: ByteBuffer,
    frameMetadata: FrameMetadata,
    graphicOverlay: GraphicOverlay
  ) {
    val frameStartMs = SystemClock.elapsedRealtime()
    val bitmap =
      if (PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.context)) null
      else BitmapUtils.getBitmap(data, frameMetadata)

    requestDetectInImage(
      InputImage.fromByteBuffer(
        data,
        frameMetadata.width,
        frameMetadata.height,
        frameMetadata.rotation,
        InputImage.IMAGE_FORMAT_NV21
      ),
      graphicOverlay,
      bitmap,
      frameStartMs
    )
      .addOnSuccessListener(executor) { processLatestImage(graphicOverlay) }
  }

  @ExperimentalGetImage
  override fun processImageProxy(image: ImageProxy, graphicOverlay: GraphicOverlay) {
    val frameStartMs = SystemClock.elapsedRealtime()
    if (isShutdown) {
      return
    }
    var bitmap: Bitmap? = null
    if (!PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.context)) {
      bitmap = BitmapUtils.getBitmap(image)
    }

    requestDetectInImage(
      InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees),
      graphicOverlay,
      bitmap,
      frameStartMs
    )
      .addOnCompleteListener { image.close() }
  }

  private fun requestDetectInImage(
    image: InputImage,
    graphicOverlay: GraphicOverlay,
    originalCameraImage: Bitmap?,
    frameStartMs: Long
  ): Task<T> {
    return setUpListener(
      detectInImage(image),
      graphicOverlay,
      originalCameraImage,
      frameStartMs
    )
  }

  private fun setUpListener(
    task: Task<T>,
    graphicOverlay: GraphicOverlay,
    originalCameraImage: Bitmap?,
    frameStartMs: Long
  ): Task<T> {
    val detectorStartMs = SystemClock.elapsedRealtime()
    return task
      .addOnSuccessListener(executor as Executor) { results: T ->
        val endMs = SystemClock.elapsedRealtime()
        val currentFrameLatencyMs = endMs - frameStartMs
        val currentDetectorLatencyMs = endMs - detectorStartMs
        if (numRuns >= 500) {
          resetLatencyStats()
        }
        numRuns++
        frameProcessedInOneSecondInterval++
        totalFrameMs += currentFrameLatencyMs
        maxFrameMs = max(currentFrameLatencyMs, maxFrameMs)
        minFrameMs = min(currentFrameLatencyMs, minFrameMs)
        totalDetectorMs += currentDetectorLatencyMs
        maxDetectorMs = max(currentDetectorLatencyMs, maxDetectorMs)
        minDetectorMs = min(currentDetectorLatencyMs, minDetectorMs)

        if (frameProcessedInOneSecondInterval == 1) {
          val mi = ActivityManager.MemoryInfo()
          activityManager.getMemoryInfo(mi)
        }
        graphicOverlay.clear()
        if (originalCameraImage != null) {
          graphicOverlay.add(CameraImageGraphic(graphicOverlay, originalCameraImage))
        }
        onSuccess(results, graphicOverlay)
        if (!PreferenceUtils.shouldHideDetectionInfo(graphicOverlay.context)) {
          graphicOverlay.add(
            InferenceInfoGraphic(
              graphicOverlay,
              currentFrameLatencyMs,
              currentDetectorLatencyMs,
              framesPerSecond
            )
          )
        }
        graphicOverlay.postInvalidate()
      }
      .addOnFailureListener(executor as Executor) { e: Exception ->
        graphicOverlay.clear()
        graphicOverlay.postInvalidate()
        onFailure(e)
      }
  }

  override fun stop() {
    executor.shutdown()
    isShutdown = true
    resetLatencyStats()
    fpsTimer.cancel()
  }

  private fun resetLatencyStats() {
    numRuns = 0
    totalFrameMs = 0
    maxFrameMs = 0
    minFrameMs = Long.MAX_VALUE
    totalDetectorMs = 0
    maxDetectorMs = 0
    minDetectorMs = Long.MAX_VALUE
  }

  protected abstract fun detectInImage(image: InputImage): Task<T>

  protected abstract fun onSuccess(results: T, graphicOverlay: GraphicOverlay)

  protected abstract fun onFailure(e: Exception)
}