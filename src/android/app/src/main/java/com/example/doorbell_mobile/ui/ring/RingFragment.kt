package com.example.doorbell_mobile.ui.ring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import com.example.doorbell_mobile.R
import com.example.doorbell_mobile.adapters.CallHistoryAdapter
import com.example.doorbell_mobile.models.CallStatus
import com.example.doorbell_mobile.models.RingHistory
import java.time.Duration

class RingFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CallHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_ring, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Thiết lập RecyclerView và Adapter
        recyclerView = view.findViewById(R.id.recyclerViewCallHistory)
        val callHistoryList = listOf(
            RingHistory("10:00 AM", "10:05 AM", CallStatus.ACCEPTED),
            RingHistory("11:00 AM", "11:02 AM", CallStatus.REJECTED),
            RingHistory("12:00 PM", "12:10 PM", CallStatus.MISSED)
        )

        val tvEmptyMessage = view.findViewById<TextView>(R.id.tvEmptyMessage)
        // Check if the list is empty
        if (callHistoryList.isEmpty()) {
            tvEmptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Set up RecyclerView and Adapter
            adapter = CallHistoryAdapter(callHistoryList)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }

    }
}
