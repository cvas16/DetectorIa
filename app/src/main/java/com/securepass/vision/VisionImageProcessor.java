
package com.securepass.vision;

import android.graphics.Bitmap;
import android.os.Build.VERSION_CODES;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.common.MlKitException;

import java.nio.ByteBuffer;

/** Interfaz para procesar imágenes con diferentes detectores de visión y modelos personalizados. */
public interface VisionImageProcessor {

  /** Procesa una imagen en formato Bitmap. */
  void processBitmap(Bitmap bitmap, GraphicOverlay graphicOverlay);

  /** Procesa datos de imagen en ByteBuffer, por ejemplo, para el caso de vista previa de Camera1. */
  void processByteBuffer(
          ByteBuffer data, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay)
      throws MlKitException;

  /** Procesa datos de imagen en ImageProxy, por ejemplo, para el caso de vista previa de CameraX. */
  @RequiresApi(VERSION_CODES.KITKAT)
  void processImageProxy(ImageProxy image, GraphicOverlay graphicOverlay) throws MlKitException;

  /** Detiene el modelo de aprendizaje automático subyacente y libera los recursos. */
  void stop();
}
