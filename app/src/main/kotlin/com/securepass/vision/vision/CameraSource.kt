@file:Suppress("DEPRECATION", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.securepass.vision.vision

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import com.google.android.gms.common.images.Size
import com.securepass.vision.ui.components.GraphicOverlay
import com.securepass.vision.model.FrameMetadata
import com.securepass.vision.utils.PreferenceUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil

@Suppress("DEPRECATION")
class CameraSource(private var activity: Activity, private val graphicOverlay: GraphicOverlay) {
    private var camera: Camera? = null
    var cameraFacing = CAMERA_FACING_BACK
        private set
    private var rotationDegrees = 0
    var previewSize: Size? = null
        private set
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var processingThread: Thread? = null
    private val processingRunnable: FrameProcessingRunnable = FrameProcessingRunnable()
    private val processorLock = Any()
    private var frameProcessor: VisionImageProcessor? = null
    private val bytesToByteBuffer = IdentityHashMap<ByteArray, ByteBuffer>()

    init {
        graphicOverlay.clear()
    }

    fun release() {
        synchronized(processorLock) {
            stop()
            cleanScreen()
            frameProcessor?.stop()
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class)
    @Synchronized
    fun start(): CameraSource {
        if (camera != null) return this
        camera = createCamera()
        dummySurfaceTexture = SurfaceTexture(DUMMY_TEXTURE_NAME)
        camera?.setPreviewTexture(dummySurfaceTexture)
        camera?.startPreview()
        processingThread = Thread(processingRunnable)
        processingRunnable.setActive(true)
        processingThread?.start()
        return this
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Throws(IOException::class)
    @Synchronized
    fun start(surfaceHolder: SurfaceHolder): CameraSource {
        if (camera != null) return this
        camera = createCamera()
        camera?.setPreviewDisplay(surfaceHolder)
        camera?.startPreview()
        processingThread = Thread(processingRunnable)
        processingRunnable.setActive(true)
        processingThread?.start()
        return this
    }

    @Synchronized
    fun stop() {
        processingRunnable.setActive(false)
        try {
            processingThread?.join()
        } catch (_: InterruptedException) {
            Log.d(TAG, "Frame processing thread interrupted on release.")
        }
        processingThread = null
        camera?.let {
            it.stopPreview()
            it.setPreviewCallbackWithBuffer(null)
            try {
                it.setPreviewTexture(null)
                dummySurfaceTexture = null
                it.setPreviewDisplay(null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear camera preview: $e")
            }
            it.release()
            camera = null
        }
        bytesToByteBuffer.clear()
    }

    @Synchronized
    fun setFacing(facing: Int) {
        require(!(facing != CAMERA_FACING_BACK && facing != CAMERA_FACING_FRONT)) { "Invalid camera: $facing" }
        cameraFacing = facing
    }

    @SuppressLint("InlinedApi")
    @Throws(IOException::class)
    private fun createCamera(): Camera {
        val requestedCameraId = getIdForRequestedCamera(cameraFacing)
        if (requestedCameraId == -1) {
            throw IOException("Could not find requested camera.")
        }
        val camera = Camera.open(requestedCameraId)
        val prefSizePair = PreferenceUtils.getCameraPreviewSizePair(activity, requestedCameraId)
        val sizePair = if (prefSizePair != null) {
            SizePair(
                Size(prefSizePair.preview.width, prefSizePair.preview.height),
                prefSizePair.picture?.let { Size(it.width, it.height) }
            )
        } else {
            selectSizePair(
                camera,
                DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH,
                DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT
            )
        }

        if (sizePair == null) {
            throw IOException("Could not find suitable preview size.")
        }
        previewSize = sizePair.preview
        Log.v(TAG, "Camera preview size: $previewSize")
        val previewFpsRange = selectPreviewFpsRange(camera)
            ?: throw IOException("Could not find suitable preview frames per second range.")
        val parameters = camera.parameters
        val pictureSize = sizePair.picture
        if (pictureSize != null) {
            Log.v(TAG, "Camera picture size: $pictureSize")
            parameters.setPictureSize(pictureSize.width, pictureSize.height)
        }
        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)
        parameters.setPreviewFpsRange(
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
            previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
        )
        parameters.previewFormat = IMAGE_FORMAT
        setRotation(camera, parameters, requestedCameraId)
        if (REQUESTED_AUTO_FOCUS) {
            if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            } else {
                Log.i(TAG, "Camera auto focus is not supported on this device.")
            }
        }
        camera.parameters = parameters
        camera.setPreviewCallbackWithBuffer(CameraPreviewCallback())
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        return camera
    }

    private fun setRotation(camera: Camera, parameters: Camera.Parameters, cameraId: Int) {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
            else -> Log.e(TAG, "Bad rotation value: $rotation")
        }
        val cameraInfo = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, cameraInfo)
        val displayAngle: Int
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotationDegrees = (cameraInfo.orientation + degrees) % 360
            displayAngle = (360 - rotationDegrees) % 360 // compensate for it being mirrored
        } else { // back-facing
            rotationDegrees = (cameraInfo.orientation - degrees + 360) % 360
            displayAngle = rotationDegrees
        }
        Log.d(TAG, "Display rotation is: $rotation")
        Log.d(TAG, "Camera face is: ${cameraInfo.facing}")
        Log.d(TAG, "Camera rotation is: ${cameraInfo.orientation}")
        Log.d(TAG, "RotationDegrees is: $rotationDegrees")
        camera.setDisplayOrientation(displayAngle)
        parameters.setRotation(rotationDegrees)
    }

    @SuppressLint("InlinedApi")
    private fun createPreviewBuffer(previewSize: Size): ByteArray {
        val bitsPerPixel = ImageFormat.getBitsPerPixel(IMAGE_FORMAT)
        val sizeInBits = previewSize.height.toLong() * previewSize.width * bitsPerPixel
        val bufferSize = ceil(sizeInBits / 8.0).toInt() + 1
        val byteArray = ByteArray(bufferSize)
        val buffer = ByteBuffer.wrap(byteArray)
        check(!(!buffer.hasArray() || buffer.array() !== byteArray)) { "Failed to create valid buffer for camera source." }
        bytesToByteBuffer[byteArray] = buffer
        return byteArray
    }

    private inner class CameraPreviewCallback : Camera.PreviewCallback {
        @Deprecated("Deprecated in Java")
        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            processingRunnable.setNextFrame(data, camera)
        }
    }

    fun setMachineLearningFrameProcessor(processor: VisionImageProcessor?) {
        synchronized(processorLock) {
            cleanScreen()
            frameProcessor?.stop()
            frameProcessor = processor
        }
    }

    private inner class FrameProcessingRunnable : Runnable {
        private val lock = Any()
        private var active = true
        private var pendingFrameData: ByteBuffer? = null

        fun setActive(active: Boolean) {
            synchronized(lock) {
                this.active = active
                (lock as java.lang.Object).notifyAll()
            }
        }

        fun setNextFrame(data: ByteArray, camera: Camera) {
            synchronized(lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData!!.array())
                    pendingFrameData = null
                }
                if (!bytesToByteBuffer.containsKey(data)) {
                    Log.d(TAG, "Skipping frame. Could not find ByteBuffer associated with the image data from the camera.")
                    return
                }
                pendingFrameData = bytesToByteBuffer[data]
                (lock as java.lang.Object).notifyAll()
            }
        }

        @SuppressLint("InlinedApi")
        override fun run() {
            var data: ByteBuffer?
            while (true) {
                synchronized(lock) {
                    while (active && pendingFrameData == null) {
                        try {
                            (lock as java.lang.Object).wait()
                        } catch (e: InterruptedException) {
                            Log.d(TAG, "Frame processing loop terminated.", e)
                            return
                        }
                    }
                    if (!active) return
                    data = pendingFrameData
                    pendingFrameData = null
                }
                try {
                    synchronized(processorLock) {
                        frameProcessor?.processByteBuffer(
                            data!!,
                            FrameMetadata.Builder()
                                .setWidth(previewSize!!.width)
                                .setHeight(previewSize!!.height)
                                .setRotation(rotationDegrees)
                                .build(),
                            graphicOverlay
                        )
                    }
                } catch (t: Exception) {
                    Log.e(TAG, "Exception thrown from receiver.", t)
                } finally {
                    camera?.addCallbackBuffer(data!!.array())
                }
            }
        }
    }

    private fun cleanScreen() {
        graphicOverlay.clear()
    }

    class SizePair {
        val preview: Size
        val picture: Size?

        internal constructor(previewSize: Camera.Size, pictureSize: Camera.Size?) {
            preview = Size(previewSize.width, previewSize.height)
            picture = if (pictureSize != null) Size(pictureSize.width, pictureSize.height) else null
        }

        constructor(previewSize: Size, pictureSize: Size?) {
            preview = previewSize
            picture = pictureSize
        }
    }

    companion object {
        @SuppressLint("InlinedApi")
        val CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK

        @SuppressLint("InlinedApi")
        val CAMERA_FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT
        const val IMAGE_FORMAT = ImageFormat.NV21
        const val DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH = 480
        const val DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT = 360
        private const val TAG = "MIDemoApp:CameraSource"
        private const val DUMMY_TEXTURE_NAME = 100
        private const val ASPECT_RATIO_TOLERANCE = 0.01f
        private const val REQUESTED_FPS = 30.0f
        private const val REQUESTED_AUTO_FOCUS = true

        private fun getIdForRequestedCamera(facing: Int): Int {
            val cameraInfo = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == facing) return i
            }
            return -1
        }

        fun selectSizePair(camera: Camera, desiredWidth: Int, desiredHeight: Int): SizePair? {
            val validPreviewSizes = generateValidPreviewSizeList(camera)
            var selectedPair: SizePair? = null
            var minDiff = Int.MAX_VALUE
            for (sizePair in validPreviewSizes) {
                val size = sizePair.preview
                val diff = abs(size.width - desiredWidth) + abs(size.height - desiredHeight)
                if (diff < minDiff) {
                    selectedPair = sizePair
                    minDiff = diff
                }
            }
            return selectedPair
        }

        fun generateValidPreviewSizeList(camera: Camera): List<SizePair> {
            val parameters = camera.parameters
            val supportedPreviewSizes = parameters.supportedPreviewSizes
            val supportedPictureSizes = parameters.supportedPictureSizes
            val validPreviewSizes: MutableList<SizePair> = ArrayList()
            for (previewSize in supportedPreviewSizes) {
                val previewAspectRatio = previewSize.width.toFloat() / previewSize.height
                for (pictureSize in supportedPictureSizes) {
                    val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height
                    if (abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                        validPreviewSizes.add(SizePair(previewSize, pictureSize))
                        break
                    }
                }
            }
            if (validPreviewSizes.isEmpty()) {
                Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size")
                for (previewSize in supportedPreviewSizes) {
                    validPreviewSizes.add(SizePair(previewSize, null))
                }
            }
            return validPreviewSizes
        }

        @SuppressLint("InlinedApi")
        private fun selectPreviewFpsRange(camera: Camera): IntArray? {
            val desiredPreviewFpsScaled = (REQUESTED_FPS * 1000.0f).toInt()
            var selectedFpsRange: IntArray? = null
            var minUpperBoundDiff = Int.MAX_VALUE
            var minLowerBound = Int.MAX_VALUE
            val previewFpsRangeList = camera.parameters.supportedPreviewFpsRange
            for (range in previewFpsRangeList) {
                val upperBoundDiff = abs(desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX])
                val lowerBound = range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                if (upperBoundDiff <= minUpperBoundDiff && lowerBound <= minLowerBound) {
                    selectedFpsRange = range
                    minUpperBoundDiff = upperBoundDiff
                    minLowerBound = lowerBound
                }
            }
            return selectedFpsRange
        }
    }
}
