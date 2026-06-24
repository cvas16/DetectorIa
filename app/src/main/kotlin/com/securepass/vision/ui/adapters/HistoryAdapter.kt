package com.securepass.vision.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.securepass.vision.R
import com.securepass.vision.model.DetectionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var events: List<DetectionEvent> = emptyList(),
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
        
        holder.staffInfo.text = holder.itemView.context.getString(
            R.string.history_staff_info_format, 
            event.userName, 
            event.eventName
        )
        
        holder.confidence.text = holder.itemView.context.getString(
            R.string.history_confidence_label,
            (event.confidence * 100).toInt()
        )
        
        holder.icon.setImageResource(R.drawable.ic_launcher_lion)

        holder.itemView.setOnClickListener { onItemClick(event) }
        holder.btnDelete.setOnClickListener { onDeleteClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<DetectionEvent>) {
        val diffCallback = EventDiffCallback(events, newEvents)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        events = newEvents
        diffResult.dispatchUpdatesTo(this)
    }

    class EventDiffCallback(
        private val oldList: List<DetectionEvent>,
        private val newList: List<DetectionEvent>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
