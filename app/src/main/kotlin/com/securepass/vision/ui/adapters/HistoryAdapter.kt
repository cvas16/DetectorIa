package com.securepass.vision.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.securepass.vision.R
import com.securepass.vision.model.DetectionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var events: List<DetectionEvent>,
    private val onItemClick: (DetectionEvent) -> Unit,
    private val onDeleteClick: (DetectionEvent) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.event_icon)
        val title: TextView = view.findViewById(R.id.event_title)
        val timestamp: TextView = view.findViewById(R.id.event_timestamp)
        val staffInfo: TextView = view.findViewById(R.id.event_staff_info)
        val confidence: TextView = view.findViewById(R.id.event_confidence)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_event)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.title.text = event.objectLabel
        
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.timestamp.text = sdf.format(Date(event.timestamp))
        
        holder.staffInfo.text = "Staff: ${event.userName} | Evento: ${event.eventName}"
        
        holder.confidence.text = String.format(Locale.getDefault(), "%.0f%% Certeza", event.confidence * 100)
        
        holder.icon.setImageResource(R.drawable.ic_launcher_lion)

        holder.itemView.setOnClickListener { onItemClick(event) }
        holder.btnDelete.setOnClickListener { onDeleteClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<DetectionEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
