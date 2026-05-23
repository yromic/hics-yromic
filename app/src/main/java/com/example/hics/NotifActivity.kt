package com.example.hics

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotifActivity: AppCompatActivity() {
    private lateinit var back: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var notifList : MutableList<NotificationModel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notif)

        back         = findViewById(R.id.back)
        recyclerView = findViewById(R.id.recyclerView)
        notifList    = mutableListOf()
        adapter      = NotificationAdapter(notifList)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter       = adapter

        //dummy data notifikasii
        notifList.clear()
        val notif = NotificationModel(
            title = "Pompa Aktif",
            message = "Pompa air dinyalakan"
        )

        notifList.add(notif)
        adapter.notifyDataSetChanged()

        back.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}