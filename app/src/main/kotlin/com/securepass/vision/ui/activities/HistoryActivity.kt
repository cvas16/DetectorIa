package com.securepass.vision.ui.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.securepass.vision.R
import com.securepass.vision.data.db.DatabaseHelper
import com.securepass.vision.ui.adapters.HistoryAdapter

class HistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: HistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        // Forzar NoActionBar antes de super.onCreate para evitar el crash
        theme.applyStyle(R.style.AppTheme, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<Toolbar>(R.id.history_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        dbHelper = DatabaseHelper(this)
        recyclerView = findViewById(R.id.history_recycler_view)
        emptyState = findViewById(R.id.empty_state_container)

        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            events = emptyList(),
            onItemClick = { event ->
                showEventDetails(event.objectLabel, event.confidence)
            },
            onDeleteClick = { event ->
                dbHelper.deleteDetection(event.id)
                loadData()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadData() {
        val events = dbHelper.getAllDetections()
        if (events.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            adapter.updateEvents(events)
        }
    }

    private fun showEventDetails(label: String, confidence: Float) {
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(label)
            .setMessage("Detección confirmada con un ${String.format(java.util.Locale.getDefault(), "%.1f%%", confidence * 100)} de confianza.")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_clear_history -> {
                dbHelper.clearHistory()
                loadData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
