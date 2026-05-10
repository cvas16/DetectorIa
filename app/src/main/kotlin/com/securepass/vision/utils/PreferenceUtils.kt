package com.securepass.vision.utils

import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import com.google.android.gms.common.images.Size
import com.google.common.base.Preconditions
import com.google.mlkit.common.model.LocalModel
import com.securepass.vision.vision.CameraSource
import com.securepass.vision.vision.CameraSource.SizePair
import com.securepass.vision.R
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase.DetectorMode
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/** Conjunto de métodos de utilidad para trabajar con SharedPreferences. */
object PreferenceUtils {

    fun saveString(context: Context, @StringRes prefKeyId: Int, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(prefKeyId), value)
            .apply()
    }

    @JvmStatic
    fun getCameraPreviewSizePair(context: Context, cameraId: Int): SizePair? {
        Preconditions.checkArgument(
            cameraId == CameraSource.CAMERA_FACING_BACK || cameraId == CameraSource.CAMERA_FACING_FRONT
        )
        val previewSizePrefKey: String
        val pictureSizePrefKey: String
        if (cameraId == CameraSource.CAMERA_FACING_BACK) {
            previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size)
        } else {
            previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size)
        }

        return try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            SizePair(
                Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
                Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null))
            )
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @JvmStatic
    fun getCameraXTargetResolution(context: Context, lensfacing: Int): android.util.Size? {
        Preconditions.checkArgument(
            lensfacing == CameraSelector.LENS_FACING_BACK || lensfacing == CameraSelector.LENS_FACING_FRONT
        )
        val prefKey = if (lensfacing == CameraSelector.LENS_FACING_BACK) {
            context.getString(R.string.pref_key_camerax_rear_camera_target_resolution)
        } else {
            context.getString(R.string.pref_key_camerax_front_camera_target_resolution)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            android.util.Size.parseSize(sharedPreferences.getString(prefKey, null))
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun shouldHideDetectionInfo(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_info_hide)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    @JvmStatic
    fun getObjectDetectorOptionsForLivePreview(context: Context): ObjectDetectorOptions {
        return getObjectDetectorOptions(
            context,
            R.string.pref_key_live_preview_object_detector_enable_multiple_objects,
            R.string.pref_key_live_preview_object_detector_enable_classification,
            ObjectDetectorOptions.STREAM_MODE
        )
    }

    private fun getObjectDetectorOptions(
        context: Context,
        @StringRes prefKeyForMultipleObjects: Int,
        @StringRes prefKeyForClassification: Int,
        @DetectorMode mode: Int
    ): ObjectDetectorOptions {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enableMultipleObjects = sharedPreferences.getBoolean(context.getString(prefKeyForMultipleObjects), false)
        val enableClassification = sharedPreferences.getBoolean(context.getString(prefKeyForClassification), true)

        val builder = ObjectDetectorOptions.Builder().setDetectorMode(mode)
        if (enableMultipleObjects) {
            builder.enableMultipleObjects()
        }
        if (enableClassification) {
            builder.enableClassification()
        }
        return builder.build()
    }

    @JvmStatic
    fun getCustomObjectDetectorOptionsForLivePreview(
        context: Context,
        localModel: LocalModel
    ): CustomObjectDetectorOptions {
        return getCustomObjectDetectorOptions(
            context,
            localModel,
            R.string.pref_key_live_preview_object_detector_enable_multiple_objects,
            R.string.pref_key_live_preview_object_detector_enable_classification,
            CustomObjectDetectorOptions.STREAM_MODE
        )
    }

    private fun getCustomObjectDetectorOptions(
        context: Context,
        localModel: LocalModel,
        @StringRes prefKeyForMultipleObjects: Int,
        @StringRes prefKeyForClassification: Int,
        @DetectorMode mode: Int
    ): CustomObjectDetectorOptions {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enableMultipleObjects = sharedPreferences.getBoolean(context.getString(prefKeyForMultipleObjects), false)
        val enableClassification = sharedPreferences.getBoolean(context.getString(prefKeyForClassification), true)

        val builder = CustomObjectDetectorOptions.Builder(localModel).setDetectorMode(mode)
        if (enableMultipleObjects) {
            builder.enableMultipleObjects()
        }
        if (enableClassification) {
            builder.enableClassification().setMaxPerObjectLabelCount(3)
        }
        return builder.build()
    }

    @JvmStatic
    fun isCameraLiveViewportEnabled(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_camera_live_viewport)
        return sharedPreferences.getBoolean(prefKey, false)
    }
}
