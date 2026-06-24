package com.securepass.vision.ui.components

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.securepass.vision.utils.PreferenceUtils
import com.securepass.vision.vision.CameraSource
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class CameraSourcePreview(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {
    private val surfaceView: SurfaceView = SurfaceView(context)
    private var startRequested = false
    private var surfaceAvailable = false
    private var cameraSource: CameraSource? = null
    private var overlay: GraphicOverlay? = null

    init {
        surfaceView.holder.addCallback(SurfaceCallback())
        addView(surfaceView)
    }

    @Throws(IOException::class)
    private fun start(cameraSource: CameraSource?) {
        this.cameraSource = cameraSource
        if (this.cameraSource != null) {
            startRequested = true
            startIfReady()
        }
    }

    @Throws(IOException::class)
    fun start(cameraSource: CameraSource?, overlay: GraphicOverlay?) {
        this.overlay = overlay
        start(cameraSource)
    }

    fun stop() {
        cameraSource?.stop()
    }


    @Throws(IOException::class, SecurityException::class)
    private fun startIfReady() {
        if (startRequested && surfaceAvailable) {
            if (PreferenceUtils.isCameraLiveViewportEnabled(context)) {
                cameraSource?.start(surfaceView.holder)
            } else {
                cameraSource?.start()
            }
            requestLayout()

            overlay?.let { overlay ->
                cameraSource?.previewSize?.let { size ->
                    val minSize = min(size.width, size.height)
                    val maxSize = max(size.width, size.height)
                    val isImageFlipped = cameraSource?.cameraFacing == CameraSource.CAMERA_FACING_FRONT
                    if (isPortraitMode) {
                        // Swap width and height sizes when in portrait, since it will be rotated by 90 degrees.
                        // The camera preview and the image being processed have the same size.
                        overlay.setImageSourceInfo(minSize, maxSize, isImageFlipped)
                    } else {
                        overlay.setImageSourceInfo(maxSize, minSize, isImageFlipped)
                    }
                    overlay.clear()
                }
            }
            startRequested = false
        }
    }

    private inner class SurfaceCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(surface: SurfaceHolder) {
            surfaceAvailable = true
            try {
                startIfReady()
            } catch (e: IOException) {
                Log.e(TAG, "Could not start camera source.", e)
            }
        }

        override fun surfaceDestroyed(surface: SurfaceHolder) {
            surfaceAvailable = false
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var width = 320
        var height = 240
        cameraSource?.previewSize?.let { size ->
            width = size.width
            height = size.height
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode) {
            val tmp = width
            width = height
            height = tmp
        }

        val previewAspectRatio = width.toFloat() / height
        val layoutWidth = right - left
        val layoutHeight = bottom - top
        val layoutAspectRatio = layoutWidth.toFloat() / layoutHeight
        if (previewAspectRatio > layoutAspectRatio) {
            // The preview input is wider than the layout area. Fit the layout height and crop
            // the preview input horizontally while keep the center.
            val horizontalOffset = (previewAspectRatio * layoutHeight - layoutWidth).toInt() / 2
            surfaceView.layout(-horizontalOffset, 0, layoutWidth + horizontalOffset, layoutHeight)
        } else {
            // The preview input is taller than the layout area. Fit the layout width and crop the preview
            // input vertically while keep the center.
            val verticalOffset = (layoutWidth / previewAspectRatio - layoutHeight).toInt() / 2
            surfaceView.layout(0, -verticalOffset, layoutWidth, layoutHeight + verticalOffset)
        }
    }

    private val isPortraitMode: Boolean
        get() {
            val orientation = context.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return false
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                return true
            }
            Log.d(TAG, "isPortraitMode returning false by default")
            return false
        }

    companion object {
        private const val TAG = "MIDemoApp:Preview"
    }
}
