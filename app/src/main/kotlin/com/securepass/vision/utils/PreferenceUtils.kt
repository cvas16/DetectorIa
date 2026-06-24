package com.securepass.vision.utils

import android.content.Context
import android.util.Size
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.securepass.vision.R
import androidx.core.content.edit

/** Utility class to retrieve shared preferences.  */
object PreferenceUtils {

    fun saveString(context: Context, @StringRes prefKeyId: Int, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(context.getString(prefKeyId), value)
        }
    }

    fun getCameraPreviewSizePair(context: Context, cameraId: Int): SizePair? {
        val previewSizePrefKey: String
        val pictureSizePrefKey: String
        if (cameraId == 0) {
            previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size)
        } else {
            previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            val previewSizeStr = sharedPreferences.getString(previewSizePrefKey, null)
            val pictureSizeStr = sharedPreferences.getString(pictureSizePrefKey, null)
            if (previewSizeStr == null) return null
            
            SizePair(
                Size.parseSize(previewSizeStr),
                if (pictureSizeStr != null) Size.parseSize(pictureSizeStr) else null
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getCameraXTargetResolution(context: Context, lensFacing: Int): Size? {
        val prefKey = if (lensFacing == 0) {
            R.string.pref_key_camerax_rear_camera_target_resolution
        } else {
            R.string.pref_key_camerax_front_camera_target_resolution
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val sizeStr = sharedPreferences.getString(context.getString(prefKey), null)
        return try {
            if (sizeStr != null) Size.parseSize(sizeStr) else null
        } catch (_: Exception) {
            null
        }
    }

    fun getCustomObjectDetectorOptionsForLivePreview(
        context: Context,
        localModel: LocalModel
    ): CustomObjectDetectorOptions {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_live_preview_object_detector_enable_multiple_objects)
        val enableMultipleObjects = sharedPreferences.getBoolean(prefKey, false)
        val prefKeyClassification = context.getString(R.string.pref_key_live_preview_object_detector_enable_classification)
        val enableClassification = sharedPreferences.getBoolean(prefKeyClassification, true)

        val builder = CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
        if (enableMultipleObjects) {
            builder.enableMultipleObjects()
        }
        if (enableClassification) {
            builder.enableClassification().setMaxPerObjectLabelCount(1)
        }
        return builder.build()
    }

    fun isCameraLiveViewportEnabled(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_camera_live_viewport)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    fun shouldHideDetectionInfo(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_info_hide)
        return sharedPreferences.getBoolean(prefKey, false)
    }

    /** Helper class for a pair of [Size].  */
    class SizePair(val preview: Size, val picture: Size?)
}
