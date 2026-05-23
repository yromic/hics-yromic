@file:Suppress("DEPRECATION")

package com.example.hics

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.database.*

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
    private var dataStreamRef: DatabaseReference? = null
    private var settingRef: DatabaseReference? = null
    private var dataStreamListener: ValueEventListener? = null
    private var settingListener: ValueEventListener? = null
    private var monitoredDeviceId: String? = null
    private var latestDataStreamSnapshot: DataSnapshot? = null
    private var latestSettingSnapshot: DataSnapshot? = null
    private var notificationMonitorStarted = false

    private enum class SensorStatus {
        NORMAL,
        HIGH,
        LOW
    }

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

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

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

                        val firebaseDeviceId = snapshot.child("id").value?.toString()?.trim().orEmpty()
                        val id = firebaseDeviceId.ifEmpty { DEFAULT_DEVICE_ID }

                        Log.d(TAG, "Firebase user deviceID='$firebaseDeviceId', deviceID dipakai='$id'")

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
                    Log.d(TAG, "User listener error: ${error.message}", error.toException())
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
        val resolvedDeviceId = deviceId.trim().ifEmpty { DEFAULT_DEVICE_ID }
        Log.d(
            TAG,
            "startNotificationMonitor deviceID='$resolvedDeviceId', " +
                "monitorStarted=$notificationMonitorStarted, monitoredDeviceId='$monitoredDeviceId'"
        )

        if (notificationMonitorStarted && monitoredDeviceId == resolvedDeviceId) {
            Log.d(TAG, "Monitor sudah started untuk deviceID='$resolvedDeviceId', listener tidak dipasang ulang")
            return
        }

        if (notificationMonitorStarted && monitoredDeviceId != resolvedDeviceId) {
            Log.d(TAG, "deviceID berubah dari '$monitoredDeviceId' ke '$resolvedDeviceId', remove listener lama")
            stopNotificationMonitor()
        }

        monitoredDeviceId = resolvedDeviceId

        val deviceRef = firebaseDatabase.getReference("Hics").child(resolvedDeviceId)
        val dataRef = deviceRef.child("dataStream")
        val settingsRef = deviceRef.child("setting")

        val dataListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                latestDataStreamSnapshot = snapshot
                Log.d(TAG, "dataStream listener aktif: Hics/$resolvedDeviceId/dataStream")
                evaluateNotifications()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(TAG, "dataStream listener error: ${error.message}", error.toException())
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val settingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                latestSettingSnapshot = snapshot
                Log.d(TAG, "setting listener aktif: Hics/$resolvedDeviceId/setting")
                logSettingSnapshot(snapshot)
                evaluateNotifications()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(TAG, "setting listener error: ${error.message}", error.toException())
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        dataStreamRef = dataRef
        settingRef = settingsRef
        dataStreamListener = dataListener
        settingListener = settingsListener

        dataRef.addValueEventListener(dataListener)
        settingsRef.addValueEventListener(settingsListener)
        notificationMonitorStarted = true
        Log.d(TAG, "Monitor notification started untuk Hics/$resolvedDeviceId")
    }

    private fun stopNotificationMonitor() {
        dataStreamListener?.let { listener ->
            dataStreamRef?.removeEventListener(listener)
        }
        settingListener?.let { listener ->
            settingRef?.removeEventListener(listener)
        }
        dataStreamRef = null
        settingRef = null
        dataStreamListener = null
        settingListener = null
        monitoredDeviceId = null
        latestDataStreamSnapshot = null
        latestSettingSnapshot = null
        notificationMonitorStarted = false
        Log.d(TAG, "Monitor notification stopped")
    }

    private fun evaluateNotifications() {
        val deviceId = monitoredDeviceId ?: DEFAULT_DEVICE_ID
        val dataSnapshot = latestDataStreamSnapshot
        val settingSnapshot = latestSettingSnapshot

        Log.d(TAG, "evaluateNotifications deviceID='$deviceId'")

        if (dataSnapshot == null || settingSnapshot == null) {
            Log.d(TAG, "Notifikasi belum dibuat: dataStream atau setting belum siap")
            return
        }

        val notifEnabled = readBooleanFlexible(settingSnapshot, "notifAlert")
        Log.d(TAG, "notifAlert=$notifEnabled")

        if (!notifEnabled) {
            Log.d(TAG, "Notifikasi tidak dibuat: notifAlert=false")
            updateBadge()
            return
        }

        sensorRules.forEach { rule ->
            evaluateSensorRule(
                rule = rule,
                dataSnapshot = dataSnapshot,
                settingSnapshot = settingSnapshot
            )
        }

        updateBadge()
    }

    private fun evaluateSensorRule(
        rule: SensorRule,
        dataSnapshot: DataSnapshot,
        settingSnapshot: DataSnapshot
    ) {
        val value = readDoubleFlexible(dataSnapshot, rule.valueKey) ?: run {
            Log.d(TAG, "Notifikasi ${rule.name} tidak dibuat: nilai ${rule.valueKey} kosong/tidak valid")
            return
        }
        val min = settingSnapshot.firstDouble(rule.minKeys)
        val max = settingSnapshot.firstDouble(rule.maxKeys)

        if (min == null && max == null) {
            Log.d(TAG, "Notifikasi ${rule.name} tidak dibuat: threshold min/max kosong/tidak valid")
            return
        }

        val status = when {
            max != null && value > max -> SensorStatus.HIGH
            min != null && value < min -> SensorStatus.LOW
            else -> SensorStatus.NORMAL
        }

        if (rule.key == "pH") {
            Log.d(TAG, "pH=$value, phMin=$min, phMax=$max")
            Log.d(TAG, "status pH=$status")
        } else {
            Log.d(TAG, "${rule.name}: value=$value, min=$min, max=$max, status=$status")
        }

        val previousStatus = getLastStatus(rule.key)
        val currentStatus = status.name
        Log.d(TAG, "${rule.name}: previousStatus SharedPreferences=$previousStatus, currentStatus=$currentStatus")

        when (status) {
            SensorStatus.HIGH,
            SensorStatus.LOW -> handleAbnormalSensor(rule, value, min, max, status, previousStatus)
            SensorStatus.NORMAL -> handleNormalSensor(rule, value, min, max, previousStatus)
        }
    }

    private fun handleAbnormalSensor(
        rule: SensorRule,
        value: Double,
        min: Double?,
        max: Double?,
        status: SensorStatus,
        previousStatus: String
    ) {
        if (previousStatus == status.name) {
            saveHasBeenAbnormal(rule.key, true)
            Log.d(TAG, "Notifikasi diskip karena status sama: ${rule.name} tetap $status")
            return
        }

        val title = when (status) {
            SensorStatus.HIGH -> "${rule.name} terlalu tinggi"
            SensorStatus.LOW -> "${rule.name} terlalu rendah"
            SensorStatus.NORMAL -> return
        }

        Log.d(TAG, "Notifikasi dibuat karena status berubah: ${rule.name} $previousStatus -> ${status.name}")
        publishNotification(
            parameter = rule.key,
            status = status.name,
            notification = NotificationModel(
                title = title,
                message = buildSensorMessage(rule.name, value, min, max)
            )
        )

        saveLastStatus(rule.key, status.name)
        saveHasBeenAbnormal(rule.key, true)
    }

    private fun handleNormalSensor(
        rule: SensorRule,
        value: Double,
        min: Double?,
        max: Double?,
        previousStatus: String
    ) {
        val hasBeenAbnormal = getHasBeenAbnormal(rule.key)

        if (previousStatus == SensorStatus.NORMAL.name) {
            saveHasBeenAbnormal(rule.key, false)
            Log.d(TAG, "Notifikasi diskip karena status sama: ${rule.name} tetap NORMAL")
            return
        }

        if (hasBeenAbnormal && previousStatus in ABNORMAL_STATUSES) {
            Log.d(TAG, "Notifikasi dibuat karena status berubah: ${rule.name} $previousStatus -> NORMAL")
            publishNotification(
                parameter = rule.key,
                status = SensorStatus.NORMAL.name,
                notification = NotificationModel(
                    title = "${rule.name} kembali normal",
                    message = buildSensorMessage(rule.name, value, min, max)
                )
            )
        } else {
            Log.d(
                TAG,
                "Notifikasi tidak dibuat: ${rule.name} NORMAL, previousStatus=$previousStatus, " +
                    "hasBeenAbnormal=$hasBeenAbnormal"
            )
        }

        saveLastStatus(rule.key, SensorStatus.NORMAL.name)
        saveHasBeenAbnormal(rule.key, false)
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

    private fun publishNotification(
        parameter: String,
        status: String,
        notification: NotificationModel
    ) {
        val deviceId = monitoredDeviceId ?: DEFAULT_DEVICE_ID
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "title" to notification.title,
            "message" to notification.message,
            "read" to false,
            "time" to now
        )

        Log.d(
            TAG,
            "Notifikasi dibuat: ${notification.title} - ${notification.message}, " +
                "lastNotificationTime=${getLastNotificationTime(parameter, status)}"
        )

        firebaseDatabase.getReference("Hics")
            .child(deviceId)
            .child("notifications")
            .push()
            .setValue(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Notifikasi Firebase tersimpan dengan push() di Hics/$deviceId/notifications")
            }
            .addOnFailureListener { error ->
                Log.d(TAG, "Gagal menyimpan notifikasi Firebase: ${error.message}", error)
            }

        NotificationStore.add(notification)
        saveLastNotificationTime(parameter, status, now)
        showLocalNotification(notification)
    }

    private fun showLocalNotification(notification: NotificationModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Local notification tidak dibuat: permission POST_NOTIFICATIONS belum granted")
            return
        }

        val intent = Intent(this, NotifActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), builder.build())
        Log.d(TAG, "Local notification status bar dibuat: ${notification.title}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "HICS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Realtime alert sensor HICS"
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        Log.d(TAG, "Notification channel siap: $NOTIFICATION_CHANNEL_ID")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                POST_NOTIFICATIONS_REQUEST_CODE
            )
            Log.d(TAG, "Request permission POST_NOTIFICATIONS")
        }
    }

    private fun logSettingSnapshot(snapshot: DataSnapshot) {
        Log.d(
            TAG,
            "setting notifAlert=${readBooleanFlexible(snapshot, "notifAlert")}, " +
                "phMin=${readDoubleFlexible(snapshot, "phMin")}, " +
                "phMax=${readDoubleFlexible(snapshot, "phMax")}"
        )
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

    private fun getLastStatus(parameter: String): String {
        return notificationStatePrefs().getString(stateKey(parameter, "last_status"), "").orEmpty()
    }

    private fun saveLastStatus(parameter: String, status: String) {
        notificationStatePrefs()
            .edit()
            .putString(stateKey(parameter, "last_status"), status)
            .apply()
        Log.d(TAG, "saveLastStatus parameter=$parameter status=$status")
    }

    private fun getLastNotificationTime(parameter: String, status: String): Long {
        return notificationStatePrefs().getLong(stateKey(parameter, "last_notification_time_$status"), 0L)
    }

    private fun saveLastNotificationTime(parameter: String, status: String, time: Long) {
        notificationStatePrefs()
            .edit()
            .putLong(stateKey(parameter, "last_notification_time_$status"), time)
            .apply()
        Log.d(TAG, "saveLastNotificationTime parameter=$parameter status=$status time=$time")
    }

    private fun getHasBeenAbnormal(parameter: String): Boolean {
        return notificationStatePrefs().getBoolean(stateKey(parameter, "has_been_abnormal"), false)
    }

    private fun saveHasBeenAbnormal(parameter: String, value: Boolean) {
        notificationStatePrefs()
            .edit()
            .putBoolean(stateKey(parameter, "has_been_abnormal"), value)
            .apply()
        Log.d(TAG, "saveHasBeenAbnormal parameter=$parameter value=$value")
    }

    private fun notificationStatePrefs() = getSharedPreferences(NOTIFICATION_STATE_PREF, MODE_PRIVATE)

    private fun stateKey(parameter: String, suffix: String): String {
        val deviceId = monitoredDeviceId ?: DEFAULT_DEVICE_ID
        return "$deviceId.$parameter.$suffix"
    }

    private fun readBooleanFlexible(snapshot: DataSnapshot, key: String): Boolean {
        return when (val rawValue = snapshot.child(key).value) {
            is Boolean -> rawValue
            else -> rawValue?.toString().equals("true", ignoreCase = true)
        }
    }

    private fun readDoubleFlexible(snapshot: DataSnapshot, key: String): Double? {
        return snapshot.child(key).value?.toString()?.toDoubleOrNull()
    }

    private fun DataSnapshot.firstDouble(keys: List<String>): Double? {
        keys.forEach { key ->
            readDoubleFlexible(this, key)?.let { return it }
        }
        return null
    }

    private fun formatValue(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
    }

    private companion object {
        const val TAG = "HicsNotification"
        const val DEFAULT_DEVICE_ID = "hics2026"
        const val NOTIFICATION_STATE_PREF = "HICS_NOTIFICATION_STATE"
        const val NOTIFICATION_CHANNEL_ID = "hics_realtime_alerts"
        const val POST_NOTIFICATIONS_REQUEST_CODE = 2026
        val ABNORMAL_STATUSES = setOf(SensorStatus.HIGH.name, SensorStatus.LOW.name)
    }
}
