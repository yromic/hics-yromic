package com.example.hics

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.auth.api.signin.*

class UsernameActivity : AppCompatActivity() {

    private lateinit var googleClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.username)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        val email = intent.getStringExtra("email") ?: ""
        val defaultUsername = intent.getStringExtra("defaultUsername") ?: ""

        etUsername.setText(defaultUsername)

        val database = FirebaseDatabase.getInstance().reference

        // 🔥 INIT GOOGLE CLIENT
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleClient = GoogleSignIn.getClient(this, gso)

        // =========================
        // 🔥 SIMPAN USERNAME
        // =========================
        btnSave.setOnClickListener {

            val username = etUsername.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "Username wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            database.child("User").get()
                .addOnSuccessListener { snapshot ->

                    // 🔥 CEK DUPLIKAT USERNAME
                    for (snap in snapshot.children) {
                        val dbUsername = snap.child("userName").value.toString()
                        if (dbUsername == username) {
                            Toast.makeText(this, "Username sudah dipakai", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                    }

                    // 🔥 BUAT user_X
                    var index = 1
                    var key: String

                    do {
                        key = "user_$index"
                        index++
                    } while (snapshot.hasChild(key))

                    val userMap = HashMap<String, Any>()
                    userMap["userName"] = username
                    userMap["email"] = email
                    userMap["id"] = ""

                    // 🔥 SIMPAN USER
                    database.child("User")
                        .child(key)
                        .setValue(userMap)
                        .addOnSuccessListener {

                            val indexFix = key.substringAfter("_").toIntOrNull()

                            getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
                                .putInt("index", indexFix ?: -1)
                                .apply()

                            Toast.makeText(this, "Register berhasil", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Gagal simpan user", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal ambil data user", Toast.LENGTH_SHORT).show()
                }
        }

        // =========================
        // 🔥 TOMBOL BATAL
        // =========================
        btnCancel.setOnClickListener {
            logoutAndBack()
        }

        // =========================
        // 🔥 BACK BUTTON FIX (ANDROID BARU)
        // =========================
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                logoutAndBack()
            }
        })
    }

    // =========================
    // 🔥 LOGOUT + HAPUS AKUN FIREBASE
    // =========================
    private fun logoutAndBack() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            user.delete().addOnCompleteListener {

                FirebaseAuth.getInstance().signOut()
                googleClient.signOut()

                Toast.makeText(this, "Login dibatalkan", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        } else {
            FirebaseAuth.getInstance().signOut()
            googleClient.signOut()
            finish()
        }
    }
}