package com.example.mpa23itb234

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityLoginBinding
import com.google.firebase.database.FirebaseDatabase

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

        val database = FirebaseDatabase.getInstance().getReference("accounts")

        binding.loginBtn.setOnClickListener {
            val username = binding.username.text.toString().trim()
            val password = binding.password.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Kiểm tra trên Firebase
            database.child(username).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val dbHashedPassword = snapshot.child("password").value.toString()
                    val inputHashedPassword = HashUtils.sha256(password)

                    if (dbHashedPassword == inputHashedPassword) {
                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("userName", username)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Sai mật khẩu!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Fallback cho tài khoản cứng abc/123 nếu chưa có db (tùy chọn)
                    if (username == "abc" && password == "123") {
                         val intent = Intent(this, MainActivity::class.java)
                         intent.putExtra("userName", "abc")
                         startActivity(intent)
                         finish()
                    } else {
                        Toast.makeText(this, "Tài khoản không tồn tại!", Toast.LENGTH_SHORT).show()
                    }
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Lỗi kết nối database", Toast.LENGTH_SHORT).show()
            }
        }

        // Chuyển sang màn hình đăng ký
        binding.signUpBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
