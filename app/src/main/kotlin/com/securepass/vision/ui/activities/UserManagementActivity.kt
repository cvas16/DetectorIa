package com.securepass.vision.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.securepass.vision.R
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.model.SecurityEventGroup
import com.securepass.vision.model.User

class UserManagementActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        dbHelper = DatabaseHelper(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.user_mgmt_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val rvUsers = findViewById<RecyclerView>(R.id.rv_users)
        rvUsers.layoutManager = LinearLayoutManager(this)
        
        adapter = UserAdapter(mutableListOf()) { user ->
            showReassignDialog(user)
        }
        rvUsers.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_user).setOnClickListener {
            showAddUserDialog()
        }

        refreshUserList()
    }

    private fun refreshUserList() {
        val users = dbHelper.getAllUsers()
        val groups = dbHelper.getAllGroups()
        adapter.setGroups(groups)
        adapter.updateUsers(users)
    }

    private fun showAddUserDialog() {
        // ... (existing code for showAddUserDialog)
    }

    private fun showReassignDialog(user: User) {
        val groups = dbHelper.getAllGroups()
        val groupNames = groups.map { it.name }
        
        val spinner = Spinner(this)
        spinner.setPadding(48, 32, 48, 32)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groupNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        
        // Pre-seleccionar el grupo actual
        val currentGroupPos = groups.indexOfFirst { it.id == user.groupId }
        if (currentGroupPos != -1) spinner.setSelection(currentGroupPos)

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle("Reasignar Evento a ${user.name}")
            .setMessage("Seleccione el nuevo evento de seguridad:")
            .setView(spinner)
            .setPositiveButton("Actualizar") { _, _ ->
                val selectedPos = spinner.selectedItemPosition
                if (selectedPos != -1) {
                    val newGroupId = groups[selectedPos].id
                    dbHelper.updateUserGroup(user.id, newGroupId)
                    refreshUserList()
                    Toast.makeText(this, "Usuario reasignado con éxito", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    inner class UserAdapter(
        private var users: MutableList<User>,
        private val onUserClick: (User) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        private var groupsMap = mapOf<Long, String>()

        fun setGroups(groups: List<SecurityEventGroup>) {
            groupsMap = groups.associate { it.id to it.name }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            val groupName = groupsMap[user.groupId] ?: "Sin asignar"
            holder.bind(user, groupName)
            holder.itemView.setOnClickListener { onUserClick(user) }
        }

        override fun getItemCount() = users.size

        fun updateUsers(newUsers: List<User>) {
            users.clear()
            users.addAll(newUsers)
            notifyDataSetChanged()
        }

        inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName = itemView.findViewById<TextView>(R.id.tv_user_full_name)
            private val tvUsername = itemView.findViewById<TextView>(R.id.tv_username)
            private val tvLicense = itemView.findViewById<TextView>(R.id.tv_license_key)
            private val tvGroup = itemView.findViewById<TextView>(R.id.tv_assigned_group)

            fun bind(user: User, groupName: String) {
                tvName.text = user.name
                tvUsername.text = "Usuario: ${user.username}"
                tvLicense.text = "Licencia: ${user.licenseKey}"
                tvGroup.text = "Evento: $groupName"
            }
        }
    }
}
