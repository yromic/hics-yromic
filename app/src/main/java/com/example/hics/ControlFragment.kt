package com.example.hics

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ControlFragment : Fragment() {

    private lateinit var mode: TextView
    private lateinit var tvStatusMode: TextView

    // MODE
    private lateinit var switchMode: LinearLayout
    private lateinit var circleMode: View

    // POMPA AIR
    private lateinit var pumpLayout: CardView
    private lateinit var switchPump: LinearLayout
    private lateinit var circlePump: View
    private lateinit var statusSwitch: TextView

    // PH UP
    private lateinit var phUpLayout: CardView
    private lateinit var switchPhUp: LinearLayout
    private lateinit var circlePhUp: View
    private lateinit var statusPhUp: TextView

    // PH DOWN
    private lateinit var phDownLayout: CardView
    private lateinit var switchPhDown: LinearLayout
    private lateinit var circlePhDown: View
    private lateinit var statusPhDown: TextView

    // NUTRISI UP
    private lateinit var nutrisiUpLayout: CardView
    private lateinit var switchNutrisiUp: LinearLayout
    private lateinit var circleNutrisiUp: View
    private lateinit var statusNutrisiUp: TextView

    // NUTRISI DOWN
    private lateinit var nutrisiDownLayout: CardView
    private lateinit var switchNutrisiDown: LinearLayout
    private lateinit var circleNutrisiDown: View
    private lateinit var statusNutrisiDown: TextView

    private var deviceID: String? = ""
    private var firebaseDatabase = FirebaseDatabase.getInstance()

    // STATUS
    private var modeStatus = false
    private var pumpOn = false
    private var phUpOn = false
    private var phDownOn = false
    private var nutrisiUpOn = false
    private var nutrisiDownOn = false

    private var online = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.setting_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // MODE
        mode = view.findViewById(R.id.mode)
        switchMode = view.findViewById(R.id.switchMode)
        circleMode = view.findViewById(R.id.circleMode)
        tvStatusMode = view.findViewById(R.id.tvStatusMode)

        // POMPA
        pumpLayout = view.findViewById(R.id.pumpLayout)
        switchPump = view.findViewById(R.id.switchPump)
        circlePump = view.findViewById(R.id.circlePump)
        statusSwitch = view.findViewById(R.id.statusSwitch)

        // PH UP
        phUpLayout = view.findViewById(R.id.phUpLayout)
        switchPhUp = view.findViewById(R.id.switchPhUp)
        circlePhUp = view.findViewById(R.id.circlePhUp)
        statusPhUp = view.findViewById(R.id.statusPhUp)

        // PH DOWN
        phDownLayout = view.findViewById(R.id.phDownLayout)
        switchPhDown = view.findViewById(R.id.switchPhDown)
        circlePhDown = view.findViewById(R.id.circlePhDown)
        statusPhDown = view.findViewById(R.id.statusPhDown)

        // NUTRISI UP
        nutrisiUpLayout = view.findViewById(R.id.nutrisiUpLayout)
        switchNutrisiUp = view.findViewById(R.id.switchNutrisiUp)
        circleNutrisiUp = view.findViewById(R.id.circleNutrisiUp)
        statusNutrisiUp = view.findViewById(R.id.statusNutrisiUp)

        // NUTRISI DOWN
        nutrisiDownLayout = view.findViewById(R.id.nutrisiDownLayout)
        switchNutrisiDown = view.findViewById(R.id.switchNutrisiDown)
        circleNutrisiDown = view.findViewById(R.id.circleNutrisiDown)
        statusNutrisiDown = view.findViewById(R.id.statusNutrisiDown)

        val accPref =
            requireActivity().getSharedPreferences("ACCOUNT", MODE_PRIVATE)

        deviceID = accPref.getString("deviceID", "")

        Log.d("ControlFragment", "DeviceID: $deviceID")

        val baseFirebase = firebaseDatabase.getReference("Hics")

        // ================= GET DATA FIREBASE =================
        if (!deviceID.isNullOrEmpty()) {

            baseFirebase.child(deviceID!!)
                .child("control")
                .addValueEventListener(object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {

                        if (snapshot.exists()) {

                            online = true

                            modeStatus =
                                snapshot.child("mode")
                                    .value.toString().toBoolean()

                            pumpOn =
                                snapshot.child("waterPump")
                                    .value.toString().toBoolean()

                            phUpOn =
                                snapshot.child("phUp")
                                    .value.toString().toBoolean()

                            phDownOn =
                                snapshot.child("phDown")
                                    .value.toString().toBoolean()

                            nutrisiUpOn =
                                snapshot.child("nutrisiUp")
                                    .value.toString().toBoolean()

                            nutrisiDownOn =
                                snapshot.child("nutrisiDown")
                                    .value.toString().toBoolean()

                            // MODE
                            if (modeStatus) {

                                mode.text = "Auto"
                                modeSwitchUI(true)

                                tvStatusMode.visibility = View.VISIBLE

                                // sembunyikan manual control
                                pumpLayout.visibility = View.GONE
                                phUpLayout.visibility = View.GONE
                                phDownLayout.visibility = View.GONE
                                nutrisiUpLayout.visibility = View.GONE
                                nutrisiDownLayout.visibility = View.GONE

                            } else {

                                mode.text = "Manual"
                                modeSwitchUI(false)

                                tvStatusMode.visibility = View.GONE

                                // tampilkan manual control
                                pumpLayout.visibility = View.VISIBLE
                                phUpLayout.visibility = View.VISIBLE
                                phDownLayout.visibility = View.VISIBLE
                                nutrisiUpLayout.visibility = View.VISIBLE
                                nutrisiDownLayout.visibility = View.VISIBLE

                                // update UI
                                pumpSwitchUI(pumpOn)
                                phUpSwitchUI(phUpOn)
                                phDownSwitchUI(phDownOn)
                                nutrisiUpSwitchUI(nutrisiUpOn)
                                nutrisiDownSwitchUI(nutrisiDownOn)
                            }

                        } else {
                            online = false
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {

                        Toast.makeText(
                            requireContext(),
                            "Error: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }

        // ================= MODE =================
        switchMode.setOnClickListener {

            if (online) {

                modeStatus = !modeStatus
                modeSwitchUI(modeStatus)

                baseFirebase.child(deviceID!!)
                    .child("control")
                    .child("mode")
                    .setValue(modeStatus)
            }
        }

        // ================= POMPA =================
        switchPump.setOnClickListener {

            if (online) {

                pumpOn = !pumpOn
                pumpSwitchUI(pumpOn)

                baseFirebase.child(deviceID!!)
                    .child("control")
                    .child("waterPump")
                    .setValue(pumpOn)
            }
        }

        // ================= PH UP =================
        switchPhUp.setOnClickListener {

            if (online) {

                phUpOn = !phUpOn
                phUpSwitchUI(phUpOn)

                baseFirebase.child(deviceID!!)
                    .child("control")
                    .child("phUp")
                    .setValue(phUpOn)
            }
        }

        // ================= PH DOWN =================
        switchPhDown.setOnClickListener {

            if (online) {

                phDownOn = !phDownOn
                phDownSwitchUI(phDownOn)

                baseFirebase.child(deviceID!!)
                    .child("control")
                    .child("phDown")
                    .setValue(phDownOn)
            }
        }

        // ================= NUTRISI UP =================
        switchNutrisiUp.setOnClickListener {

            if (online) {

                nutrisiUpOn = !nutrisiUpOn
                nutrisiUpSwitchUI(nutrisiUpOn)

                baseFirebase.child(deviceID!!)
                    .child("control")
                    .child("nutrisiUp")
                    .setValue(nutrisiUpOn)
            }
        }

        // ================= NUTRISI DOWN =================
        switchNutrisiDown.setOnClickListener {

            if (online) {

                nutrisiDownOn = !nutrisiDownOn
                nutrisiDownSwitchUI(nutrisiDownOn)

                baseFirebase.child(deviceID!!)
                    .child("control")
                    .child("nutrisiDown")
                    .setValue(nutrisiDownOn)
            }
        }
    }

    // ================= MODE UI =================
    private fun modeSwitchUI(isOn: Boolean) {

        if (isOn) {

            switchMode.setBackgroundResource(R.drawable.bg_switch_on)

            circleMode.animate()
                .translationX(
                    (switchPump.width - circlePump.width - 12).toFloat()
                )
                .setDuration(200)
                .start()

        } else {

            switchMode.setBackgroundResource(R.drawable.bg_switch_off)

            circleMode.animate()
                .translationX(0f)
                .setDuration(200)
                .start()
        }
    }

    // ================= POMPA UI =================
    private fun pumpSwitchUI(isOn: Boolean) {

        if (isOn) {

            switchPump.setBackgroundResource(R.drawable.bg_switch_on)

            circlePump.animate()
                .translationX(
                    (switchPump.width - circlePump.width - 12).toFloat()
                )
                .setDuration(200)
                .start()

            statusSwitch.text = "ON"

        } else {

            switchPump.setBackgroundResource(R.drawable.bg_switch_off)

            circlePump.animate()
                .translationX(0f)
                .setDuration(200)
                .start()

            statusSwitch.text = "OFF"
        }
    }

    // ================= PH UP UI =================
    private fun phUpSwitchUI(isOn: Boolean) {

        if (isOn) {

            switchPhUp.setBackgroundResource(R.drawable.bg_switch_on)

            circlePhUp.animate()
                .translationX(
                    (switchPump.width - circlePump.width - 12).toFloat()
                )
                .setDuration(200)
                .start()

            statusPhUp.text = "ON"

        } else {

            switchPhUp.setBackgroundResource(R.drawable.bg_switch_off)

            circlePhUp.animate()
                .translationX(0f)
                .setDuration(200)
                .start()

            statusPhUp.text = "OFF"
        }
    }

    // ================= PH DOWN UI =================
    private fun phDownSwitchUI(isOn: Boolean) {

        if (isOn) {

            switchPhDown.setBackgroundResource(R.drawable.bg_switch_on)

            circlePhDown.animate()
                .translationX(
                    (switchPump.width - circlePump.width - 12).toFloat()
                )
                .setDuration(200)
                .start()

            statusPhDown.text = "ON"

        } else {

            switchPhDown.setBackgroundResource(R.drawable.bg_switch_off)

            circlePhDown.animate()
                .translationX(0f)
                .setDuration(200)
                .start()

            statusPhDown.text = "OFF"
        }
    }

    // ================= NUTRISI UP UI =================
    private fun nutrisiUpSwitchUI(isOn: Boolean) {

        if (isOn) {

            switchNutrisiUp.setBackgroundResource(R.drawable.bg_switch_on)

            circleNutrisiUp.animate()
                .translationX(
                    (switchPump.width - circlePump.width - 12).toFloat()
                )
                .setDuration(200)
                .start()

            statusNutrisiUp.text = "ON"

        } else {

            switchNutrisiUp.setBackgroundResource(R.drawable.bg_switch_off)

            circleNutrisiUp.animate()
                .translationX(0f)
                .setDuration(200)
                .start()

            statusNutrisiUp.text = "OFF"
        }
    }

    // ================= NUTRISI DOWN UI =================
    private fun nutrisiDownSwitchUI(isOn: Boolean) {

        if (isOn) {

            switchNutrisiDown.setBackgroundResource(R.drawable.bg_switch_on)

            circleNutrisiDown.animate()
                .translationX(
                    (switchPump.width - circlePump.width - 12).toFloat()
                )
                .setDuration(200)
                .start()

            statusNutrisiDown.text = "ON"

        } else {

            switchNutrisiDown.setBackgroundResource(R.drawable.bg_switch_off)

            circleNutrisiDown.animate()
                .translationX(0f)
                .setDuration(200)
                .start()

            statusNutrisiDown.text = "OFF"
        }
    }
}