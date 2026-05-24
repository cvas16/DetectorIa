package com.securepass.vision.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.securepass.vision.R

class AdminDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar si es Administrador
        val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        val isAdmin = sharedPref.getBoolean("IS_ADMIN", false)
        if (!isAdmin) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_admin_dashboard)

        val toolbar = findViewById<MaterialToolbar>(R.id.admin_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val cardManageUsers = findViewById<MaterialCardView>(R.id.card_manage_users)
        val cardManageEvents = findViewById<MaterialCardView>(R.id.card_manage_events)

        cardManageUsers.setOnClickListener {
            val intent = Intent(this, UserManagementActivity::class.java)
            startActivity(intent)
        }

        cardManageEvents.setOnClickListener {
            val intent = Intent(this, EventManagementActivity::class.java)
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.btn_admin_logout).setOnClickListener {
            logout()
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
        val sharedPref = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
