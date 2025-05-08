package com.example.doorbell_mobile.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.doorbell_mobile.R
import com.example.doorbell_mobile.models.CallStatus
import com.example.doorbell_mobile.models.RingHistory

class CallHistoryAdapter(private val callHistoryList: List<RingHistory>) :
    RecyclerView.Adapter<CallHistoryAdapter.CallHistoryViewHolder>() {

    inner class CallHistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val startTime: TextView = view.findViewById(R.id.tvStartTime)
        val endTime: TextView = view.findViewById(R.id.tvEndTime)
        val statusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        val container: View = view.findViewById(R.id.callHistoryItemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_history, parent, false)
        return CallHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallHistoryViewHolder, position: Int) {
        val callHistory = callHistoryList[position]
        holder.startTime.text = callHistory.startTime
        holder.endTime.text = callHistory.endTime

        when (callHistory.status) {
            CallStatus.ACCEPTED -> {
                holder.statusIcon.setImageResource(R.drawable.ic_accepted)
                holder.container.setBackgroundColor(Color.TRANSPARENT)
            }
            CallStatus.REJECTED -> {
                holder.statusIcon.setImageResource(R.drawable.ic_rejected)
                holder.container.setBackgroundColor(Color.TRANSPARENT)
            }
            CallStatus.MISSED -> {
                holder.statusIcon.setImageResource(R.drawable.ic_missed)
                holder.startTime.setTextColor(Color.RED)
                holder.endTime.setTextColor(Color.RED)
            }
        }
    }

    override fun getItemCount(): Int = callHistoryList.size
}