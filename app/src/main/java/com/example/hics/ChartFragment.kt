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
import java.util.*

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private val firebaseDatabase = FirebaseDatabase.getInstance()

    private var deviceID: String? = ""

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }

        val accPref = requireActivity()
            .getSharedPreferences("ACCOUNT", MODE_PRIVATE)

        deviceID = accPref.getString("deviceID", "")

        Log.d("ChartFragment", "Device ID : $deviceID")

        setupSpinners()
        setupChart()
        loadRealtimeChart()
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

                val v = super.getView(position, convertView, parent)

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

                val v = super.getDropDownView(position, convertView, parent)

                (v as TextView).apply {
                    text = items[position]
                    setTextColor(Color.parseColor("#2D3A4A"))
                    textSize = 13f
                    setBackgroundResource(R.drawable.bg_dropdown_item)
                }

                return v
            }

        }.also {
            it.setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
    }

    private fun setupSpinners() {

        // =====================================================
        // DATA TYPE
        // =====================================================

        binding.spinnerDataType.background = null

        binding.spinnerDataType.adapter =
            makeAdapter(dataTypeList, Color.WHITE)

        binding.spinnerDataType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    selectedDataType = dataTypeList[position]

                    loadRealtimeChart()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.layoutSpinnerData.setOnClickListener {
            binding.spinnerDataType.performClick()
        }

        // =====================================================
        // PERIOD
        // =====================================================

        binding.spinnerPeriod.adapter =
            makeAdapter(periodList, Color.parseColor("#2D3A4A"))

        binding.spinnerPeriod.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    selectedPeriod = periodList[position]

                    loadRealtimeChart()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
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

                enableGridDashedLine(6f, 4f, 0f)

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

        historyListener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                chartEntries.clear()
                chartLabels.clear()

                val tempList =
                    mutableListOf<Pair<String, Float>>()

                for (yearSnap in snapshot.children) {

                    for (monthSnap in yearSnap.children) {

                        for (daySnap in monthSnap.children) {

                            for (timeSnap in daySnap.children) {

                                val value = when (selectedDataType) {

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

                                    val label = when (selectedPeriod) {

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

                // =================================================
                // AMBIL DATA TERBARU
                // =================================================

                val filteredList = when (selectedPeriod) {

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
                        Entry(index.toFloat(), pair.second)
                    )

                    chartLabels.add(pair.first)
                }

                updateChart()
            }

            override fun onCancelled(error: DatabaseError) {

                Log.e(
                    "ChartFragment",
                    error.message
                )
            }
        }

        historyRef.addValueEventListener(historyListener!!)
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

            highLightColor =
                Color.parseColor("#F5A623")

            highlightLineWidth = 1.5f

            isHighlightEnabled = true
        }

        binding.lineChart.xAxis.apply {

            valueFormatter =
                IndexAxisValueFormatter(chartLabels)

            labelCount =
                if (chartLabels.size > 6) 6
                else chartLabels.size
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

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {

        return MPPointF(
            -(width / 2f),
            -(height + 16f)
        )
    }
}