package com.securepass.vision.ui.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.LocalModel
import com.securepass.vision.R
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.ui.components.GraphicOverlay
import com.securepass.vision.viewmodel.CameraXViewModel
import com.securepass.vision.vision.ObjectDetectorProcessor
import com.securepass.vision.vision.VisionImageProcessor
import com.securepass.vision.utils.PreferenceUtils
import com.securepass.vision.ui.activities.SettingsActivity
import com.securepass.vision.ui.activities.SettingsActivity.LaunchSource

@KeepName
class CameraXLivePreviewActivity :
  AppCompatActivity(),
  ActivityCompat.OnRequestPermissionsResultCallback,
  OnItemSelectedListener,
  CompoundButton.OnCheckedChangeListener {

  private var previewView: PreviewView? = null
  private var graphicOverlay: GraphicOverlay? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var previewUseCase: Preview? = null
  private var analysisUseCase: ImageAnalysis? = null
  private var imageProcessor: VisionImageProcessor? = null
  private var needUpdateGraphicOverlayImageSourceInfo = false
  private var selectedModel = SECURITY_MODE
  private var lensFacing = CameraSelector.LENS_FACING_BACK
  private var cameraSelector: CameraSelector? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")

    if (savedInstanceState != null) {
      selectedModel = savedInstanceState.getString(STATE_SELECTED_MODEL, SECURITY_MODE)
    }
    cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
    setContentView(R.layout.activity_vision_camerax_live_preview)

    val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.camera_toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.title = "Vigilante AI"

    previewView = findViewById(R.id.preview_view)
    graphicOverlay = findViewById(R.id.graphic_overlay)

    val spinner = findViewById<Spinner>(R.id.spinner)
    val options = mutableListOf(SECURITY_MODE)

    val dataAdapter = ArrayAdapter(this, R.layout.spinner_style, options)
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinner.adapter = dataAdapter
    spinner.setSelection(0)
    spinner.onItemSelectedListener = this

    val facingSwitch = findViewById<ToggleButton>(R.id.facing_switch)
    facingSwitch.setOnCheckedChangeListener(this)

    ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
      .get(CameraXViewModel::class.java)
      .getProcessCameraProvider()
      .observe(this) { provider ->
        cameraProvider = provider
        if (allPermissionsGranted()) {
          bindAllCameraUseCases()
        }
      }

    val settingsButton = findViewById<ImageView>(R.id.settings_button)
    settingsButton.setOnClickListener {
      if (SECURITY_MODE == selectedModel) {
        showSecurityConfigDialog()
      } else {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_LAUNCH_SOURCE, LaunchSource.CAMERAX_LIVE_PREVIEW)
        startActivity(intent)
      }
    }

    if (!allPermissionsGranted()) {
      runtimePermissions()
    }
  }

  override fun onSaveInstanceState(bundle: Bundle) {
    super.onSaveInstanceState(bundle)
    bundle.putString(STATE_SELECTED_MODEL, selectedModel)
  }

  @Synchronized
  override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
    selectedModel = parent?.getItemAtPosition(pos).toString()
    Log.d(TAG, "Selected model: $selectedModel")
    bindAnalysisUseCase()
  }

  override fun onNothingSelected(parent: AdapterView<*>?) {}

  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    if (cameraProvider == null) return

    val newLensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
      CameraSelector.LENS_FACING_BACK
    } else {
      CameraSelector.LENS_FACING_FRONT
    }
    val newCameraSelector = CameraSelector.Builder().requireLensFacing(newLensFacing).build()
    try {
      if (cameraProvider!!.hasCamera(newCameraSelector)) {
        Log.d(TAG, "Set facing to $newLensFacing")
        lensFacing = newLensFacing
        cameraSelector = newCameraSelector
        bindAllCameraUseCases()
        return
      }
    } catch (e: CameraInfoUnavailableException) {
      // Falls through
    }
    Toast.makeText(
      applicationContext, "This device does not have lens with facing: $newLensFacing",
      Toast.LENGTH_SHORT
    ).show()
  }

  override fun onResume() {
    super.onResume()
    bindAllCameraUseCases()
  }

  override fun onPause() {
    super.onPause()
    imageProcessor?.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    imageProcessor?.stop()
  }

  private fun bindAllCameraUseCases() {
    if (cameraProvider != null) {
      cameraProvider!!.unbindAll()
      bindPreviewUseCase()
      bindAnalysisUseCase()
    }
  }

  private fun bindPreviewUseCase() {
    if (!PreferenceUtils.isCameraLiveViewportEnabled(this) || cameraProvider == null) {
      return
    }
    if (previewUseCase != null) {
      cameraProvider!!.unbind(previewUseCase)
    }

    val builder = Preview.Builder()
    val targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing)
    if (targetResolution != null) {
      builder.setTargetResolution(targetResolution)
    }
    previewUseCase = builder.build()
    previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)
    cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)
  }

  private fun bindAnalysisUseCase() {
    if (cameraProvider == null) return

    if (analysisUseCase != null) {
      cameraProvider!!.unbind(analysisUseCase)
    }
    imageProcessor?.stop()

    imageProcessor = try {
      when (selectedModel) {
        SECURITY_MODE -> {
          Log.i(TAG, "Using Custom Object Detector (Security Mode)")
          val securityModel = LocalModel.Builder()
            .setAssetFilePath("custom_models/object_labeler.tflite")
            .build()
          val securityOptions = PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(this, securityModel)

          // 1. Obtener el grupo asignado al usuario desde SharedPreferences
          val authPrefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
          val groupId = authPrefs.getLong("CURRENT_GROUP_ID", -1L)

          // 2. Buscar las reglas del evento en la DB
          val dbHelper = DatabaseHelper(this)
          val group = dbHelper.getGroupById(groupId)

          // 3. Usar los objetos del evento o unos por defecto si no hay evento
          val prohibitedStr = group?.prohibitedItems ?: "Knife,Weapon"
          val prohibitedList = prohibitedStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

          Log.d(TAG, "Cargando reglas para el evento: ${group?.name ?: "Ninguno"}. Prohibidos: $prohibitedList")
          ObjectDetectorProcessor(this, securityOptions, prohibitedList)
        }
        else -> throw IllegalStateException("Invalid model name")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Can not create image processor: $selectedModel", e)
      Toast.makeText(
        applicationContext,
        "Can not create image processor: ${e.localizedMessage}",
        Toast.LENGTH_LONG
      ).show()
      return
    }

    val builder = ImageAnalysis.Builder()
    val targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing)
    if (targetResolution != null) {
      builder.setTargetResolution(targetResolution)
    }
    analysisUseCase = builder.build()

    needUpdateGraphicOverlayImageSourceInfo = true

    analysisUseCase?.setAnalyzer(
      ContextCompat.getMainExecutor(this)
    ) { imageProxy ->
      if (needUpdateGraphicOverlayImageSourceInfo) {
        val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees == 0 || rotationDegrees == 180) {
          graphicOverlay!!.setImageSourceInfo(imageProxy.width, imageProxy.height, isImageFlipped)
        } else {
          graphicOverlay!!.setImageSourceInfo(imageProxy.height, imageProxy.width, isImageFlipped)
        }
        needUpdateGraphicOverlayImageSourceInfo = false
      }
      try {
        imageProcessor!!.processImageProxy(imageProxy, graphicOverlay!!)
      } catch (e: MlKitException) {
        Log.e(TAG, "Failed to process image. Error: ${e.localizedMessage}")
        Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
      }
    }
    cameraProvider!!.bindToLifecycle(this, cameraSelector!!, analysisUseCase)
  }

  private fun showSecurityConfigDialog() {
    val editText = EditText(this)
    val currentProhibited = getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
      .getString("prohibited_objects", "Knife,Weapon")
    editText.setText(currentProhibited)
    editText.hint = "Ej: Knife, Bottle, Chair"

    AlertDialog.Builder(this)
      .setTitle("Configuración de Seguridad")
      .setMessage("Escriba los objetos a prohibir (separados por comas):")
      .setView(editText)
      .setPositiveButton("Guardar") { _, _ ->
        val input = editText.text.toString()
        getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
          .edit()
          .putString("prohibited_objects", input)
          .apply()
        bindAnalysisUseCase()
      }
      .setNegativeButton("Cancelar", null)
      .show()
  }

  private fun getRequiredPermissions(): Array<String> {
    return try {
      val info = this.packageManager
        .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
      val ps = info.requestedPermissions
      if (ps != null && ps.isNotEmpty()) {
        ps
      } else {
        emptyArray()
      }
    } catch (e: Exception) {
      emptyArray()
    }
  }

  private fun allPermissionsGranted(): Boolean {
    for (permission in getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        return false
      }
    }
    return true
  }

  private fun runtimePermissions() {
    val allNeededPermissions = mutableListOf<String>()
    for (permission in getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        allNeededPermissions.add(permission)
      }
    }
    if (allNeededPermissions.isNotEmpty()) {
      ActivityCompat.requestPermissions(
        this,
        allNeededPermissions.toTypedArray(),
        PERMISSION_REQUESTS
      )
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    if (allPermissionsGranted()) {
      bindAllCameraUseCases()
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  companion object {
    private const val TAG = "CameraXLivePreview"
    private const val PERMISSION_REQUESTS = 1
    private const val SECURITY_MODE = "Security Surveillance Mode"
    private const val STATE_SELECTED_MODEL = "selected_model"

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
      if (ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED
      ) {
        Log.i(TAG, "Permission granted: $permission")
        return true
      }
      Log.i(TAG, "Permission NOT granted: $permission")
      return false
    }
  }
}
