package com.securepass.vision.vision

import androidx.camera.core.ImageProxy
import com.google.mlkit.common.MlKitException
import com.securepass.vision.model.FrameMetadata
import com.securepass.vision.ui.components.GraphicOverlay
import java.nio.ByteBuffer

/** Interfaz para procesar las imágenes con diferentes detectores. */
interface VisionImageProcessor {
  /** Procesa un búfer de imagen desde [ByteBuffer]. */
  @Throws(MlKitException::class)
  fun processByteBuffer(
    data: ByteBuffer,
    frameMetadata: FrameMetadata,
    graphicOverlay: GraphicOverlay
  )

  /** Procesa [ImageProxy] desde CameraX. */
  @Throws(MlKitException::class)
  fun processImageProxy(image: ImageProxy, graphicOverlay: GraphicOverlay)

  /** Detiene el detector de aprendizaje automático subyacente y libera recursos. */
  fun stop()
}
