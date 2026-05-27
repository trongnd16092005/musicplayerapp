package com.example.mpa23itb234

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    companion object {
        // Lưu trữ tạm thời danh sách người dùng (Key: Username, Value: Password)
        val tempUsers = mutableMapOf<String, String>().apply {
            put("abc", "123") // Mặc định tài khoản cũ vẫn dùng được
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sử dụng theme đồng bộ
        val themeEditor = getSharedPreferences("THEMES", MODE_PRIVATE)
        val themeIndex = themeEditor.getInt("themeIndex", 0)
        setTheme(MainActivity.currentThemeNav[themeIndex])

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

            if (tempUsers.containsKey(user)) {
                Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Lưu vào map tạm thời
            tempUsers[user] = pass
            Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
            
            // Quay về màn hình Login
            finish()
        }

        binding.backToLogin.setOnClickListener {
            finish()
        }
    }
}
