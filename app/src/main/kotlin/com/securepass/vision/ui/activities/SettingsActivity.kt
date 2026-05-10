package com.securepass.vision.ui.activities

import android.os.Bundle
import android.preference.PreferenceFragment
import androidx.appcompat.app.AppCompatActivity
import com.securepass.vision.R
import com.securepass.vision.ui.fragments.LivePreviewPreferenceFragment
import com.securepass.vision.ui.fragments.CameraXLivePreviewPreferenceFragment

/**
 * Hosts the preference fragment to configure settings for a demo activity that specified by the
 * [LaunchSource].
 */
class SettingsActivity : AppCompatActivity() {

    /** Specifies where this activity is launched from. */
    enum class LaunchSource(
        val titleResId: Int,
        val prefFragmentClass: Class<out PreferenceFragment>
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

        val launchSource = intent.getSerializableExtra(EXTRA_LAUNCH_SOURCE) as LaunchSource
        supportActionBar?.setTitle(launchSource.titleResId)

        try {
            fragmentManager
                .beginTransaction()
                .replace(
                    R.id.settings_container,
                    launchSource.prefFragmentClass.getDeclaredConstructor().newInstance()
                )
                .commit()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    companion object {
        const val EXTRA_LAUNCH_SOURCE = "extra_launch_source"
    }
}
