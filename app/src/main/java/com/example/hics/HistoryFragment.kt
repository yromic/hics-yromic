package com.example.hics

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.firebase.database.*
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    // FILTER UI
    private lateinit var btnDateRange: Button
    private lateinit var btnClear: Button

    private lateinit var layoutFilterHeader: LinearLayout
    private lateinit var layoutFilterContent: LinearLayout
    private lateinit var ivArrow: ImageView

    // CHIP FILTER
    private lateinit var cbAll: CheckBox
    private lateinit var cbPpm: CheckBox
    private lateinit var cbPh: CheckBox
    private lateinit var cbWaterTemp: CheckBox
    private lateinit var cbAirTemp: CheckBox
    private lateinit var cbLight: CheckBox
    private lateinit var cbWaterLevel: CheckBox

    // HEADER TABLE
    private lateinit var headerTimestamp: TextView
    private lateinit var headerPh: TextView
    private lateinit var headerPpm: TextView
    private lateinit var headerWaterTemp: TextView
    private lateinit var headerAirTemp: TextView
    private lateinit var headerLight: TextView
    private lateinit var headerWaterLevel: TextView

    // EXPORT
    private lateinit var btnExport: Button

    // OTHER UI
    private lateinit var tvTotal: TextView
    private lateinit var progressBar: ProgressBar

    // RECYCLER
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter

    private val listData = mutableListOf<HistoryModel>()
    private val database = FirebaseDatabase.getInstance()

    private var startFilter: Long = 0
    private var endFilter: Long = Long.MAX_VALUE

    private var isFilterOpen = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // INIT VIEW

        btnDateRange =
            view.findViewById(R.id.btnRangeDate)

        btnClear =
            view.findViewById(R.id.btnClearFilter)

        layoutFilterHeader =
            view.findViewById(R.id.layoutFilterHeader)

        layoutFilterContent =
            view.findViewById(R.id.layoutFilterContent)

        ivArrow =
            view.findViewById(R.id.ivFilterArrow)

        btnExport =
            view.findViewById(R.id.btnExport)

        tvTotal =
            view.findViewById(R.id.tvTotalData)

        progressBar =
            view.findViewById(R.id.progressBar)

        recyclerView =
            view.findViewById(R.id.rvHistory)

        // CHIP

        cbAll =
            view.findViewById(R.id.cbAll)

        cbPpm =
            view.findViewById(R.id.cbppm)

        cbPh =
            view.findViewById(R.id.cbPh)

        cbWaterTemp =
            view.findViewById(R.id.cbWaterTemp)

        cbAirTemp =
            view.findViewById(R.id.cbairTemp)

        cbLight =
            view.findViewById(R.id.cblight)

        cbWaterLevel =
            view.findViewById(R.id.cbwaterLevel)

        // HEADER

        headerTimestamp =
            view.findViewById(R.id.headerTimestamp)

        headerPh =
            view.findViewById(R.id.headerPh)

        headerPpm =
            view.findViewById(R.id.headerPpm)

        headerWaterTemp =
            view.findViewById(R.id.headerWaterTemp)

        headerAirTemp =
            view.findViewById(R.id.headerAirTemp)

        headerLight =
            view.findViewById(R.id.headerLight)

        headerWaterLevel =
            view.findViewById(R.id.headerWaterLevel)

        // RECYCLER

        recyclerView.layoutManager =
            LinearLayoutManager(requireContext())

        adapter = HistoryAdapter(listData)

        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false

        // =========================
        // TOGGLE FILTER
        // =========================

        layoutFilterHeader.setOnClickListener {

            isFilterOpen = !isFilterOpen

            TransitionManager.beginDelayedTransition(
                view as ViewGroup,
                AutoTransition()
            )

            layoutFilterContent.visibility =
                if (isFilterOpen)
                    View.VISIBLE
                else
                    View.GONE

            ivArrow.animate()
                .rotation(
                    if (isFilterOpen) 180f
                    else 0f
                )
                .setDuration(200)
                .start()
        }

        // =========================
        // DATE RANGE
        // =========================

        btnDateRange.setOnClickListener {
            pickStartDate()
        }

        // =========================
        // EXPORT
        // =========================

        btnExport.setOnClickListener {

            val options = arrayOf(
                "Export PDF",
                "Export CSV"
            )

            AlertDialog.Builder(requireContext())
                .setTitle("Pilih Format Export")
                .setItems(options) { _, which ->

                    when (which) {

                        0 -> exportPDF()

                        1 -> exportCSV()
                    }
                }
                .show()
        }

        // =========================
        // FILTER CHECKBOX
        // =========================

        cbAll.isChecked = true
        cbPpm.isChecked = true
        cbPh.isChecked = true
        cbWaterTemp.isChecked = true
        cbAirTemp.isChecked = true
        cbLight.isChecked = true
        cbWaterLevel.isChecked = true

        cbAll.setOnCheckedChangeListener { _, isChecked ->

            if (isChecked) {

                cbPpm.isChecked = true
                cbPh.isChecked = true
                cbWaterTemp.isChecked = true
                cbAirTemp.isChecked = true
                cbLight.isChecked = true
                cbWaterLevel.isChecked = true
            }

            updateColumnVisibility()
        }

        val checkboxListener =
            CompoundButton.OnCheckedChangeListener {
                    buttonView,
                    _ ->

                val totalChecked = listOf(
                    cbPpm,
                    cbPh,
                    cbWaterTemp,
                    cbAirTemp,
                    cbLight,
                    cbWaterLevel
                ).count { it.isChecked }

                if (totalChecked == 0) {

                    buttonView.isChecked = true

                    Toast.makeText(
                        requireContext(),
                        "Minimal 1 data dipilih",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@OnCheckedChangeListener
                }

                cbAll.setOnCheckedChangeListener(null)

                cbAll.isChecked =
                    cbPpm.isChecked &&
                            cbPh.isChecked &&
                            cbWaterTemp.isChecked &&
                            cbAirTemp.isChecked &&
                            cbLight.isChecked &&
                            cbWaterLevel.isChecked

                cbAll.setOnCheckedChangeListener { _, checked ->

                    if (checked) {

                        cbPpm.isChecked = true
                        cbPh.isChecked = true
                        cbWaterTemp.isChecked = true
                        cbAirTemp.isChecked = true
                        cbLight.isChecked = true
                        cbWaterLevel.isChecked = true
                    }

                    updateColumnVisibility()
                }

                updateColumnVisibility()
            }

        cbPpm.setOnCheckedChangeListener(
            checkboxListener
        )

        cbPh.setOnCheckedChangeListener(
            checkboxListener
        )

        cbWaterTemp.setOnCheckedChangeListener(
            checkboxListener
        )

        cbAirTemp.setOnCheckedChangeListener(
            checkboxListener
        )

        cbLight.setOnCheckedChangeListener(
            checkboxListener
        )

        cbWaterLevel.setOnCheckedChangeListener(
            checkboxListener
        )

        // =========================
        // RESET FILTER
        // =========================

        btnClear.setOnClickListener {

            startFilter = 0
            endFilter = Long.MAX_VALUE

            btnDateRange.text =
                "Rentang Waktu"

            cbAll.isChecked = true

            updateColumnVisibility()

            getHistoryData()
        }

        // LOAD DATA
        updateColumnVisibility()
        getHistoryData()
    }

    // ===============================
    // EXPORT CSV
    // ===============================

    private fun exportCSV() {

        try {

            val fileName =
                "history_${System.currentTimeMillis()}.csv"

            val file =
                File(
                    requireContext()
                        .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    fileName
                )

            val writer =
                OutputStreamWriter(FileOutputStream(file))

            val headers =
                mutableListOf<String>()

            headers.add("Timestamp")

            if (cbPh.isChecked)
                headers.add("pH")

            if (cbPpm.isChecked)
                headers.add("PPM")

            if (cbWaterTemp.isChecked)
                headers.add("Water Temp")

            if (cbAirTemp.isChecked)
                headers.add("Air Temp")

            if (cbLight.isChecked)
                headers.add("Light")

            if (cbWaterLevel.isChecked)
                headers.add("Water Level")

            writer.append(headers.joinToString(","))
            writer.append("\n")

            for (data in listData) {

                val row =
                    mutableListOf<String>()

                row.add(data.timestamp)

                if (cbPh.isChecked)
                    row.add(data.ph)

                if (cbPpm.isChecked)
                    row.add(data.ppm)

                if (cbWaterTemp.isChecked)
                    row.add(data.waterTemp)

                if (cbAirTemp.isChecked)
                    row.add(data.airTemp)

                if (cbLight.isChecked)
                    row.add(data.light)

                if (cbWaterLevel.isChecked)
                    row.add(data.waterLevel)

                writer.append(row.joinToString(","))
                writer.append("\n")
            }

            writer.flush()
            writer.close()

            openFile(file, "text/csv")

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                requireContext(),
                "Export CSV gagal",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ===============================
    // EXPORT PDF
    // ===============================

    private fun exportPDF() {

        try {

            val fileName =
                "history_${System.currentTimeMillis()}.pdf"

            val file =
                File(
                    requireContext()
                        .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    fileName
                )

            val document = Document()

            PdfWriter.getInstance(
                document,
                FileOutputStream(file)
            )

            document.open()

            val title = Paragraph(
                "History Monitoring HICS"
            )

            title.spacingAfter = 16f

            document.add(title)

            val columnCount =
                getSelectedColumnCount()

            val table =
                PdfPTable(columnCount)

            table.widthPercentage = 100f

            table.addCell("Timestamp")

            if (cbPh.isChecked)
                table.addCell("pH")

            if (cbPpm.isChecked)
                table.addCell("PPM")

            if (cbWaterTemp.isChecked)
                table.addCell("Water Temp")

            if (cbAirTemp.isChecked)
                table.addCell("Air Temp")

            if (cbLight.isChecked)
                table.addCell("Light")

            if (cbWaterLevel.isChecked)
                table.addCell("Water Level")

            for (data in listData) {

                table.addCell(data.timestamp)

                if (cbPh.isChecked)
                    table.addCell(data.ph)

                if (cbPpm.isChecked)
                    table.addCell(data.ppm)

                if (cbWaterTemp.isChecked)
                    table.addCell(data.waterTemp)

                if (cbAirTemp.isChecked)
                    table.addCell(data.airTemp)

                if (cbLight.isChecked)
                    table.addCell(data.light)

                if (cbWaterLevel.isChecked)
                    table.addCell(data.waterLevel)
            }

            document.add(table)

            document.close()

            openFile(file, "application/pdf")

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                requireContext(),
                "Export PDF gagal",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ===============================
    // OPEN FILE
    // ===============================

    private fun openFile(
        file: File,
        type: String
    ) {

        try {

            val uri: Uri =
                FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().packageName + ".provider",
                    file
                )

            val intent = Intent(Intent.ACTION_VIEW)

            intent.setDataAndType(uri, type)

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(intent)

            Toast.makeText(
                requireContext(),
                "File berhasil diexport",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                requireContext(),
                "File tersimpan di folder Documents aplikasi",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ===============================
    // TOTAL COLUMN
    // ===============================

    private fun getSelectedColumnCount(): Int {

        var total = 1

        if (cbPh.isChecked) total++
        if (cbPpm.isChecked) total++
        if (cbWaterTemp.isChecked) total++
        if (cbAirTemp.isChecked) total++
        if (cbLight.isChecked) total++
        if (cbWaterLevel.isChecked) total++

        return total
    }

    // ===============================
    // UPDATE COLUMN VISIBILITY
    // ===============================

    private fun updateColumnVisibility() {

        adapter.showPh =
            cbPh.isChecked

        adapter.showPpm =
            cbPpm.isChecked

        adapter.showWaterTemp =
            cbWaterTemp.isChecked

        adapter.showAirTemp =
            cbAirTemp.isChecked

        adapter.showLight =
            cbLight.isChecked

        adapter.showWaterLevel =
            cbWaterLevel.isChecked

        var visibleColumn = 1

        if (cbPh.isChecked) visibleColumn++
        if (cbPpm.isChecked) visibleColumn++
        if (cbWaterTemp.isChecked) visibleColumn++
        if (cbAirTemp.isChecked) visibleColumn++
        if (cbLight.isChecked) visibleColumn++
        if (cbWaterLevel.isChecked) visibleColumn++

        val useWeight =
            visibleColumn <= 3

        setHeaderVisibility(
            headerTimestamp,
            true,
            if (useWeight) {
                if (visibleColumn <= 2)
                    2f
                else
                    1.5f
            } else {
                0f
            }
        )

        setHeaderVisibility(
            headerPh,
            cbPh.isChecked,
            if (useWeight) 1f else 0f
        )

        setHeaderVisibility(
            headerPpm,
            cbPpm.isChecked,
            if (useWeight) 1f else 0f
        )

        setHeaderVisibility(
            headerWaterTemp,
            cbWaterTemp.isChecked,
            if (useWeight) 1f else 0f
        )

        setHeaderVisibility(
            headerAirTemp,
            cbAirTemp.isChecked,
            if (useWeight) 1f else 0f
        )

        setHeaderVisibility(
            headerLight,
            cbLight.isChecked,
            if (useWeight) 1f else 0f
        )

        setHeaderVisibility(
            headerWaterLevel,
            cbWaterLevel.isChecked,
            if (useWeight) 1f else 0f
        )

        val rvParams =
            recyclerView.layoutParams

        rvParams.width =
            if (useWeight) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }

        recyclerView.layoutParams =
            rvParams

        adapter.notifyDataSetChanged()
    }

    // ===============================
    // HEADER VISIBILITY
    // ===============================

    private fun setHeaderVisibility(
        view: TextView,
        isVisible: Boolean,
        weight: Float
    ) {

        val params =
            view.layoutParams
                    as LinearLayout.LayoutParams

        if (isVisible) {

            if (weight > 0f) {

                params.width = 0
                params.weight = weight

            } else {

                params.width =
                    LinearLayout.LayoutParams.WRAP_CONTENT

                params.weight = 0f
            }

            view.visibility = View.VISIBLE

        } else {

            params.width = 0
            params.weight = 0f

            view.visibility = View.GONE
        }

        view.layoutParams = params
    }

    // ===============================
    // PICK START DATE
    // ===============================

    private fun pickStartDate() {

        val cal = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->

                val startCal =
                    Calendar.getInstance()

                startCal.set(
                    year,
                    month,
                    day,
                    0,
                    0,
                    0
                )

                startFilter =
                    startCal.timeInMillis

                pickEndDate()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ===============================
    // PICK END DATE
    // ===============================

    private fun pickEndDate() {

        val cal = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->

                val endCal =
                    Calendar.getInstance()

                endCal.set(
                    year,
                    month,
                    day,
                    23,
                    59,
                    59
                )

                endFilter =
                    endCal.timeInMillis

                val sdf = SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.getDefault()
                )

                val startText =
                    sdf.format(Date(startFilter))

                val endText =
                    sdf.format(Date(endFilter))

                btnDateRange.text =
                    "$startText - $endText"

                getHistoryData()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ===============================
    // GET DATA FIREBASE
    // ===============================

    private fun getHistoryData() {

        progressBar.visibility =
            View.VISIBLE

        val accPref =
            requireActivity()
                .getSharedPreferences(
                    "ACCOUNT",
                    MODE_PRIVATE
                )

        val deviceID =
            accPref.getString(
                "deviceID",
                ""
            ) ?: ""

        if (deviceID.isEmpty()) {

            progressBar.visibility =
                View.GONE

            return
        }

        val ref = database
            .getReference("Hics")
            .child(deviceID)
            .child("history")

        ref.addListenerForSingleValueEvent(
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    listData.clear()

                    val tempList =
                        mutableListOf<
                                Pair<Long, HistoryModel>
                                >()

                    for (year in snapshot.children) {
                        for (month in year.children) {
                            for (day in month.children) {
                                for (hour in day.children) {

                                    try {

                                        val dateStr =
                                            "${year.key}-${month.key}-${day.key} ${hour.key}"

                                        val sdf =
                                            SimpleDateFormat(
                                                "yyyy-MM-dd HH",
                                                Locale.getDefault()
                                            )

                                        val date =
                                            sdf.parse(dateStr)

                                        val timestamp =
                                            date?.time
                                                ?: continue

                                        val model =
                                            HistoryModel(
                                                timestamp = dateStr,
                                                airTemp = hour.child("airTemp").value.toString(),
                                                light = hour.child("light").value.toString(),
                                                ph = hour.child("pH").value.toString(),
                                                ppm = hour.child("ppm").value.toString(),
                                                waterLevel = hour.child("waterLevel").value.toString(),
                                                waterTemp = hour.child("waterTemp").value.toString()
                                            )

                                        tempList.add(
                                            Pair(
                                                timestamp,
                                                model
                                            )
                                        )

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }

                    val filtered =
                        tempList.filter {
                            it.first in
                                    startFilter..endFilter
                        }

                    val sorted =
                        filtered.sortedByDescending {
                            it.first
                        }

                    sorted.forEach {
                        listData.add(it.second)
                    }

                    adapter.notifyDataSetChanged()

                    tvTotal.text =
                        listData.size.toString()

                    progressBar.visibility =
                        View.GONE
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    progressBar.visibility =
                        View.GONE
                }
            }
        )
    }
}