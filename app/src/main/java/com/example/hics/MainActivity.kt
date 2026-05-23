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
import com.google.firebase.database.*
import kotlin.math.abs

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
    private var notificationRef: DatabaseReference? = null
    private var notificationListener: ValueEventListener? = null
    private var monitoredDeviceId: String? = null
    private val sensorNotifStates = mutableMapOf<String, SensorNotifState>()

    private enum class SensorStatus {
        NORMAL,
        HIGH,
        LOW
    }

    private data class SensorNotifState(
        val status: SensorStatus,
        val lastNotifiedValue: Double?,
        val hadAbnormal: Boolean
    )

    private data class SensorRule(
        val key: String,
        val name: String,
        val valueKey: String,
        val minKeys: List<String>,
        val maxKeys: List<String>
    )

    private val sensorRules = listOf(
        SensorRule("pH", "pH", "pH", listOf("phMin"), listOf("phMax")),
        SensorRule("ppm", "Nutrisi", "ppm", listOf("ppmMin"), listOf("ppmMax")),
        SensorRule(
            "waterTemp",
            "Suhu Air",
            "waterTemp",
            listOf("waterTempMin", "suhuAirMin"),
            listOf("waterTempMax", "suhuAirMax")
        ),
        SensorRule(
            "airTemp",
            "Suhu Udara",
            "airTemp",
            listOf("airTempMin", "suhuUdaraMin"),
            listOf("airTempMax", "suhuUdaraMax")
        ),
        SensorRule(
            "light",
            "Intensitas Cahaya",
            "light",
            listOf("lightMin", "intensitasMin"),
            listOf("lightMax", "intensitasMax")
        ),
        SensorRule(
            "waterLevel",
            "Ketersediaan Air",
            "waterLevel",
            listOf("waterLevelMin", "levelAirMin"),
            listOf("waterLevelMax", "levelAirMax")
        )
    )

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

                        startNotificationMonitor(id)

                    } else {
                        getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
                            .putString("deviceID", "")
                            .putInt("index", -1)
                            .apply()
                        stopNotificationMonitor()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

        updateBadge()

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

    override fun onResume() {
        super.onResume()
        updateBadge()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotificationMonitor()
    }

    private fun startNotificationMonitor(deviceId: String) {
        if (deviceId.isEmpty() || monitoredDeviceId == deviceId) return

        stopNotificationMonitor()
        monitoredDeviceId = deviceId

        val deviceRef = firebaseDatabase.getReference("Hics").child(deviceId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifEnabled = snapshot.child("setting")
                    .child("notifAlert")
                    .asBoolean()

                if (!notifEnabled) {
                    sensorNotifStates.clear()
                    updateBadge()
                    return
                }

                sensorRules.forEach { rule ->
                    evaluateSensorRule(
                        rule = rule,
                        dataSnapshot = snapshot.child("dataStream"),
                        settingSnapshot = snapshot.child("setting")
                    )
                }

                updateBadge()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        notificationRef = deviceRef
        notificationListener = listener
        deviceRef.addValueEventListener(listener)
    }

    private fun stopNotificationMonitor() {
        notificationListener?.let { listener ->
            notificationRef?.removeEventListener(listener)
        }
        notificationRef = null
        notificationListener = null
        monitoredDeviceId = null
        sensorNotifStates.clear()
    }

    private fun evaluateSensorRule(
        rule: SensorRule,
        dataSnapshot: DataSnapshot,
        settingSnapshot: DataSnapshot
    ) {
        val value = dataSnapshot.child(rule.valueKey).asDouble() ?: return
        val min = settingSnapshot.firstDouble(rule.minKeys)
        val max = settingSnapshot.firstDouble(rule.maxKeys)

        if (min == null && max == null) return

        val status = when {
            max != null && value > max -> SensorStatus.HIGH
            min != null && value < min -> SensorStatus.LOW
            else -> SensorStatus.NORMAL
        }

        val previous = sensorNotifStates[rule.key]

        when (status) {
            SensorStatus.HIGH,
            SensorStatus.LOW -> handleAbnormalSensor(rule, value, min, max, status, previous)
            SensorStatus.NORMAL -> handleNormalSensor(rule, value, min, max, previous)
        }
    }

    private fun handleAbnormalSensor(
        rule: SensorRule,
        value: Double,
        min: Double?,
        max: Double?,
        status: SensorStatus,
        previous: SensorNotifState?
    ) {
        val shouldNotify = previous == null ||
            previous.status != status ||
            previous.lastNotifiedValue == null ||
            abs(value - previous.lastNotifiedValue) > VALUE_EPSILON

        if (shouldNotify) {
            val title = when (status) {
                SensorStatus.HIGH -> "${rule.name} terlalu tinggi"
                SensorStatus.LOW -> "${rule.name} terlalu rendah"
                SensorStatus.NORMAL -> return
            }

            NotificationStore.add(
                NotificationModel(
                    title = title,
                    message = buildSensorMessage(rule.name, value, min, max)
                )
            )
        }

        sensorNotifStates[rule.key] = SensorNotifState(
            status = status,
            lastNotifiedValue = if (shouldNotify) value else previous?.lastNotifiedValue,
            hadAbnormal = true
        )
    }

    private fun handleNormalSensor(
        rule: SensorRule,
        value: Double,
        min: Double?,
        max: Double?,
        previous: SensorNotifState?
    ) {
        if (previous?.hadAbnormal == true && previous.status != SensorStatus.NORMAL) {
            NotificationStore.add(
                NotificationModel(
                    title = "${rule.name} kembali normal",
                    message = buildSensorMessage(rule.name, value, min, max)
                )
            )
        }

        sensorNotifStates[rule.key] = SensorNotifState(
            status = SensorStatus.NORMAL,
            lastNotifiedValue = null,
            hadAbnormal = false
        )
    }

    private fun buildSensorMessage(
        name: String,
        value: Double,
        min: Double?,
        max: Double?
    ): String {
        val rangeText = when {
            min != null && max != null -> "range $min - $max"
            min != null -> "minimal $min"
            max != null -> "maksimal $max"
            else -> "tanpa batas"
        }

        return "$name saat ini ${formatValue(value)} ($rangeText)"
    }

    private fun updateBadge() {
        val count = NotificationStore.unreadCount
        if (count == 0) {
            badgeNotif.visibility = View.GONE
        } else {
            badgeNotif.visibility = View.VISIBLE
            badgeNotif.text = count.toString()
        }
    }

    private fun DataSnapshot.asDouble(): Double? {
        return when (val rawValue = value) {
            is Number -> rawValue.toDouble()
            is String -> rawValue.toDoubleOrNull()
            else -> null
        }
    }

    private fun DataSnapshot.asBoolean(): Boolean {
        return when (val rawValue = value) {
            is Boolean -> rawValue
            is String -> rawValue.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun DataSnapshot.firstDouble(keys: List<String>): Double? {
        keys.forEach { key ->
            child(key).asDouble()?.let { return it }
        }
        return null
    }

    private fun formatValue(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
    }

    private companion object {
        const val VALUE_EPSILON = 0.01
    }
}
