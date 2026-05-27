package com.example.mpa23itb234

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Sử dụng theme đồng bộ với ứng dụng
        val themeEditor = getSharedPreferences("THEMES", MODE_PRIVATE)
        val themeIndex = themeEditor.getInt("themeIndex", 0)
        setTheme(MainActivity.currentThemeNav[themeIndex])
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginBtn.setOnClickListener {
            val username = binding.username.text.toString().trim()
            val password = binding.password.text.toString().trim()

            // Kiểm tra trong danh sách người dùng tạm thời
            if (RegisterActivity.tempUsers.containsKey(username) && 
                RegisterActivity.tempUsers[username] == password) {
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Sai tài khoản hoặc mật khẩu!", Toast.LENGTH_SHORT).show()
            }
        }

        // Chuyển sang màn hình đăng ký
        findViewById<android.widget.TextView>(R.id.signUpBtn)?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
