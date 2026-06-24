package com.securepass.vision.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.securepass.vision.R
import com.securepass.vision.data.api.RetrofitClient
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.model.SecurityEventGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventManagementActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_management)

        dbHelper = DatabaseHelper(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.event_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.manage_events_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val rvEvents = findViewById<RecyclerView>(R.id.rv_events)
        rvEvents.layoutManager = LinearLayoutManager(this)
        adapter = EventAdapter(
            onDeleteClick = { event -> showDeleteConfirmation(event) },
            onEditClick = { event -> showEditEventDialog(event) }
        )
        rvEvents.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_event).setOnClickListener {
            showAddEventDialog()
        }

        syncEventsWithCloud()
    }

    private fun syncEventsWithCloud() {
        lifecycleScope.launch {
            try {
                // 1. Intentar descargar desde MockAPI (Proyecto 2)
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.eventsInstance.getAllGroups()
                }

                if (response.isSuccessful) {
                    val remoteEvents = response.body() ?: emptyList()
                    // Actualizar DB local con los datos de la nube
                    withContext(Dispatchers.IO) {
                        remoteEvents.forEach { dbHelper.insertGroup(it) }
                    }
                    Log.d("Sync", "Eventos sincronizados desde la nube")
                }
            } catch (_: Exception) {
                Log.e("Sync", "Error de red, usando datos locales")
            }
            loadLocalEvents()
        }
    }

    private fun loadLocalEvents() {
        val newItems = dbHelper.getAllGroups()
        adapter.updateData(newItems)
    }

    private fun showAddEventDialog() {
        val container = findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, container, false)
        val etName = view.findViewById<EditText>(R.id.et_event_name)
        val etLocation = view.findViewById<EditText>(R.id.et_event_location)
        val etProhibited = view.findViewById<EditText>(R.id.et_prohibited_items)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = etName.text.toString()
                val location = etLocation.text.toString()
                val prohibited = etProhibited.text.toString()

                if (name.isNotEmpty() && location.isNotEmpty()) {
                    val newGroup = SecurityEventGroup(
                        name = name,
                        location = location,
                        prohibitedItems = prohibited
                    )
                    saveEvent(newGroup)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveEvent(group: SecurityEventGroup) {
        lifecycleScope.launch {
            try {
                // 1. Guardar en la Nube
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.eventsInstance.createGroup(group)
                }
                
                if (response.isSuccessful) {
                    // 2. Si la nube acepta, guardar en Local con el ID real
                    val savedGroup = response.body()
                    if (savedGroup != null) {
                        withContext(Dispatchers.IO) { dbHelper.insertGroup(savedGroup) }
                        Toast.makeText(this@EventManagementActivity, R.string.event_saved_cloud, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                // Fallback: solo local si no hay internet
                withContext(Dispatchers.IO) { dbHelper.insertGroup(group) }
                Toast.makeText(this@EventManagementActivity, R.string.event_saved_local, Toast.LENGTH_SHORT).show()
            }
            loadLocalEvents()
        }
    }

    private fun showEditEventDialog(event: SecurityEventGroup) {
        val container = findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, container, false)
        val etName = view.findViewById<EditText>(R.id.et_event_name)
        val etLocation = view.findViewById<EditText>(R.id.et_event_location)
        val etProhibited = view.findViewById<EditText>(R.id.et_prohibited_items)

        etName.setText(event.name)
        etLocation.setText(event.location)
        etProhibited.setText(event.prohibitedItems)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(view)
            .setPositiveButton(R.string.action_update) { _, _ ->
                val updated = event.copy(
                    name = etName.text.toString(),
                    location = etLocation.text.toString(),
                    prohibitedItems = etProhibited.text.toString()
                )
                updateEvent(updated)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateEvent(event: SecurityEventGroup) {
        lifecycleScope.launch {
            try {
                event.id?.let { id ->
                    RetrofitClient.eventsInstance.updateGroup(id, event)
                }
            } catch (_: Exception) { }
            withContext(Dispatchers.IO) { dbHelper.updateGroup(event) }
            loadLocalEvents()
        }
    }

    private fun showDeleteConfirmation(event: SecurityEventGroup) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.delete_event_title)
            .setMessage(R.string.delete_event_confirmation)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteEvent(event)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun deleteEvent(event: SecurityEventGroup) {
        lifecycleScope.launch {
            try {
                event.id?.let { id ->
                    RetrofitClient.eventsInstance.deleteGroup(id)
                }
            } catch (_: Exception) { }
            withContext(Dispatchers.IO) { dbHelper.deleteGroup(event.id) }
            loadLocalEvents()
        }
    }

    inner class EventAdapter(
        private val onDeleteClick: (SecurityEventGroup) -> Unit,
        private val onEditClick: (SecurityEventGroup) -> Unit
    ) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

        private var list: List<SecurityEventGroup> = emptyList()

        fun updateData(newList: List<SecurityEventGroup>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = list.size
                override fun getNewListSize(): Int = newList.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                    list[oldPos].id == newList[newPos].id
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                    list[oldPos] == newList[newPos]
            })
            list = newList.toList()
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_event_management, parent, false)
            return EventViewHolder(view)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            val event = list[position]
            holder.tvName.text = event.name
            holder.tvLocation.text = holder.itemView.context.getString(R.string.event_location_format, event.location)
            holder.tvProhibited.text = holder.itemView.context.getString(R.string.event_prohibited_format, event.prohibitedItems)

            holder.btnEdit.setOnClickListener { onEditClick(event) }
            holder.btnDelete.setOnClickListener { onDeleteClick(event) }
        }

        override fun getItemCount(): Int = list.size

        inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_event_name)
            val tvLocation: TextView = view.findViewById(R.id.tv_event_location)
            val tvProhibited: TextView = view.findViewById(R.id.tv_prohibited_items)
            val btnEdit: View = view.findViewById(R.id.btn_edit_event)
            val btnDelete: View = view.findViewById(R.id.btn_delete_event)
        }
    }
}
