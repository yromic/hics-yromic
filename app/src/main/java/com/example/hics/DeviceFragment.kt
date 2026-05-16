package com.example.hics

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DeviceFragment: Fragment() {

    private lateinit var connectButton: CardView
    private lateinit var idInput: EditText

    private var firebaseDatabase = FirebaseDatabase.getInstance()
    private var id: String        = ""
    private var deviceID: String? = ""
    private var indexAcc: Int?    = 0

    private lateinit var deviceIDtv : TextView
    private lateinit var ssidtv     : TextView
    private lateinit var tvUpdate   : TextView
    private lateinit var connectBt      : CardView
    private lateinit var disconnectBt   : CardView
    private lateinit var tvInputDevice  : TextView
    private lateinit var layoutInputDevice   : CardView
    private lateinit var layoutInfo          : CardView

    private lateinit var progressBar: ProgressBar



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.setting_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        connectButton       = view.findViewById(R.id.connectBt)
        idInput             = view.findViewById(R.id.idInput)
        deviceIDtv          = view.findViewById(R.id.deviceID)
        connectBt           = view.findViewById(R.id.connectBt)
        tvInputDevice       = view.findViewById(R.id.tvInputDevice)
        layoutInputDevice   = view.findViewById(R.id.layoutInputDevice)
        layoutInfo          = view.findViewById(R.id.layoutInfo)
        disconnectBt        = view.findViewById(R.id.disconnectBt)
        ssidtv              = view.findViewById(R.id.ssid)
        progressBar         = view.findViewById(R.id.progres)
        tvUpdate            = view.findViewById(R.id.lastUpdate)

        showWhenStart()

        val accPref      = requireActivity().getSharedPreferences("ACCOUNT", MODE_PRIVATE)
        indexAcc         = accPref.getInt("index", -1)
        deviceID         = accPref.getString("deviceID", "")

        Log.d("DeviceFragment", "indexAcc: $indexAcc")

        var baseFirebase = firebaseDatabase.getReference("Hics")
        var accFirebase = firebaseDatabase.getReference("User")

        progressBar.visibility = View.VISIBLE

        accFirebase.child("user_$indexAcc").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.child("id").exists()) {
                    val id   = snapshot.child("id").value.toString()

                    showWhenIdExist()
                    deviceIDtv.text = id

                    baseFirebase.child(id).child("device").addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val ssid = snapshot.child("ssid").value.toString()
                                val last = snapshot.child("lastUpdate").value.toString()
                                ssidtv.text = ssid
                                tvUpdate.text   = last
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    })

                } else {
                    showWhenIdNotExist()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        disconnectBt.setOnClickListener {
            accFirebase.child("user_$indexAcc").child("id").removeValue()
            requireActivity().getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
                .remove("deviceID")
                .apply()

            Toast.makeText(activity, "Disconnected", Toast.LENGTH_SHORT).show()
        }

        connectButton.setOnClickListener {
            id = idInput.text.toString()

            if(id.isEmpty()) {
                Toast.makeText(activity, "ID Kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            baseFirebase.child(id).get()
                .addOnSuccessListener { snapshot ->
                    if(snapshot.exists()) {

                        accFirebase.child("user_$indexAcc").child("id").setValue(id)

                        requireActivity().getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
                            .putString("deviceID", id)
                            .commit()

                        Toast.makeText(activity, "Connected", Toast.LENGTH_SHORT).show()

                    } else {
                        Toast.makeText(activity, "ID Not Available", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    fun showWhenIdExist() {
        progressBar.visibility       = View.GONE
        tvInputDevice.visibility     = View.GONE
        layoutInputDevice.visibility = View.GONE
        connectBt.visibility         = View.GONE

        layoutInfo.visibility   = View.VISIBLE
        disconnectBt.visibility = View.VISIBLE
    }

    fun showWhenIdNotExist() {
        progressBar.visibility       = View.GONE
        tvInputDevice.visibility     = View.VISIBLE
        layoutInputDevice.visibility = View.VISIBLE
        connectBt.visibility         = View.VISIBLE

        layoutInfo.visibility   = View.GONE
        disconnectBt.visibility = View.GONE
    }

    fun showWhenStart() {
        progressBar.visibility       = View.GONE
        tvInputDevice.visibility     = View.GONE
        layoutInputDevice.visibility = View.GONE
        connectBt.visibility         = View.GONE

        layoutInfo.visibility   = View.GONE
        disconnectBt.visibility = View.GONE
    }
}