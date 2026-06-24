package com.securepass.vision.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import com.securepass.vision.ui.components.GraphicOverlay.Graphic

/** Dibuja la imagen de la cámara en el fondo. */
class CameraImageGraphic(overlay: GraphicOverlay, private val bitmap: Bitmap) : Graphic(overlay) {

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, transformationMatrix, null)
    }
}
