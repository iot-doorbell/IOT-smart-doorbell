package com.example.doorbell_mobile.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.doorbell_mobile.R
import com.example.doorbell_mobile.models.ChatMessage

class ChatAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.ChatVH>() {

    inner class ChatVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMsg)
        fun bind(msg: ChatMessage) {
            tvMessage.text = msg.text
            val params = tvMessage.layoutParams as FrameLayout.LayoutParams
            if (msg.isMine) {
                params.gravity = Gravity.END
                tvMessage.setBackgroundResource(R.drawable.bg_my_msg)
            } else {
                params.gravity = Gravity.START
                tvMessage.setBackgroundResource(R.drawable.bg_other_msg)
            }
            tvMessage.layoutParams = params
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatVH(view)
    }

    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }
}
