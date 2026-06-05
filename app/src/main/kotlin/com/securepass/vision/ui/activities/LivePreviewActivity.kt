package com.securepass.vision.ui.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.model.LocalModel
import com.securepass.vision.R
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.vision.CameraSource
import com.securepass.vision.ui.components.CameraSourcePreview
import com.securepass.vision.ui.components.GraphicOverlay
import com.securepass.vision.vision.ObjectDetectorProcessor
import com.securepass.vision.utils.PreferenceUtils
import com.securepass.vision.ui.activities.SettingsActivity
import com.securepass.vision.ui.activities.SettingsActivity.LaunchSource
import java.io.IOException
import java.util.ArrayList

@KeepName
class LivePreviewActivity :
  AppCompatActivity(),
  ActivityCompat.OnRequestPermissionsResultCallback,
  OnItemSelectedListener,
  CompoundButton.OnCheckedChangeListener {

  private var cameraSource: CameraSource? = null
  private var preview: CameraSourcePreview? = null
  private var graphicOverlay: GraphicOverlay? = null
  private var selectedModel = SECURITY_MODE

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")
    setContentView(R.layout.activity_vision_live_preview)

    val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.camera_toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.title = "Vigilante AI"

    preview = findViewById(R.id.preview_view)
    if (preview == null) {
      Log.d(TAG, "Preview is null")
    }

    graphicOverlay = findViewById(R.id.graphic_overlay)
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null")
    }

    val spinner = findViewById<Spinner>(R.id.spinner)
    val options: MutableList<String> = ArrayList()
    options.add(SECURITY_MODE)

    val dataAdapter = ArrayAdapter(this, R.layout.spinner_style, options)
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = dataAdapter
    spinner.onItemSelectedListener = this

    val facingSwitch = findViewById<ToggleButton>(R.id.facing_switch)
    facingSwitch.setOnCheckedChangeListener(this)

    val settingsButton = findViewById<ImageView>(R.id.settings_button)
    settingsButton.setOnClickListener {
      if (SECURITY_MODE == selectedModel) {
        showSecurityConfigDialog()
      } else {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_LAUNCH_SOURCE, LaunchSource.LIVE_PREVIEW)
        startActivity(intent)
      }
    }

    if (allPermissionsGranted()) {
      createCameraSource(selectedModel)
    } else {
      runtimePermissions
    }
  }

  @Synchronized
  override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
    selectedModel = parent?.getItemAtPosition(pos).toString()
    Log.d(TAG, "Selected model: $selectedModel")
    preview?.stop()
    if (allPermissionsGranted()) {
      createCameraSource(selectedModel)
      startCameraSource()
    } else {
      runtimePermissions
    }
  }

  override fun onNothingSelected(parent: AdapterView<*>?) {}

  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    Log.d(TAG, "Set facing")
    if (cameraSource != null) {
      if (isChecked) {
        cameraSource?.setFacing(CameraSource.CAMERA_FACING_FRONT)
      } else {
        cameraSource?.setFacing(CameraSource.CAMERA_FACING_BACK)
      }
    }
    preview?.stop()
    startCameraSource()
  }

  private fun createCameraSource(model: String) {
    if (cameraSource == null) {
      cameraSource = CameraSource(this, graphicOverlay!!)
    }
    try {
      if (model == SECURITY_MODE) {
        Log.i(TAG, "Using Custom Object Detector Processor (Security Mode)")
        val securityModel = LocalModel.Builder()
          .setAssetFilePath("custom_models/object_labeler.tflite")
          .build()
        val securityOptions = PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(this, securityModel)
        
        // 1. Obtener el grupo asignado al usuario
        val authPrefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        val groupId = authPrefs.getLong("CURRENT_GROUP_ID", -1L)

        // 2. Buscar las reglas en la DB
        val dbHelper = DatabaseHelper(this)
        val group = dbHelper.getGroupById(groupId)

        // 3. Usar los objetos del evento
        val prohibitedStr = group?.prohibitedItems ?: "Knife,Weapon"
        val prohibitedList = prohibitedStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        val userId = authPrefs.getString("CURRENT_USER_ID", "0") ?: "0"
        val userName = authPrefs.getString("CURRENT_USER_NAME", "Staff") ?: "Staff"
        val eventName = group?.name ?: "Evento Desconocido"

        Log.d(TAG, "Cargando reglas para el evento: $eventName. Prohibidos: $prohibitedList")
        cameraSource!!.setMachineLearningFrameProcessor(
          ObjectDetectorProcessor(
            this, 
            securityOptions, 
            prohibitedList,
            userId,
            userName,
            groupId,
            eventName
          )
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Can not create image processor: $model", e)
      Toast.makeText(applicationContext, "Can not create image processor: " + e.message, Toast.LENGTH_LONG).show()
    }
  }

  private fun showSecurityConfigDialog() {
    val editText = android.widget.EditText(this)
    val currentProhibited = getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
      .getString("prohibited_objects", "Knife,Weapon")
    editText.setText(currentProhibited)
    editText.hint = "Ej: Knife, Bottle, Chair"

    android.app.AlertDialog.Builder(this)
      .setTitle("Configuración de Seguridad")
      .setMessage("Escriba los objetos a prohibir (separados por comas):")
      .setView(editText)
      .setPositiveButton("Guardar") { _, _ ->
        val input = editText.text.toString()
        getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
          .edit()
          .putString("prohibited_objects", input)
          .apply()
        preview?.stop()
        createCameraSource(selectedModel)
        startCameraSource()
      }
      .setNegativeButton("Cancelar", null)
      .show()
  }

  private fun startCameraSource() {
    if (cameraSource != null) {
      try {
        preview!!.start(cameraSource, graphicOverlay)
      } catch (e: IOException) {
        Log.e(TAG, "Unable to start camera source.", e)
        cameraSource!!.release()
        cameraSource = null
      }
    }
  }

  public override fun onResume() {
    super.onResume()
    createCameraSource(selectedModel)
    startCameraSource()
  }

  override fun onPause() {
    super.onPause()
    preview?.stop()
  }

  public override fun onDestroy() {
    super.onDestroy()
    cameraSource?.release()
  }

  private val requiredPermissions: Array<String?>
    get() = try {
      val info = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
      info.requestedPermissions ?: arrayOfNulls(0)
    } catch (e: Exception) {
      arrayOfNulls(0)
    }

  private fun allPermissionsGranted(): Boolean {
    for (permission in requiredPermissions) {
      if (!isPermissionGranted(this, permission)) return false
    }
    return true
  }

  private val runtimePermissions: Unit
    get() {
      val allNeededPermissions = ArrayList<String>()
      for (permission in requiredPermissions) {
        if (!isPermissionGranted(this, permission)) allNeededPermissions.add(permission!!)
      }
      if (allNeededPermissions.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS)
      }
    }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    if (allPermissionsGranted()) createCameraSource(selectedModel)
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  companion object {
    private const val SECURITY_MODE = "Security Surveillance Mode"
    private const val TAG = "LivePreviewActivity"
    private const val PERMISSION_REQUESTS = 1
    private fun isPermissionGranted(context: Context, permission: String?): Boolean {
      return ContextCompat.checkSelfPermission(context, permission!!) == PackageManager.PERMISSION_GRANTED
    }
  }
}
