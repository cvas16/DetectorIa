package com.securepass.vision.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.securepass.vision.R
import com.securepass.vision.data.api.RetrofitClient
import com.securepass.vision.ui.adapters.HistoryAdapter
import kotlinx.coroutines.launch

class GlobalHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history) // Reutilizamos el layout de history

        val toolbar = findViewById<Toolbar>(R.id.history_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Historial Global (Nube)"

        recyclerView = findViewById(R.id.history_recycler_view)
        emptyState = findViewById(R.id.empty_state_container)
        
        // Añadimos un progress bar programáticamente o verificamos si existe
        // En este caso, como activity_history no lo tiene, lo manejaremos con el empty state por ahora
        // o simplemente cargamos los datos.

        setupRecyclerView()
        loadGlobalData()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            events = emptyList(),
            onItemClick = { event ->
                // Opcional: ver detalles o ampliar imagen si existiera
            },
            onDeleteClick = { event ->
                showDeleteConfirmation(event)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showDeleteConfirmation(event: com.securepass.vision.model.DetectionEvent) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("¿Eliminar de la nube?")
            .setMessage("Esta acción eliminará el registro permanentemente de la base de datos global.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteEventFromCloud(event.id.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteEventFromCloud(eventId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteDetection(eventId)
                if (response.isSuccessful) {
                    Toast.makeText(this@GlobalHistoryActivity, "Eliminado con éxito", Toast.LENGTH_SHORT).show()
                    loadGlobalData() // Recargar la lista
                } else {
                    Toast.makeText(this@GlobalHistoryActivity, "No se pudo eliminar de la nube", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GlobalHistoryActivity, "Error de conexión al eliminar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadGlobalData() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllDetections()
                if (response.isSuccessful) {
                    val events = response.body() ?: emptyList()
                    if (events.isEmpty()) {
                        recyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        adapter.updateEvents(events.sortedByDescending { it.timestamp })
                    }
                } else {
                    Toast.makeText(this@GlobalHistoryActivity, "Error al cargar datos de la nube", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GlobalHistory", "Error de red", e)
                Toast.makeText(this@GlobalHistoryActivity, "Fallo de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
