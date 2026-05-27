package com.example.mpa23itb234

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Theme sync
        val themeEditor = getSharedPreferences("THEMES", MODE_PRIVATE)
        val themeIndex = themeEditor.getInt("themeIndex", 0)
        setTheme(MainActivity.currentThemeNav[themeIndex])

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set User Info
        val currentUser = intent.getStringExtra("userName") ?: "Guest"
        binding.profileName.text = currentUser

        // Setup Toolbar
        binding.profileToolbar.setNavigationOnClickListener {
            finish()
        }

        // Add Music
        binding.profileAddMusic.setOnClickListener {
            // Quay về MainActivity để dùng hàm uploadMusicDialog() hoặc mở dialog tại đây
            // Ở đây mình ví dụ gọi intent để MainActivity xử lý
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("action", "upload")
            startActivity(intent)
        }

        // Manage Playlists
        binding.profileMyPlaylists.setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }

        // Settings
        binding.profileSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Logout
        binding.profileLogout.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
