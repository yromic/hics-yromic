package com.example.hics

import android.content.Context.MODE_PRIVATE
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.example.hics.databinding.FragmentChartBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.google.firebase.database.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private var deviceID: String? = ""

    // =========================================================
    // BMKG
    // =========================================================

    private val client = OkHttpClient()

    // GANTI DENGAN KODE WILAYAH BMKG
    // contoh Semarang = 33.74.06.1001
    // TEGAL
    private val bmkgAreaCode = "33.74.06.1001"

    private val dataTypeList = listOf(
        "Nutrisi",
        "Suhu Air",
        "Suhu Udara",
        "pH",
        "Intensitas Cahaya"
    )

    private val periodList = listOf(
        "Daily",
        "Weekly",
        "Monthly",
        "Yearly"
    )

    private var selectedDataType = "Nutrisi"
    private var selectedPeriod = "Daily"

    private val colorTeal = Color.parseColor("#2BBFA4")

    private val chartEntries = ArrayList<Entry>()
    private val chartLabels = ArrayList<String>()

    private var historyListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner) {
                requireActivity().finish()
            }

        val accPref = requireActivity()
            .getSharedPreferences("ACCOUNT", MODE_PRIVATE)

        deviceID = accPref.getString("deviceID", "")

        Log.d("ChartFragment", "Device ID : $deviceID")

        setupSpinners()

        setupChart()

        // =====================================================
        // GANTI DUMMY MENJADI BMKG REALTIME
        // =====================================================

        loadWeatherFromBMKG()

        loadRealtimeChart()
    }

    // =========================================================
    // WEATHER DATA
    // =========================================================

    data class WeatherData(
        val time: String,
        val temp: String,
        val wind: String,
        val icon: Int
    )

    // =========================================================
// BMKG WEATHER
// =========================================================

    private fun loadWeatherFromBMKG() {

        val url =
            "https://api.bmkg.go.id/publik/prakiraan-cuaca?adm4=$bmkgAreaCode"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request)
            .enqueue(object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {

                    Log.e(
                        "BMKG",
                        "Failed : ${e.message}"
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {

                    try {

                        val body =
                            response.body?.string()

                        Log.d(
                            "BMKG_RESPONSE",
                            body ?: "NULL"
                        )

                        val weatherList =
                            parseBMKGWeather(body)

                        requireActivity().runOnUiThread {

                            showWeather(weatherList)
                        }

                    } catch (e: Exception) {

                        Log.e(
                            "BMKG",
                            "Parse Error : ${e.message}"
                        )
                    }
                }
            })
    }

    // =========================================================
    // PARSE BMKG JSON
    // =========================================================

    private fun parseBMKGWeather(
        json: String?
    ): List<WeatherData> {

        val result = mutableListOf<WeatherData>()

        if (json.isNullOrEmpty())
            return result

        try {

            val root = JSONObject(json)

            val dataArray =
                root.getJSONArray("data")

            if (dataArray.length() == 0)
                return result

            val firstData =
                dataArray.getJSONObject(0)

            val cuacaArray =
                firstData.getJSONArray("cuaca")

            if (cuacaArray.length() == 0)
                return result

            val weatherPerDay =
                cuacaArray.getJSONArray(0)

            val currentTime =
                System.currentTimeMillis()

            val weatherTempList =
                mutableListOf<Pair<Long, WeatherData>>()

            for (i in 0 until weatherPerDay.length()) {

                val item =
                    weatherPerDay.getJSONObject(i)

                val datetime =
                    item.optString("local_datetime")

                val suhu =
                    item.optString("t")

                val wind =
                    item.optString("ws")

                val desc =
                    item.optString("weather_desc")

                val icon =
                    getWeatherIcon(desc)

                try {

                    // FORMAT BMKG:
                    // 2025-05-14 23:00:00

                    val parts =
                        datetime.split(" ")

                    if (parts.size < 2)
                        continue

                    val datePart =
                        parts[0].split("-")

                    val timePart =
                        parts[1].split(":")

                    val calendar =
                        Calendar.getInstance()

                    calendar.set(
                        datePart[0].toInt(),
                        datePart[1].toInt() - 1,
                        datePart[2].toInt(),
                        timePart[0].toInt(),
                        timePart[1].toInt()
                    )

                    calendar.set(Calendar.SECOND, 0)

                    val itemTime =
                        calendar.timeInMillis

                    // hanya ambil waktu sekarang & masa depan
                    if (itemTime >= currentTime) {

                        val timeLabel =
                            String.format(
                                "%02d:%02d",
                                timePart[0].toInt(),
                                timePart[1].toInt()
                            )

                        weatherTempList.add(

                            Pair(
                                itemTime,
                                WeatherData(
                                    time = timeLabel,
                                    temp = "$suhu°",
                                    wind = "$wind km/h",
                                    icon = icon
                                )
                            )
                        )
                    }

                } catch (e: Exception) {

                    Log.e(
                        "BMKG_PARSE_ITEM",
                        e.message ?: "Parse item error"
                    )
                }
            }

            // sort waktu terdekat
            val sorted =
                weatherTempList.sortedBy { it.first }

            sorted.take(6)
                .forEachIndexed { index, pair ->

                    val data =
                        pair.second

                    result.add(

                        data.copy(
                            time =
                                if (index == 0)
                                    "Now"
                                else
                                    data.time
                        )
                    )
                }

        } catch (e: Exception) {

            Log.e(
                "BMKG_PARSE",
                e.message ?: "Unknown Error"
            )
        }

        return result
    }

    // =========================================================
    // WEATHER ICON
    // =========================================================

    private fun getWeatherIcon(
        desc: String
    ): Int {

        val weather =
            desc.lowercase(Locale.getDefault())

        return when {

            weather.contains("hujan") ->
                R.drawable.ic_weather_rain

            weather.contains("rain") ->
                R.drawable.ic_weather_rain

            weather.contains("cloud") ->
                R.drawable.ic_weather_cloudy

            weather.contains("berawan") ->
                R.drawable.ic_weather_cloudy

            weather.contains("cerah") ->
                R.drawable.ic_weather_sunny

            else ->
                R.drawable.ic_weather_cloudy
        }
    }

    // =========================================================
    // SHOW WEATHER
    // =========================================================

    private fun showWeather(
        weatherList: List<WeatherData>
    ) {
        Log.d("WEATHER_SIZE", weatherList.size.toString())
        val weatherContainer: LinearLayout =
            binding.layoutWeatherContainer

        weatherContainer.removeAllViews()

        weatherList.forEach { item ->

            val weatherView = layoutInflater.inflate(
                R.layout.item_weather,
                weatherContainer,
                false
            )

            weatherView.findViewById<TextView>(
                R.id.tvTime
            ).text = item.time

            weatherView.findViewById<TextView>(
                R.id.tvTemp
            ).text = item.temp

            weatherView.findViewById<TextView>(
                R.id.tvWind
            ).text = item.wind

            weatherView.findViewById<ImageView>(
                R.id.imgWeather
            ).setImageResource(item.icon)

            weatherContainer.addView(weatherView)
        }
    }


    // =========================================================
    // Spinner
    // =========================================================

    private fun makeAdapter(
        items: List<String>,
        selectedColor: Int
    ): ArrayAdapter<String> {

        return object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_spinner_dropdown,
            items
        ) {

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {

                val v = super.getView(
                    position,
                    convertView,
                    parent
                )

                (v as TextView).apply {

                    text = items[position]

                    setTextColor(selectedColor)

                    setBackgroundColor(Color.TRANSPARENT)

                    textSize = 13f

                    setPadding(0, 0, 0, 0)
                }

                return v
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {

                val v = super.getDropDownView(
                    position,
                    convertView,
                    parent
                )

                (v as TextView).apply {

                    text = items[position]

                    setTextColor(
                        Color.parseColor("#2D3A4A")
                    )

                    textSize = 13f

                    setBackgroundResource(
                        R.drawable.bg_dropdown_item
                    )
                }

                return v
            }

        }.also {

            it.setDropDownViewResource(
                R.layout.item_spinner_dropdown
            )
        }
    }

    private fun setupSpinners() {

        binding.spinnerDataType.background = null

        binding.spinnerDataType.adapter =
            makeAdapter(
                dataTypeList,
                Color.WHITE
            )

        binding.spinnerDataType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    selectedDataType =
                        dataTypeList[position]

                    loadRealtimeChart()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        binding.layoutSpinnerData.setOnClickListener {

            binding.spinnerDataType.performClick()
        }

        binding.spinnerPeriod.adapter =
            makeAdapter(
                periodList,
                Color.parseColor("#2D3A4A")
            )

        binding.spinnerPeriod.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    selectedPeriod =
                        periodList[position]

                    loadRealtimeChart()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }

        binding.layoutSpinnerPeriod.setOnClickListener {

            binding.spinnerPeriod.performClick()
        }
    }

    // =========================================================
    // Setup Chart
    // =========================================================

    private fun setupChart() {

        binding.lineChart.apply {

            description.isEnabled = false

            legend.isEnabled = false

            setTouchEnabled(true)

            isDragEnabled = true

            isScaleXEnabled = false
            isScaleYEnabled = false

            setPinchZoom(false)

            setDrawGridBackground(false)

            setBackgroundColor(Color.TRANSPARENT)

            extraBottomOffset = 8f
            extraTopOffset = 16f

            xAxis.apply {

                position = XAxis.XAxisPosition.BOTTOM

                setDrawGridLines(false)

                setDrawAxisLine(false)

                textColor = Color.parseColor("#8A9BB0")

                textSize = 11f

                granularity = 1f

                isGranularityEnabled = true
            }

            axisLeft.apply {

                setDrawGridLines(true)

                gridColor = Color.parseColor("#E8EEF4")

                gridLineWidth = 0.8f

                enableGridDashedLine(
                    6f,
                    4f,
                    0f
                )

                setDrawAxisLine(false)

                textColor = Color.parseColor("#8A9BB0")

                textSize = 11f

                setLabelCount(6, true)

                axisMinimum = 0f
            }

            axisRight.isEnabled = false

            val mv = CustomMarkerView(
                requireContext(),
                R.layout.marker_view_chart
            )

            mv.chartView = this

            marker = mv
        }
    }

    // =========================================================
    // Load Firebase Realtime
    // =========================================================

    private fun loadRealtimeChart() {

        if (deviceID.isNullOrEmpty()) return

        val historyRef = firebaseDatabase
            .getReference("Hics")
            .child(deviceID!!)
            .child("history")

        historyListener?.let {

            historyRef.removeEventListener(it)
        }

        historyListener =
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    chartEntries.clear()
                    chartLabels.clear()

                    val tempList =
                        mutableListOf<Pair<String, Float>>()

                    for (yearSnap in snapshot.children) {

                        for (monthSnap in yearSnap.children) {

                            for (daySnap in monthSnap.children) {

                                for (timeSnap in daySnap.children) {

                                    val value =
                                        when (selectedDataType) {

                                            "Nutrisi" ->
                                                timeSnap.child("ppm")
                                                    .getValue(Float::class.java)

                                            "Suhu Air" ->
                                                timeSnap.child("waterTemp")
                                                    .getValue(Float::class.java)

                                            "Suhu Udara" ->
                                                timeSnap.child("airTemp")
                                                    .getValue(Float::class.java)

                                            "pH" ->
                                                timeSnap.child("pH")
                                                    .getValue(Float::class.java)

                                            "Intensitas Cahaya" ->
                                                timeSnap.child("light")
                                                    .getValue(Float::class.java)

                                            else -> null
                                        }

                                    if (value != null) {

                                        val label =
                                            when (selectedPeriod) {

                                                "Daily" ->
                                                    timeSnap.key ?: ""

                                                "Weekly" ->
                                                    daySnap.key ?: ""

                                                "Monthly" ->
                                                    "${daySnap.key}/${monthSnap.key}"

                                                "Yearly" ->
                                                    monthSnap.key ?: ""

                                                else -> ""
                                            }

                                        tempList.add(
                                            Pair(label, value)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val filteredList =
                        when (selectedPeriod) {

                            "Daily" ->
                                tempList.takeLast(24)

                            "Weekly" ->
                                tempList.takeLast(7)

                            "Monthly" ->
                                tempList.takeLast(30)

                            "Yearly" ->
                                tempList.takeLast(12)

                            else -> tempList
                        }

                    filteredList.forEachIndexed { index, pair ->

                        chartEntries.add(
                            Entry(
                                index.toFloat(),
                                pair.second
                            )
                        )

                        chartLabels.add(pair.first)
                    }

                    updateChart()
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    Log.e(
                        "ChartFragment",
                        error.message
                    )
                }
            }

        historyRef.addValueEventListener(
            historyListener!!
        )
    }

    // =========================================================
    // Update Chart
    // =========================================================

    private fun updateChart() {

        if (chartEntries.isEmpty()) {

            binding.lineChart.clear()

            binding.lineChart.setNoDataText(
                "No chart data available"
            )

            binding.lineChart.invalidate()

            return
        }

        val gradientFill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#4D2BBFA4"),
                Color.parseColor("#002BBFA4")
            )
        )

        val dataSet = LineDataSet(
            chartEntries,
            selectedDataType
        ).apply {

            color = colorTeal

            lineWidth = 2.5f

            setCircleColor(colorTeal)

            circleRadius = 4.5f

            circleHoleRadius = 2.5f

            circleHoleColor = Color.WHITE

            setDrawValues(false)

            mode = LineDataSet.Mode.CUBIC_BEZIER

            cubicIntensity = 0.2f

            fillDrawable = gradientFill

            setDrawFilled(true)

            isHighlightEnabled = true

            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(false)

            highLightColor = Color.TRANSPARENT
        }

        binding.lineChart.xAxis.apply {

            valueFormatter =
                IndexAxisValueFormatter(chartLabels)

            labelCount =
                if (chartLabels.size > 6)
                    6
                else
                    chartLabels.size
        }

        binding.lineChart.data =
            LineData(dataSet)

        binding.lineChart.animateX(
            800,
            Easing.EaseInOutQuart
        )

        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}

// =========================================================
// Marker View
// =========================================================

class CustomMarkerView(
    context: android.content.Context,
    layoutResource: Int
) : MarkerView(context, layoutResource) {

    private val tvContent: TextView =
        findViewById(R.id.tvMarkerContent)

    override fun refreshContent(
        e: Entry,
        highlight: Highlight
    ) {

        tvContent.text =
            if (e.y % 1 == 0f)
                e.y.toInt().toString()
            else
                "%.1f".format(e.y)

        super.refreshContent(
            e,
            highlight
        )
    }

    override fun getOffset(): MPPointF {

        return MPPointF(
            -(width / 2f),
            -(height + 16f)
        )
    }
}