package com.securepass.vision.ui.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.securepass.vision.R
import java.util.ArrayList

class ChooserActivity :
  AppCompatActivity(),
  ActivityCompat.OnRequestPermissionsResultCallback,
  OnItemClickListener {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")
    setContentView(R.layout.activity_chooser)

    val listView = findViewById<ListView>(R.id.test_activity_list_view)
    val adapter = MyArrayAdapter(this, android.R.layout.simple_list_item_2, CLASSES)
    adapter.setDescriptionIds(DESCRIPTION_IDS)
    listView.adapter = adapter
    listView.onItemClickListener = this

    if (!allPermissionsGranted()) {
      getRuntimePermissions()
    }
  }

  override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
    val clicked = CLASSES[position]
    startActivity(Intent(this, clicked))
  }

  private fun getRequiredPermissions(): Array<String?> {
    return try {
      val info = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
      info.requestedPermissions ?: arrayOfNulls(0)
    } catch (e: Exception) {
      arrayOfNulls(0)
    }
  }

  private fun allPermissionsGranted(): Boolean {
    for (permission in getRequiredPermissions()) {
      permission?.let {
        if (!isPermissionGranted(this, it)) return false
      }
    }
    return true
  }

  private fun getRuntimePermissions() {
    val allNeededPermissions = ArrayList<String>()
    for (permission in getRequiredPermissions()) {
      permission?.let {
        if (!isPermissionGranted(this, it)) allNeededPermissions.add(permission)
      }
    }

    if (allNeededPermissions.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS)
    }
  }

  private fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  private class MyArrayAdapter(
    private val ctx: Context,
    resource: Int,
    private val classes: Array<Class<*>>
  ) : ArrayAdapter<Class<*>>(ctx, resource, classes) {
    private var descriptionIds: IntArray? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      var view = convertView
      if (convertView == null) {
        val inflater = ctx.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(android.R.layout.simple_list_item_2, null)
      }

      (view!!.findViewById<View>(android.R.id.text1) as TextView).text = classes[position].simpleName
      descriptionIds?.let {
        (view.findViewById<View>(android.R.id.text2) as TextView).setText(it[position])
      }
      return view
    }

    fun setDescriptionIds(descriptionIds: IntArray) {
      this.descriptionIds = descriptionIds
    }
  }

  companion object {
    private const val TAG = "ChooserActivity"
    private const val PERMISSION_REQUESTS = 1
    private val CLASSES = if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP)
      arrayOf<Class<*>>(LivePreviewActivity::class.java)
      else arrayOf<Class<*>>(
        LivePreviewActivity::class.java,
        CameraXLivePreviewActivity::class.java
      )
    private val DESCRIPTION_IDS = if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP)
      intArrayOf(R.string.desc_camera_source_activity)
      else intArrayOf(
        R.string.desc_camera_source_activity,
        R.string.desc_camerax_live_preview_activity
      )
  }
}
