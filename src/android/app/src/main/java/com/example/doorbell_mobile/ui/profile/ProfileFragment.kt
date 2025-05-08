package com.example.doorbell_mobile.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.doorbell_mobile.R
import com.example.doorbell_mobile.models.Profile

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Mock profile data
        val profile = Profile(
            id = "12345",
            username = "John Doe",
            avatarUrl = "https://example.com/avatar.jpg",
            email = "johndoe@example.com"
        )

        // Bind views
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val tvId: TextView = view.findViewById(R.id.tvId)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvEmail: TextView = view.findViewById(R.id.tvEmail)

        // Set profile data
        tvId.text = profile.id
        tvUsername.text = profile.username
        tvEmail.text = profile.email
        Glide.with(this)
            .load(profile.avatarUrl)
            .placeholder(R.drawable.avatar)
            .circleCrop()
            .into(ivAvatar)

        return view
    }
}