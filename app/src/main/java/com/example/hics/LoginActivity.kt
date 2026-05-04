package com.example.hics

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogle = findViewById<Button>(R.id.btnLoginGoogle)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)
        val tvForgot = findViewById<TextView>(R.id.tvForgot)

        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance().reference

        // 🔥 GOOGLE CONFIG
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // =========================
        // 🔥 LOGIN EMAIL / USERNAME
        // =========================
        btnLogin.setOnClickListener {

            val input = etEmail.text.toString().trim().lowercase()
            val password = etPassword.text.toString().trim()

            if (input.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Isi semua field", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔥 LOGIN EMAIL
            if (Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
                loginWithEmail(auth, input, password)

            } else {
                // 🔥 LOGIN USERNAME TANPA INDEX
                database.child("User").get()
                    .addOnSuccessListener { snapshot ->

                        var emailFromUsername: String? = null

                        for (snap in snapshot.children) {
                            val usernameDB = snap.child("userName").value.toString()

                            if (usernameDB == input) {
                                emailFromUsername = snap.child("email").value.toString()
                                break
                            }
                        }

                        if (emailFromUsername == null) {
                            Toast.makeText(this, "Username tidak ditemukan", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        loginWithEmail(auth, emailFromUsername, password)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal ambil data user", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // =========================
        // 🔥 GOOGLE LOGIN
        // =========================
        btnGoogle.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }
    }

    // =========================
    // 🔥 RESULT GOOGLE
    // =========================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken

                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Token Google null", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this, "Google gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================
    // 🔥 GOOGLE AUTH
    // =========================
    private fun firebaseAuthWithGoogle(idToken: String) {

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener {

                val user = FirebaseAuth.getInstance().currentUser
                val email = user?.email
                val username = user?.displayName ?: "user"

                if (email.isNullOrEmpty()) {
                    Toast.makeText(this, "Email tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val database = FirebaseDatabase.getInstance().reference

                database.child("User").get()
                    .addOnSuccessListener { snapshot ->

                        var foundIndex: Int? = null

                        for (snap in snapshot.children) {
                            if (snap.child("email").value.toString() == email) {
                                val key = snap.key
                                foundIndex = key?.substringAfter("_")?.toIntOrNull()
                                break
                            }
                        }

                        if (foundIndex != null) {
                            saveSession(foundIndex)
                        } else {
                            // 🔥 USER BARU → KE USERNAME ACTIVITY
                            val intent = Intent(this, UsernameActivity::class.java)
                            intent.putExtra("email", email)
                            intent.putExtra("defaultUsername", username)
                            startActivity(intent)
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Auth Google gagal", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================
    // 🔥 LOGIN EMAIL
    // =========================
    private fun loginWithEmail(auth: FirebaseAuth, email: String, password: String) {

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {

                val database = FirebaseDatabase.getInstance().reference

                database.child("User").get()
                    .addOnSuccessListener { snapshot ->

                        var index: Int? = null

                        for (snap in snapshot.children) {
                            if (snap.child("email").value.toString() == email) {
                                index = snap.key?.substringAfter("_")?.toIntOrNull()
                                break
                            }
                        }

                        if (index != null) {
                            saveSession(index)
                        } else {
                            Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Login gagal", Toast.LENGTH_SHORT).show()
            }
    }

    // =========================
    // SAVE SESSION
    // =========================
    private fun saveSession(index: Int?) {
        getSharedPreferences("ACCOUNT", MODE_PRIVATE).edit()
            .putInt("index", index ?: -1)
            .apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}