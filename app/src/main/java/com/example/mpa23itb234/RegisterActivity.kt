package com.example.mpa23itb234

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityRegisterBinding
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sử dụng theme đồng bộ
        val themeEditor = getSharedPreferences("THEMES", MODE_PRIVATE)
        val themeIndex = themeEditor.getInt("themeIndex", 0)
        setTheme(MainActivity.currentThemeNav[themeIndex])

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = FirebaseDatabase.getInstance().getReference("accounts")

        binding.registerBtn.setOnClickListener {
            val user = binding.regUsername.text.toString().trim()
            val pass = binding.regPassword.text.toString().trim()
            val confirmPass = binding.regConfirmPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Kiểm tra user trên Firebase
            database.child(user).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show()
                } else {
                    // Mã hóa mật khẩu trước khi lưu
                    val hashedPassword = HashUtils.sha256(pass)
                    
                    val userMap = mapOf(
                        "username" to user,
                        "password" to hashedPassword,
                        "createdAt" to System.currentTimeMillis()
                    )

                    database.child(user).setValue(userMap).addOnSuccessListener {
                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                        finish()
                    }.addOnFailureListener {
                        Toast.makeText(this, "Failed to register", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.backToLogin.setOnClickListener {
            finish()
        }
    }
}
