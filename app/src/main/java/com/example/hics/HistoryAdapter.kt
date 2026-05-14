package com.example.hics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private var list: MutableList<HistoryModel>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // =========================
    // SHOW / HIDE COLUMN
    // =========================

    var showPh = true
    var showPpm = true
    var showWaterTemp = true
    var showAirTemp = true
    var showLight = true
    var showWaterLevel = true

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val rowLayout: LinearLayout =
            view.findViewById(R.id.rowLayout)

        val tvTime: TextView =
            view.findViewById(R.id.tvTime)

        val tvPh: TextView =
            view.findViewById(R.id.tvPh)

        val tvPpm: TextView =
            view.findViewById(R.id.tvPpm)

        val tvWaterTemp: TextView =
            view.findViewById(R.id.tvWaterTemp)

        val tvAirTemp: TextView =
            view.findViewById(R.id.tvAirTemp)

        val tvLight: TextView =
            view.findViewById(R.id.tvLight)

        val tvWaterLevel: TextView =
            view.findViewById(R.id.tvWaterLevel)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val data = list[position]

        // =========================
        // SET DATA
        // =========================

        holder.tvTime.text = data.timestamp
        holder.tvPh.text = data.ph
        holder.tvPpm.text = data.ppm
        holder.tvWaterTemp.text = data.waterTemp
        holder.tvAirTemp.text = data.airTemp
        holder.tvLight.text = data.light
        holder.tvWaterLevel.text = data.waterLevel

        // =========================
        // TOTAL KOLOM
        // =========================

        var visibleColumn = 1 // timestamp

        if (showPh) visibleColumn++
        if (showPpm) visibleColumn++
        if (showWaterTemp) visibleColumn++
        if (showAirTemp) visibleColumn++
        if (showLight) visibleColumn++
        if (showWaterLevel) visibleColumn++

        /*
         * <= 4 kolom
         * FULL WIDTH
         *
         * > 4 kolom
         * HORIZONTAL SCROLL
         */

        val useDynamicWidth = visibleColumn <= 3

        // =========================
        // TIMESTAMP
        // =========================

        setColumnVisibility(
            holder.tvTime,
            true,
            if (useDynamicWidth) {
                when (visibleColumn) {
                    1 -> 3f
                    2 -> 2.2f
                    3 -> 1.8f
                    else -> 1.5f
                }
            } else {
                0f
            }
        )

        // =========================
        // SENSOR
        // =========================

        setColumnVisibility(
            holder.tvPh,
            showPh,
            if (useDynamicWidth) 1f else 0f
        )

        setColumnVisibility(
            holder.tvPpm,
            showPpm,
            if (useDynamicWidth) 1f else 0f
        )

        setColumnVisibility(
            holder.tvWaterTemp,
            showWaterTemp,
            if (useDynamicWidth) 1f else 0f
        )

        setColumnVisibility(
            holder.tvAirTemp,
            showAirTemp,
            if (useDynamicWidth) 1f else 0f
        )

        setColumnVisibility(
            holder.tvLight,
            showLight,
            if (useDynamicWidth) 1f else 0f
        )

        setColumnVisibility(
            holder.tvWaterLevel,
            showWaterLevel,
            if (useDynamicWidth) 1f else 0f
        )

        // =========================
        // ROW WIDTH
        // =========================

        val rowParams =
            holder.rowLayout.layoutParams

        rowParams.width =
            if (useDynamicWidth) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }

        holder.rowLayout.layoutParams = rowParams
    }

    // =========================
    // SHOW / HIDE COLUMN
    // =========================

    private fun setColumnVisibility(
        view: View,
        isVisible: Boolean,
        weight: Float
    ) {

        val params =
            view.layoutParams as LinearLayout.LayoutParams

        if (isVisible) {

            if (weight > 0f) {

                /*
                 * MODE FULL WIDTH
                 */

                params.width = 0
                params.weight = weight

            } else {

                /*
                 * MODE HORIZONTAL SCROLL
                 */

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

    // =========================
    // UPDATE DATA
    // =========================

    fun updateData(
        newList: MutableList<HistoryModel>
    ) {

        list = newList
        notifyDataSetChanged()
    }
}