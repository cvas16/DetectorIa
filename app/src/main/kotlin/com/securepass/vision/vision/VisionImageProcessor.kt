package com.securepass.vision.vision

import androidx.camera.core.ImageProxy
import com.google.mlkit.common.MlKitException
import com.securepass.vision.model.FrameMetadata
import com.securepass.vision.ui.components.GraphicOverlay
import java.nio.ByteBuffer

/** An interface to process the images with different detectors. */
interface VisionImageProcessor {
  /** Processes a protein from [ByteBuffer]. */
  @Throws(MlKitException::class)
  fun processByteBuffer(
    data: ByteBuffer,
    frameMetadata: FrameMetadata,
    graphicOverlay: GraphicOverlay
  )

  /** Processes [ImageProxy] from CameraX. */
  @Throws(MlKitException::class)
  fun processImageProxy(image: ImageProxy, graphicOverlay: GraphicOverlay)

  /** Stops the underlying machine learning detector and release resources. */
  fun stop()
}
