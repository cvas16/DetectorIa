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
                    
                    // Sincronización: Actualizar DB local con datos remotos
                    remoteUsers.forEach { remoteUser ->
                        // Intentamos insertar. Si ya existe, DatabaseHelper debería manejarlo (o podemos limpiar y reinsertar)
                        dbHelper.insertUser(remoteUser) 
                    }

                    adapter.updateUsers(remoteUsers)
                } else {
                    val localUsers = dbHelper.getAllUsers()
                    adapter.updateUsers(localUsers)
                    Toast.makeText(this@UserManagementActivity, "Error al cargar desde API, usando local", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val localUsers = dbHelper.getAllUsers()
                adapter.updateUsers(localUsers)
                Toast.makeText(this@UserManagementActivity, "Sin conexión, usando datos locales", Toast.LENGTH_SHORT).show()
            }
            val groups = dbHelper.getAllGroups()
            adapter.setGroups(groups)
        }
    }

    private fun showAddUserDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null)
        val etName = view.findViewById<TextInputEditText>(R.id.et_dialog_name)
        val etUsername = view.findViewById<TextInputEditText>(R.id.et_dialog_username)
        val etPassword = view.findViewById<TextInputEditText>(R.id.et_dialog_password)
        val etLicense = view.findViewById<TextInputEditText>(R.id.et_dialog_license)
        val spinnerRoles = view.findViewById<Spinner>(R.id.spinner_dialog_role)
        val spinnerGroups = view.findViewById<Spinner>(R.id.spinner_dialog_group)

        // Configurar Spinner de Roles
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
            .setPositiveButton("Guardar") { _, _ ->
                val name = etName.text.toString()
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                val license = etLicense.text.toString()
                val selectedRole = roles[spinnerRoles.selectedItemPosition]
                val selectedGroupPos = spinnerGroups.selectedItemPosition

                if (name.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && license.isNotEmpty() && selectedGroupPos != -1) {
                    val groupId = groups[selectedGroupPos].id
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
                                // USAR EL USUARIO QUE DEVUELVE LA API (Trae el ID correcto)
                                val savedUser = response.body() ?: newUser
                                dbHelper.insertUser(savedUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, "Usuario guardado en remoto y local", Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.insertUser(newUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, "Guardado solo localmente (Error API)", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            dbHelper.insertUser(newUser)
                            refreshUserList()
                            Toast.makeText(this@UserManagementActivity, "Guardado localmente (Sin conexión)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
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
        
        // Pre-seleccionar el grupo actual
        val currentGroupPos = groups.indexOfFirst { it.id == user.groupId }
        if (currentGroupPos != -1) spinner.setSelection(currentGroupPos)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Reasignar Evento a ${user.name}")
            .setMessage("Seleccione el nuevo evento de seguridad:")
            .setView(spinner)
            .setPositiveButton("Actualizar") { _, _ ->
                val selectedPos = spinner.selectedItemPosition
                if (selectedPos != -1) {
                    val newGroupId = groups[selectedPos].id
                    val updatedUser = user.copy(groupId = newGroupId)
                    
                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.updateUser(user.id, updatedUser)
                            if (response.isSuccessful) {
                                dbHelper.updateUserGroup(user.id, newGroupId)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, "Sincronizado con API", Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.updateUserGroup(user.id, newGroupId)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, "Actualizado localmente (Error API)", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            dbHelper.updateUserGroup(user.id, newGroupId)
                            refreshUserList()
                            Toast.makeText(this@UserManagementActivity, "Actualizado localmente (Sin conexión)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditUserDialog(user: User) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null)
        val etName = view.findViewById<TextInputEditText>(R.id.et_dialog_name)
        val etUsername = view.findViewById<TextInputEditText>(R.id.et_dialog_username)
        val etPassword = view.findViewById<TextInputEditText>(R.id.et_dialog_password)
        val etLicense = view.findViewById<TextInputEditText>(R.id.et_dialog_license)
        val spinnerRoles = view.findViewById<Spinner>(R.id.spinner_dialog_role)
        val spinnerGroups = view.findViewById<Spinner>(R.id.spinner_dialog_group)

        // Pre-cargar datos
        etName.setText(user.name)
        etUsername.setText(user.username)
        etPassword.setText(user.password)
        etLicense.setText(user.licenseKey)

        // Configurar Spinner de Roles
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
            .setTitle("Editar Usuario")
            .setView(view)
            .setPositiveButton("Actualizar") { _, _ ->
                val name = etName.text.toString()
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                val license = etLicense.text.toString()
                val selectedRole = roles[spinnerRoles.selectedItemPosition]
                val selectedGroupPos = spinnerGroups.selectedItemPosition

                if (name.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && license.isNotEmpty() && selectedGroupPos != -1) {
                    val groupId = groups[selectedGroupPos].id
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
                                Toast.makeText(this@UserManagementActivity, "Usuario actualizado en remoto y local", Toast.LENGTH_SHORT).show()
                            } else {
                                dbHelper.updateUser(updatedUser)
                                refreshUserList()
                                Toast.makeText(this@UserManagementActivity, "Actualizado localmente (Error API)", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            dbHelper.updateUser(updatedUser)
                            refreshUserList()
                            Toast.makeText(this@UserManagementActivity, "Actualizado localmente (Sin conexión)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteConfirmation(user: User) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Eliminar Usuario")
            .setMessage("¿Estás seguro de que deseas eliminar a ${user.name}? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Eliminar de MockAPI
                        RetrofitClient.instance.deleteUser(user.id)
                        // Eliminar de Local
                        dbHelper.deleteUser(user.id)
                        Toast.makeText(this@UserManagementActivity, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                        refreshUserList()
                    } catch (e: Exception) {
                        Toast.makeText(this@UserManagementActivity, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
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
            holder.btnEdit.setOnClickListener { showEditUserDialog(user) }
            holder.btnDelete.setOnClickListener { showDeleteConfirmation(user) }
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
            val btnEdit = itemView.findViewById<ImageButton>(R.id.btn_edit_user)
            val btnDelete = itemView.findViewById<ImageButton>(R.id.btn_delete_user)

            fun bind(user: User, groupName: String) {
                tvName.text = user.name
                tvUsername.text = "Usuario: ${user.username}"
                tvLicense.text = "Licencia: ${user.licenseKey}"
                tvGroup.text = "Evento: $groupName"
            }
        }
    }
}
