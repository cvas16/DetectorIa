package com.securepass.vision.ui.activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.securepass.vision.R
import com.securepass.vision.ui.fragments.LivePreviewPreferenceFragment
import com.securepass.vision.ui.fragments.CameraXLivePreviewPreferenceFragment

/**
 * Aloja el fragmento de preferencias para configurar los ajustes de una actividad de demostración 
 * especificada por el [LaunchSource].
 */
class SettingsActivity : AppCompatActivity() {

    /** Especifica desde dónde se lanza esta actividad. */
    enum class LaunchSource(
        val titleResId: Int,
        val prefFragmentClass: Class<out PreferenceFragmentCompat>
    ) {
        LIVE_PREVIEW(R.string.pref_screen_title_live_preview, LivePreviewPreferenceFragment::class.java),
        CAMERAX_LIVE_PREVIEW(
            R.string.pref_screen_title_camerax_live_preview,
            CameraXLivePreviewPreferenceFragment::class.java
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val launchSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_LAUNCH_SOURCE, LaunchSource::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_LAUNCH_SOURCE) as? LaunchSource
        } ?: LaunchSource.CAMERAX_LIVE_PREVIEW

        supportActionBar?.setTitle(launchSource.titleResId)

        if (savedInstanceState == null) {
            try {
                // Cargar el fragmento de configuración dinámicamente según el origen del lanzamiento
                supportFragmentManager
                    .beginTransaction()
                    .replace(
                        R.id.settings_container,
                        launchSource.prefFragmentClass.getDeclaredConstructor().newInstance()
                    )
                    .commit()
            } catch (e: Exception) {
                throw RuntimeException("Error al instanciar el fragmento de configuración", e)
            }
        }
    }

    companion object {
        const val EXTRA_LAUNCH_SOURCE = "extra_launch_source"
    }
}
