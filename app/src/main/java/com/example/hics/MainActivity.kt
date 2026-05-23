@file:Suppress("DEPRECATION")

package com.example.hics

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var homeBt: LinearLayout
    private lateinit var chartBt: LinearLayout
    private lateinit var settingBt: LinearLayout
    private lateinit var imgHome: ImageView
    private lateinit var tvHome: TextView
    private lateinit var imgChart: ImageView
    private lateinit var tvChart: TextView
    private lateinit var imgSetting: ImageView
    private lateinit var tvSetting: TextView
    private lateinit var badgeNotif: TextView
    private lateinit var btnNotif: ImageView

    private var indexAcc: Int = -1

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)

        // animasi fade in
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(300).start()

        window.statusBarColor = getColor(R.color.hijau_start)
        window.navigationBarColor = getColor(R.color.white)

        // init view
        homeBt = findViewById(R.id.homeBt)
        chartBt = findViewById(R.id.chartBt)
        settingBt = findViewById(R.id.settingBt)
        imgHome = findViewById(R.id.imgHome)
        tvHome = findViewById(R.id.tvHome)
        imgChart = findViewById(R.id.imgChart)
        tvChart = findViewById(R.id.tvChart)
        imgSetting = findViewById(R.id.imgSetting)
        tvSetting = findViewById(R.id.tvSetting)
        badgeNotif = findViewById(R.id.badge_notif)
        btnNotif = findViewById(R.id.btn_notif)

        //  AMBIL INDEX DARI SESSION
        val accPref = getSharedPreferences("ACCOUNT", MODE_PRIVATE)
        indexAcc = accPref.getInt("index", -1)
        //cek pengambilan index
        //Toast.makeText(this, "Index di Main: $indexAcc", Toast.LENGTH_LONG).show()

        //  VALIDASI SESSION
        if (indexAcc == -1) {
            Toast.makeText(this, "Session tidak ditemukan, silakan login ulang", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val accFirebase = firebaseDatabase.getReference("User")

        Log.d("MainActivity", "indexAcc: $indexAcc")

        // AMBIL DATA USER BERDASARKAN INDEX
        accFirebase.child("user_$indexAcc")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {

                        val id = snapshot.child("id").value?.toString() ?: ""

                        getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
                            .putString("deviceID", id)
                            .putInt("index", indexAcc)
                            .apply()

                    } else {
                        getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
                            .putString("deviceID", "")
                            .putInt("index", -1)
                            .apply()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

        //  NOTIF DUMMY
        lifecycleScope.launch {
            while (true) {
                val notif = (0..20).random()

                if (notif == 0) {
                    badgeNotif.visibility = View.GONE
                } else {
                    badgeNotif.visibility = View.VISIBLE
                    badgeNotif.text = notif.toString()
                }

                delay(2000)
            }
        }

        btnNotif.setOnClickListener {
            startActivity(Intent(this, NotifActivity::class.java))
        }

        // DEFAULT FRAGMENT
        if (savedInstanceState == null) {
            currentFragment = HomeFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.mainFragment, currentFragment!!)
                .commit()
        }

        homeBt.setOnClickListener {
            replaceFragment(HomeFragment(), 0)
        }

        chartBt.setOnClickListener {
            replaceFragment(ChartFragment(), 1)
        }

        settingBt.setOnClickListener {
            replaceFragment(SettingFragment(), 2)
        }
    }

    private fun replaceFragment(fragment: Fragment, mode: Int) {
        val transaction = supportFragmentManager.beginTransaction()

        when (mode) {
            0 -> {
                imgHome.setImageResource(R.drawable.home_green)
                tvHome.setTextColor(resources.getColor(R.color.hijau))

                imgChart.setImageResource(R.drawable.chart_abu)
                tvChart.setTextColor(resources.getColor(R.color.abu))

                imgSetting.setImageResource(R.drawable.setting_grey)
                tvSetting.setTextColor(resources.getColor(R.color.abu))
            }

            1 -> {
                imgHome.setImageResource(R.drawable.home_grey)
                tvHome.setTextColor(resources.getColor(R.color.abu))

                imgChart.setImageResource(R.drawable.chart_hijau)
                tvChart.setTextColor(resources.getColor(R.color.hijau))

                imgSetting.setImageResource(R.drawable.setting_grey)
                tvSetting.setTextColor(resources.getColor(R.color.abu))
            }

            2 -> {
                imgHome.setImageResource(R.drawable.home_grey)
                tvHome.setTextColor(resources.getColor(R.color.abu))

                imgChart.setImageResource(R.drawable.chart_abu)
                tvChart.setTextColor(resources.getColor(R.color.abu))

                imgSetting.setImageResource(R.drawable.setting_green)
                tvSetting.setTextColor(resources.getColor(R.color.hijau))
            }
        }

        transaction.replace(R.id.mainFragment, fragment)
        transaction.commit()

        currentFragment = fragment
    }
}