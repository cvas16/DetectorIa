package com.securepass.vision.ui.fragments

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import com.securepass.vision.R
import com.securepass.vision.utils.PreferenceUtils
import android.util.Log

/** Configura los ajustes para la actividad de vista previa en vivo de CameraX. */
class CameraXLivePreviewPreferenceFragment : LivePreviewPreferenceFragment() {

    override fun setUpCameraPreferences() {
        val cameraPreference = findPreference<PreferenceCategory>(getString(R.string.pref_category_key_camera))

        findPreference<ListPreference>(getString(R.string.pref_key_rear_camera_preview_size))?.let {
            cameraPreference?.removePreference(it)
        }
        findPreference<ListPreference>(getString(R.string.pref_key_front_camera_preview_size))?.let {
            cameraPreference?.removePreference(it)
        }

        setUpCameraXTargetAnalysisSizePreference(
            R.string.pref_key_camerax_rear_camera_target_resolution,
            CameraSelector.LENS_FACING_BACK
        )
        setUpCameraXTargetAnalysisSizePreference(
            R.string.pref_key_camerax_front_camera_target_resolution,
            CameraSelector.LENS_FACING_FRONT
        )
    }

    private fun setUpCameraXTargetAnalysisSizePreference(
        @StringRes previewSizePrefKeyId: Int,
        lensFacing: Int
    ) {
        val pref = findPreference<ListPreference>(getString(previewSizePrefKeyId))
            ?: return


        val cameraCharacteristics = getCameraCharacteristics(requireContext(), lensFacing)
        val entries: Array<String> = if (cameraCharacteristics != null) {
            val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            if (outputSizes != null) {
                Array(outputSizes.size) { i -> outputSizes[i].toString() }
            } else {
                emptyArray()
            }
        } else {
            arrayOf(
                "2000x2000",
                "1600x1600",
                "1200x1200",
                "1000x1000",
                "800x800",
                "600x600",
                "400x400",
                "200x200",
                "100x100"
            )
        }
        pref.entries = entries
        pref.entryValues = entries
        pref.summary = pref.entry ?: "Default"
        pref.setOnPreferenceChangeListener { _, newValue ->
            val newStringValue = newValue as String
            pref.summary = newStringValue
            PreferenceUtils.saveString(requireContext(), previewSizePrefKeyId, newStringValue)
            true
        }
    }

    companion object {
        private const val TAG = "CameraXLivePreviewPref"

        fun getCameraCharacteristics(context: Context, lensFacing: Int?): CameraCharacteristics? {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                val cameraList = cameraManager.cameraIdList
                for (availableCameraId in cameraList) {
                    val availableCameraCharacteristics = cameraManager.getCameraCharacteristics(availableCameraId)
                    val availableLensFacing = availableCameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                        ?: continue
                    if (availableLensFacing == lensFacing) {
                        return availableCameraCharacteristics
                    }
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Error al acceder a la información del ID de la cámara", e)
            }
            return null
        }
    }
}
