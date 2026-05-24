package com.securepass.vision.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.securepass.vision.R
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.model.SecurityEventGroup

class EventManagementActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: EventAdapter
    private val eventsList = mutableListOf<SecurityEventGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_management)

        dbHelper = DatabaseHelper(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.event_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Configuración de Eventos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val rvEvents = findViewById<RecyclerView>(R.id.rv_events)
        rvEvents.layoutManager = LinearLayoutManager(this)
        adapter = EventAdapter(eventsList,
            onDeleteClick = { event -> showDeleteConfirmation(event) },
            onEditClick = { event -> showEditEventDialog(event) }
        )
        rvEvents.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_event).setOnClickListener {
            showAddEventDialog()
        }

        loadEvents()
    }

    private fun loadEvents() {
        eventsList.clear()
        eventsList.addAll(dbHelper.getAllGroups())
        adapter.notifyDataSetChanged()
    }

    private fun showAddEventDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null)
        val etName = view.findViewById<EditText>(R.id.et_event_name)
        val etLocation = view.findViewById<EditText>(R.id.et_event_location)
        val etProhibited = view.findViewById<EditText>(R.id.et_prohibited_items)

        AlertDialog.Builder(this)
            .setTitle("Nuevo Evento")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val name = etName.text.toString()
                val location = etLocation.text.toString()
                val prohibited = etProhibited.text.toString()

                if (name.isNotEmpty() && location.isNotEmpty()) {
                    val newGroup = SecurityEventGroup(
                        name = name,
                        location = location,
                        prohibitedItems = prohibited
                    )
                    dbHelper.insertGroup(newGroup)
                    loadEvents()
                } else {
                    Toast.makeText(this, "Nombre y ubicación son obligatorios", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditEventDialog(event: SecurityEventGroup) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_event, null)
        val etName = view.findViewById<EditText>(R.id.et_event_name)
        val etLocation = view.findViewById<EditText>(R.id.et_event_location)
        val etProhibited = view.findViewById<EditText>(R.id.et_prohibited_items)

        etName.setText(event.name)
        etLocation.setText(event.location)
        etProhibited.setText(event.prohibitedItems)

        AlertDialog.Builder(this)
            .setTitle("Editar Evento")
            .setView(view)
            .setPositiveButton("Actualizar") { _, _ ->
                val name = etName.text.toString()
                val location = etLocation.text.toString()
                val prohibited = etProhibited.text.toString()

                if (name.isNotEmpty() && location.isNotEmpty()) {
                    val updatedEvent = event.copy(
                        name = name,
                        location = location,
                        prohibitedItems = prohibited
                    )
                    dbHelper.updateGroup(updatedEvent)
                    loadEvents()
                } else {
                    Toast.makeText(this, "Nombre y ubicación son obligatorios", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteConfirmation(event: SecurityEventGroup) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Evento")
            .setMessage("¿Estás seguro de que deseas eliminar el evento '${event.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                dbHelper.deleteGroup(event.id)
                loadEvents()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    inner class EventAdapter(
        private val list: List<SecurityEventGroup>,
        private val onDeleteClick: (SecurityEventGroup) -> Unit,
        private val onEditClick: (SecurityEventGroup) -> Unit
    ) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return EventViewHolder(view)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            val event = list[position]
            holder.text1.text = event.name
            holder.text2.text = "Ubicación: ${event.location}\nProhibido: ${event.prohibitedItems}"
            holder.itemView.setOnClickListener {
                onEditClick(event)
            }
            holder.itemView.setOnLongClickListener {
                onDeleteClick(event)
                true
            }
        }

        override fun getItemCount(): Int = list.size

        inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text1: TextView = view.findViewById(android.R.id.text1)
            val text2: TextView = view.findViewById(android.R.id.text2)

            init {
                text1.setTextColor(ContextCompat.getColor(this@EventManagementActivity, R.color.white))
                text2.setTextColor(ContextCompat.getColor(this@EventManagementActivity, R.color.gray))
            }
        }
    }
}
