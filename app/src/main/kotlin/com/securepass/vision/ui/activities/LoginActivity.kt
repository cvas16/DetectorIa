package com.securepass.vision.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.securepass.vision.R
import com.securepass.vision.data.db.DatabaseHelper

class LoginActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar sesión existente antes de mostrar el login
        val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        if (sharedPref.contains("IS_ADMIN")) {
            val isAdmin = sharedPref.getBoolean("IS_ADMIN", false)
            if (isAdmin) {
                navigateToAdminDashboard()
            } else {
                navigateToCamera()
            }
            return
        }

        setContentView(R.layout.activity_login)

        dbHelper = DatabaseHelper(this)

        val etUser = findViewById<TextInputEditText>(R.id.et_user)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val username = etUser.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Verificar si es Administrador (Hardcoded para prototipo)
            if (username == "admin" && password == "admin123") {
                val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean("IS_ADMIN", true)
                    putString("CURRENT_USER_NAME", "Administrador")
                    apply()
                }
                navigateToAdminDashboard()
                return@setOnClickListener
            }

            // 2. Verificar en la Base de Datos si es un usuario (Personal)
            val user = dbHelper.getUserByUsername(username)
            if (user != null && user.password == password) {
                // Guardar ID del usuario en SharedPreferences para saber quién está logueado
                val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean("IS_ADMIN", false)
                    putLong("CURRENT_USER_ID", user.id)
                    putString("CURRENT_USER_NAME", user.name)
                    putLong("CURRENT_GROUP_ID", user.groupId)
                    apply()
                }
                navigateToCamera()
            } else {
                Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToAdminDashboard() {
        val intent = Intent(this, AdminDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToCamera() {
        // Volvemos a enviar al menú de selección (Chooser) para que el usuario elija
        val intent = Intent(this, ChooserActivity::class.java)
        startActivity(intent)
        finish()
    }
}
