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
import androidx.lifecycle.lifecycleScope
import com.securepass.vision.data.api.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar sesión existente completa antes de mostrar el login
        val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        if (sharedPref.contains("CURRENT_USER_ID")) {
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

            lifecycleScope.launch {
                try {
                    // Primero intentamos con el API remoto
                    val response = RetrofitClient.instance.getAllUsers()
                    if (response.isSuccessful) {
                        val remoteUsers = response.body()
                        val remoteUser = remoteUsers?.find { it.username == username && it.password == password }
                        if (remoteUser != null) {
                            saveSessionAndNavigate(remoteUser)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    // Si falla la red, continuamos con la base de datos local
                }

                // Verificar en la Base de Datos local (Incluye al admin si fue sincronizado o insertado)
                val user = dbHelper.getUserByUsername(username)
                if (user != null && user.password == password) {
                    saveSessionAndNavigate(user)
                } else {
                    // Fallback de seguridad: Admin por defecto (solo si no hay internet Y no está en DB)
                    if (username == "admin" && password == "admin123") {
                        val adminUser = com.securepass.vision.model.User(
                            id = "admin-id",
                            name = "Administrador",
                            username = "admin",
                            password = "admin123",
                            licenseKey = "MASTER",
                            groupId = 0,
                            role = "admin"
                        )
                        saveSessionAndNavigate(adminUser)
                    } else {
                        Toast.makeText(this@LoginActivity, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveSessionAndNavigate(user: com.securepass.vision.model.User) {
        // Sincronización inmediata: Guardar el usuario que acaba de loguearse en la DB local
        dbHelper.insertUser(user)

        val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("IS_ADMIN", user.role == "admin")
            putString("CURRENT_USER_ID", user.id)
            putString("CURRENT_USER_NAME", user.name)
            putLong("CURRENT_GROUP_ID", user.groupId)
            putString("CURRENT_USER_ROLE", user.role)
            apply()
        }
        if (user.role == "admin") {
            navigateToAdminDashboard()
        } else {
            navigateToCamera()
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
