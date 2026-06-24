package com.securepass.vision.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.ImageButton
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
import androidx.lifecycle.lifecycleScope
import com.securepass.vision.data.api.RetrofitClient
import kotlinx.coroutines.launch
import com.securepass.vision.model.SecurityEventGroup
import com.securepass.vision.model.User
import androidx.recyclerview.widget.DiffUtil

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
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllUsers()
                if (response.isSuccessful) {
                    val remoteUsers = response.body() ?: emptyList()
                    remoteUsers.forEach { remoteUser ->
                        dbHelper.insertUser(remoteUser) 
                    }
                    adapter.updateUsers(remoteUsers)
                } else {
                    val localUsers = dbHelper.getAllUsers()
                    adapter.updateUsers(localUsers)
                    Toast.makeText(this@UserManagementActivity, R.string.error_api_local, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                val localUsers = dbHelper.getAllUsers()
                adapter.updateUsers(localUsers)
                Toast.makeText(this@UserManagementActivity, R.string.error_no_connection_local, Toast.LENGTH_SHORT).show()
            }
            val groups = dbHelper.getAllGroups()
            adapter.setGroups(groups)
        }
    }

    private fun showAddUserDialog() {
        val container = findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, container, false)
        val etName = view.findViewById<TextInputEditText>(R.id.et_dialog_name)
        val etUsername = view.findViewById<TextInputEditText>(R.id.et_dialog_username)
        val etPassword = view.findViewById<TextInputEditText>(R.id.et_dialog_password)
        val etLicense = view.findViewById<TextInputEditText>(R.id.et_dialog_license)
        val spinnerRoles = view.findViewById<Spinner>(R.id.spinner_dialog_role)
        val spinnerGroups = view.findViewById<Spinner>(R.id.spinner_dialog_group)

        val roles = listOf("staff", "admin")
        val roleDisplayNames = listOf(getString(R.string.role_staff), getString(R.string.role_admin))
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roleDisplayNames)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoles.adapter = roleAdapter

        val groups = dbHelper.getAllGroups()
        val groupNames = groups.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groupNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGroups.adapter = spinnerAdapter

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(view)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = etName.text.toString()
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                val license = etLicense.text.toString()
                val selectedRole = roles[spinnerRoles.selectedItemPosition]
                val selectedGroupPos = spinnerGroups.selectedItemPosition

                if (name.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && license.isNotEmpty() && selectedGroupPos != -1) {
                    val groupId = groups[selectedGroupPos].id ?: "0"
                    val newUser = User(
                        id = System.currentTimeMillis().toString(),
                        name = name,
                        username = username,
                        password = password,
                        licenseKey = license,
                        groupId = groupId,
                        role = selectedRole
                    )
                    
                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.createUser(newUser)
                            if (response.isSuccessful) {
                                val savedUser = response.body() ?: newUser
                                dbHelper.insertUser(savedUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, R.string.save_remote_local_success, Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.insertUser(newUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, R.string.save_local_api_error, Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            dbHelper.insertUser(newUser)
                            refreshUserList()
                            Toast.makeText(this@UserManagementActivity, R.string.save_local_no_connection, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showReassignDialog(user: User) {
        val groups = dbHelper.getAllGroups()
        val groupNames = groups.map { it.name }
        
        val spinner = Spinner(this)
        spinner.setPadding(48, 32, 48, 32)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groupNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        
        val currentGroupPos = groups.indexOfFirst { it.id == user.groupId }
        if (currentGroupPos != -1) spinner.setSelection(currentGroupPos)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(getString(R.string.reassign_event_title, user.name))
            .setMessage(R.string.reassign_event_message)
            .setView(spinner)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val selectedPos = spinner.selectedItemPosition
                if (selectedPos != -1) {
                    val newGroupId = groups[selectedPos].id ?: "0"
                    val updatedUser = user.copy(groupId = newGroupId)
                    
                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.updateUser(user.id, updatedUser)
                            if (response.isSuccessful) {
                                dbHelper.updateUserGroup(user.id, newGroupId)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, R.string.sync_api_success, Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.updateUserGroup(user.id, newGroupId)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, R.string.update_local_api_error, Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            dbHelper.updateUserGroup(user.id, newGroupId)
                            refreshUserList()
                            Toast.makeText(this@UserManagementActivity, R.string.update_local_no_connection, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditUserDialog(user: User) {
        val container = findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, container, false)
        val etName = view.findViewById<TextInputEditText>(R.id.et_dialog_name)
        val etUsername = view.findViewById<TextInputEditText>(R.id.et_dialog_username)
        val etPassword = view.findViewById<TextInputEditText>(R.id.et_dialog_password)
        val etLicense = view.findViewById<TextInputEditText>(R.id.et_dialog_license)
        val spinnerRoles = view.findViewById<Spinner>(R.id.spinner_dialog_role)
        val spinnerGroups = view.findViewById<Spinner>(R.id.spinner_dialog_group)

        etName.setText(user.name)
        etUsername.setText(user.username)
        etPassword.setText(user.password)
        etLicense.setText(user.licenseKey)

        val roles = listOf("staff", "admin")
        val roleDisplayNames = listOf(getString(R.string.role_staff), getString(R.string.role_admin))
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roleDisplayNames)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoles.adapter = roleAdapter
        
        val currentRolePos = roles.indexOf(user.role)
        if (currentRolePos != -1) spinnerRoles.setSelection(currentRolePos)

        val groups = dbHelper.getAllGroups()
        val groupNames = groups.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groupNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGroups.adapter = spinnerAdapter
        
        val currentGroupPos = groups.indexOfFirst { it.id == user.groupId }
        if (currentGroupPos != -1) spinnerGroups.setSelection(currentGroupPos)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.edit_user_title)
            .setView(view)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val name = etName.text.toString()
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                val license = etLicense.text.toString()
                val selectedRole = roles[spinnerRoles.selectedItemPosition]
                val selectedGroupPos = spinnerGroups.selectedItemPosition

                if (name.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && license.isNotEmpty() && selectedGroupPos != -1) {
                    val groupId = groups[selectedGroupPos].id ?: "0"
                    val updatedUser = user.copy(
                        name = name,
                        username = username,
                        password = password,
                        licenseKey = license,
                        groupId = groupId,
                        role = selectedRole
                    )
                    
                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.updateUser(user.id, updatedUser)
                            if (response.isSuccessful) {
                                dbHelper.updateUser(updatedUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, R.string.update_remote_local_success, Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.updateUser(updatedUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, R.string.update_local_api_error, Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            dbHelper.updateUser(updatedUser)
                            refreshUserList()
                            Toast.makeText(this@UserManagementActivity, R.string.update_local_no_connection, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(user: User) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.delete_user_title)
            .setMessage(getString(R.string.delete_user_confirmation, user.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        RetrofitClient.instance.deleteUser(user.id)
                        dbHelper.deleteUser(user.id)
                        Toast.makeText(this@UserManagementActivity, R.string.user_deleted_success, Toast.LENGTH_SHORT).show()
                        refreshUserList()
                    } catch (_: Exception) {
                        Toast.makeText(this@UserManagementActivity, R.string.error_deleting_user, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    inner class UserAdapter(
        private var users: MutableList<User>,
        private val onUserClick: (User) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        private var groupsMap = mapOf<String, String>()

        fun setGroups(groups: List<SecurityEventGroup>) {
            groupsMap = groups.associate { (it.id ?: "0") to it.name }
            if (users.isNotEmpty()) {
                notifyItemRangeChanged(0, users.size)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            val groupName = groupsMap[user.groupId] ?: holder.itemView.context.getString(R.string.no_assigned_group)
            holder.bind(user, groupName)
            holder.itemView.setOnClickListener { onUserClick(user) }
            holder.btnEdit.setOnClickListener { showEditUserDialog(user) }
            holder.btnDelete.setOnClickListener { showDeleteConfirmation(user) }
        }

        override fun getItemCount() = users.size

        fun updateUsers(newUsers: List<User>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = users.size
                override fun getNewListSize() = newUsers.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    users[oldItemPosition].id == newUsers[newItemPosition].id
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    users[oldItemPosition] == newUsers[newItemPosition]
            })
            users.clear()
            users.addAll(newUsers)
            diffResult.dispatchUpdatesTo(this)
        }

        inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tv_user_full_name)
            private val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
            private val tvLicense: TextView = itemView.findViewById(R.id.tv_license_key)
            private val tvGroup: TextView = itemView.findViewById(R.id.tv_assigned_group)
            val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit_user)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_user)

            fun bind(user: User, groupName: String) {
                tvName.text = user.name
                tvUsername.text = itemView.context.getString(R.string.user_label, user.username)
                tvLicense.text = itemView.context.getString(R.string.license_label, user.licenseKey)
                tvGroup.text = itemView.context.getString(R.string.assigned_event_label, groupName)
            }
        }
    }
}
