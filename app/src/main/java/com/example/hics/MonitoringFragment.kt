package com.example.hics

import android.content.Context.MODE_PRIVATE
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.addCallback
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MonitoringFragment: Fragment() {

    private lateinit var etPhMin : EditText
    private lateinit var etPhMax : EditText
    private lateinit var etPpmMin : EditText
    private lateinit var etPpmMax : EditText

    private lateinit var switchNotif : LinearLayout
    private lateinit var circleNotif : View
    private lateinit var save : CardView

    private lateinit var spinnerInterval : Spinner
    private lateinit var spinnerSuhu : Spinner
    private lateinit var spinnerNutrisi : Spinner

    private var firebaseDatabase = FirebaseDatabase.getInstance()
    private var indexAcc: Int?    = 0
    private var isOn: Boolean     = false
    private var deviceID: String? = ""

    private var online: Boolean = false

    var phMin: String? = ""
    var phMax: String? = ""
    var ppmMin: String? = ""
    var ppmMax: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.setting_monitoring, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhMin     = view.findViewById(R.id.phMin)
        etPhMax     = view.findViewById(R.id.phMax)
        etPpmMin    = view.findViewById(R.id.ppmMin)
        etPpmMax    = view.findViewById(R.id.ppmMax)
        switchNotif = view.findViewById(R.id.switchNotif)
        circleNotif = view.findViewById(R.id.circleNotif)
        spinnerInterval  = view.findViewById(R.id.spinnerInterval)
        spinnerSuhu      = view.findViewById(R.id.spinnerSuhu)
        spinnerNutrisi   = view.findViewById(R.id.spinnernutrisi)
        save             = view.findViewById(R.id.save)

        val intervalList = listOf("10", "30", "60", "120")
        val suhuList     = listOf("C", "F")
        val nutrisiList  = listOf("PPM", "EC")

        spinnerInterval.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, intervalList)
        spinnerSuhu.adapter     = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, suhuList)
        spinnerNutrisi.adapter  = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nutrisiList)

        val accPref      = requireActivity().getSharedPreferences("ACCOUNT", MODE_PRIVATE)
        deviceID         = accPref.getString("deviceID", "")

        Log.d("MonitoringFragment", "DeviceID: $deviceID")

        var baseFirebase = firebaseDatabase.getReference("Hics")

        if (deviceID != null && deviceID.toString().isNotEmpty()) {
            baseFirebase.child(deviceID.toString()).child("setting").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        spinnerInterval.visibility = View.VISIBLE
                        spinnerSuhu.visibility     = View.VISIBLE
                        spinnerNutrisi.visibility  = View.VISIBLE

                        ppmMin = snapshot.child("phMin").value.toString()
                        ppmMax = snapshot.child("phMax").value.toString()
                        phMin  = snapshot.child("ppmMin").value.toString()
                        phMax  = snapshot.child("ppmMax").value.toString()
                        val notif  = snapshot.child("notifAlert").value.toString()
                        val tUnit  = snapshot.child("tempUnit").value.toString()
                        val nUnit  = snapshot.child("ppmUnit").value.toString()
                        val interval = snapshot.child("intervalUpdate").value.toString()

                        val intervalIndex = when(interval) {
                            "10"  -> 0
                            "30"  -> 1
                            "60"  -> 2
                            "120" -> 3
                            else  -> 0
                        }

                        val suhuIndex = when(tUnit) {
                            "C" -> 0
                            "F" -> 1
                            else -> 0
                        }

                        val nutrisiIndex = when(nUnit) {
                            "PPM" -> 0
                            "EC"  -> 1
                            else  -> 0
                        }

                        etPhMin.hint = phMin
                        etPhMax.hint = phMax
                        etPpmMin.hint = ppmMin
                        etPpmMax.hint = ppmMax

                        if(notif == "true") {
                            updateSwitchUI(true)
                            isOn = true
                        }
                        else {
                            isOn = false
                            updateSwitchUI(false)
                        }

                        spinnerInterval.setSelection(intervalIndex)
                        spinnerSuhu.setSelection(suhuIndex)
                        spinnerNutrisi.setSelection(nutrisiIndex)

                        online = true

                    } else {
                        spinnerInterval.visibility = View.GONE
                        spinnerSuhu.visibility     = View.GONE
                        spinnerNutrisi.visibility  = View.GONE

                        online = false
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

        }

        switchNotif.setOnClickListener {
            isOn = !isOn
            updateSwitchUI(isOn)
        }
        save.setOnClickListener {
            if(online) {
                val newPhMax = etPhMax.text.toString().ifEmpty { phMax }
                val newPhMin = etPhMin.text.toString().ifEmpty { phMin }
                val newPpmMax = etPpmMax.text.toString().ifEmpty { ppmMax }
                val newPpmMin = etPpmMin.text.toString().ifEmpty { ppmMin }

                if (deviceID != null && deviceID.toString().isNotEmpty()) {
                    baseFirebase.child(deviceID!!)
                        .child("setting")
                        .updateChildren(
                            mapOf(
                                "phMax" to newPhMax,
                                "phMin" to newPhMin,
                                "ppmMax" to newPpmMax,
                                "ppmMin" to newPpmMin,
                                "notifAlert" to isOn.toString(),
                                "tempUnit" to spinnerSuhu.selectedItem.toString(),
                                "ppmUnit" to spinnerNutrisi.selectedItem.toString(),
                                "intervalUpdate" to spinnerInterval.selectedItem.toString()
                            )
                        )
                }

                Toast.makeText(requireContext(), "Data Updated", Toast.LENGTH_SHORT).show()
                val fragment = SettingFragment()
                val transaction = requireActivity().supportFragmentManager.beginTransaction()
                transaction.replace(R.id.mainFragment, fragment)
                transaction.commit()
            }
        }
    }

    fun updateSwitchUI(isOn: Boolean) {
        if (isOn) {
            switchNotif.setBackgroundResource(R.drawable.bg_switch_on)
            circleNotif.animate().translationX(
                (switchNotif.width - circleNotif.width - 12).toFloat()
            ).setDuration(200).start()
        } else {
            switchNotif.setBackgroundResource(R.drawable.bg_switch_off)
            circleNotif.animate().translationX(0f).setDuration(200).start()
        }
    }
}