package com.securepass.vision.ui.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.securepass.vision.R
import com.google.android.material.appbar.MaterialToolbar

class ChooserActivity :
  AppCompatActivity(),
  ActivityCompat.OnRequestPermissionsResultCallback,
  OnItemClickListener {
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")
    setContentView(R.layout.activity_chooser)

    val toolbar = findViewById<MaterialToolbar>(R.id.chooser_toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.title = "Vigilante AI"

    // Leer privilegios del usuario logueado
    val sharedPref = getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
    val isAdmin = sharedPref.getBoolean("IS_ADMIN", false)
    val userName = sharedPref.getString("CURRENT_USER_NAME", "Usuario")
    val userRole = sharedPref.getString("CURRENT_USER_ROLE", "Staff")

    toolbar.subtitle = "Sesión: $userName ($userRole)"

    // Construir lista de actividades según el rol
    val classesList = mutableListOf<Class<*>>()
    val descriptionsList = mutableListOf<Int>()

    // Opciones para todos los usuarios
    classesList.add(LivePreviewActivity::class.java)
    descriptionsList.add(R.string.desc_camera_source_activity)

    classesList.add(CameraXLivePreviewActivity::class.java)
    descriptionsList.add(R.string.desc_camerax_live_preview_activity)

    classesList.add(HistoryActivity::class.java)
    descriptionsList.add(R.string.menu_item_history)

    // Solo añadir el Dashboard si es administrador
    if (isAdmin) {
      classesList.add(AdminDashboardActivity::class.java)
      descriptionsList.add(R.string.admin_panel_description)
    }

    val listView = findViewById<ListView>(R.id.test_activity_list_view)
    val adapter = MyArrayAdapter(this, android.R.layout.simple_list_item_2, classesList.toTypedArray())
    adapter.setDescriptionIds(descriptionsList.toIntArray())
    listView.adapter = adapter
    listView.onItemClickListener = this

    findViewById<View>(R.id.btn_logout).setOnClickListener {
      logout()
    }

    if (!allPermissionsGranted()) {
      getRuntimePermissions()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.action_logout) {
      logout()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun logout() {
    val sharedPref = getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
    sharedPref.edit { clear() }
    val intent = Intent(this, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    finish()
  }

  override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
    val clicked = parent?.adapter?.getItem(position) as Class<*>
    startActivity(Intent(this, clicked))
  }

  private fun getRequiredPermissions(): Array<String> {
    return try {
      val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
      info.requestedPermissions ?: emptyArray()
    } catch (_: Exception) {
      emptyArray()
    }
  }

  private fun allPermissionsGranted(): Boolean {
    return getRequiredPermissions().all { isPermissionGranted(this, it) }
  }

  private fun getRuntimePermissions() {
    val allNeededPermissions = getRequiredPermissions().filter { !isPermissionGranted(this, it) }

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
      val view = convertView ?: (ctx.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        .inflate(android.R.layout.simple_list_item_2, parent, false)

      val text1 = view.findViewById<TextView>(android.R.id.text1)
      val text2 = view.findViewById<TextView>(android.R.id.text2)

      text1.text = classes[position].simpleName
      text1.setTextColor(ContextCompat.getColor(ctx, R.color.white))

      descriptionIds?.let {
        text2.setText(it[position])
        text2.setTextColor(ContextCompat.getColor(ctx, R.color.gray))
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
  }
}
