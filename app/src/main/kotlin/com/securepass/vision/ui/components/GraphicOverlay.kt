package com.securepass.vision.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import java.util.ArrayList

/**
 * Una vista que renderiza una serie de gráficos personalizados para superponerlos sobre una vista previa
 * asociada (por ejemplo, la vista previa de la cámara).
 */
class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    val transformationMatrix = Matrix()

    var imageWidth = 0
        private set
    var imageHeight = 0
        private set
    
    var scaleFactor = 1.0f
        private set
    
    var postScaleWidthOffset = 0f
        private set
    
    var postScaleHeightOffset = 0f
        private set
    
    var isImageFlipped = false
        private set
        
    private var needUpdateTransformation = true

    /**
     * Clase base para un objeto gráfico personalizado que se renderizará dentro de la superposición.
     */
    abstract class Graphic(private val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        /** Ajusta el valor proporcionado de la escala de la imagen a la escala de la vista. */
        fun scale(imagePixel: Float): Float = imagePixel * overlay.scaleFactor

        /** Ajusta la coordenada x del sistema de coordenadas de la imagen al sistema de la vista. */
        fun translateX(x: Float): Float {
            return if (overlay.isImageFlipped) {
                overlay.width - (scale(x) - overlay.postScaleWidthOffset)
            } else {
                scale(x) - overlay.postScaleWidthOffset
            }
        }

        /** Ajusta la coordenada y del sistema de coordenadas de la imagen al sistema de la vista. */
        fun translateY(y: Float): Float = scale(y) - overlay.postScaleHeightOffset

        /** Matriz para transformar de coordenadas de imagen a coordenadas de vista. */
        val transformationMatrix: Matrix
            get() = overlay.transformationMatrix

        fun postInvalidate() {
            overlay.postInvalidate()
        }
    }

    init {
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            needUpdateTransformation = true
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }


    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean) {
        check(imageWidth > 0) { "image width must be positive" }
        check(imageHeight > 0) { "image height must be positive" }
        synchronized(lock) {
            this.imageWidth = imageWidth
            this.imageHeight = imageHeight
            this.isImageFlipped = isFlipped
            needUpdateTransformation = true
        }
        postInvalidate()
    }

    private fun updateTransformationIfNeeded() {
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) return
        
        val viewAspectRatio = width.toFloat() / height
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        postScaleWidthOffset = 0f
        postScaleHeightOffset = 0f
        
        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = width.toFloat() / imageWidth
            postScaleHeightOffset = (width.toFloat() / imageAspectRatio - height) / 2
        } else {
            scaleFactor = height.toFloat() / imageHeight
            postScaleWidthOffset = (height.toFloat() * imageAspectRatio - width) / 2
        }

        transformationMatrix.apply {
            reset()
            setScale(scaleFactor, scaleFactor)
            postTranslate(-postScaleWidthOffset, -postScaleHeightOffset)
            if (isImageFlipped) {
                postScale(-1f, 1f, width / 2f, height / 2f)
            }
        }

        needUpdateTransformation = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(lock) {
            updateTransformationIfNeeded()
            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }
}
