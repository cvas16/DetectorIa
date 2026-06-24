@file:Suppress("DEPRECATION")
package com.securepass.vision.ui.fragments

import android.hardware.Camera
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.securepass.vision.vision.CameraSource
import com.securepass.vision.R
import com.securepass.vision.utils.PreferenceUtils

/** Configura los ajustes para la actividad de vista previa en vivo. */
open class LivePreviewPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_live_preview_quickstart, rootKey)
        setUpCameraPreferences()
    }

    open fun setUpCameraPreferences() {
        val cameraPreference = findPreference<PreferenceCategory>(getString(R.string.pref_category_key_camera))
        
        findPreference<Preference>(getString(R.string.pref_key_camerax_rear_camera_target_resolution))?.let {
            cameraPreference?.removePreference(it)
        }
        findPreference<Preference>(getString(R.string.pref_key_camerax_front_camera_target_resolution))?.let {
            cameraPreference?.removePreference(it)
        }

        setUpCameraPreviewSizePreference(
            R.string.pref_key_rear_camera_preview_size,
            R.string.pref_key_rear_camera_picture_size,
            CameraSource.CAMERA_FACING_BACK
        )
        setUpCameraPreviewSizePreference(
            R.string.pref_key_front_camera_preview_size,
            R.string.pref_key_front_camera_picture_size,
            CameraSource.CAMERA_FACING_FRONT
        )
    }

    private fun setUpCameraPreviewSizePreference(
        @StringRes previewSizePrefKeyId: Int,
        @StringRes pictureSizePrefKeyId: Int,
        cameraId: Int
    ) {
        val previewSizePreference = findPreference<ListPreference>(getString(previewSizePrefKeyId)) ?: return

        var camera: Camera? = null
        try {
            camera = Camera.open(cameraId)
            val previewSizeList = CameraSource.generateValidPreviewSizeList(camera)
            val previewSizeStringValues = Array(previewSizeList.size) { i -> previewSizeList[i].preview.toString() }
            val previewToPictureSizeStringMap = mutableMapOf<String, String>()

            for (sizePair in previewSizeList) {
                if (sizePair.picture != null) {
                    previewToPictureSizeStringMap[sizePair.preview.toString()] = sizePair.picture.toString()
                }
            }

            previewSizePreference.entries = previewSizeStringValues
            previewSizePreference.entryValues = previewSizeStringValues

            if (previewSizePreference.entry == null) {
                // Primera vez que se abre la página de ajustes.
                val sizePair = CameraSource.selectSizePair(
                    camera,
                    CameraSource.DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH,
                    CameraSource.DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT
                )
                val previewSizeString = sizePair?.preview.toString()
                previewSizePreference.value = previewSizeString
                previewSizePreference.summary = previewSizeString
                PreferenceUtils.saveString(
                    requireContext(),
                    pictureSizePrefKeyId,
                    sizePair?.picture?.toString()
                )
            } else {
                previewSizePreference.summary = previewSizePreference.entry
            }

            previewSizePreference.setOnPreferenceChangeListener { _, newValue ->
                val newPreviewSizeStringValue = newValue as String
                previewSizePreference.summary = newPreviewSizeStringValue
                PreferenceUtils.saveString(
                    requireContext(),
                    pictureSizePrefKeyId,
                    previewToPictureSizeStringMap[newPreviewSizeStringValue]
                )
                true
            }
        } catch (_: RuntimeException) {
            // Si no hay cámara para el ID dado, ocultar la preferencia correspondiente.
            val category = findPreference<PreferenceCategory>(getString(R.string.pref_category_key_camera))
            category?.removePreference(previewSizePreference)
        } finally {
            camera?.release()
        }
    }
}
