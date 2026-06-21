package com.example.mpa23itb234

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.ACTIVE_THEME)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.aboutToolbar.title = getString(R.string.about)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        binding.aboutText.text = aboutText()
    }

    private fun aboutText(): String{
        return "Music Player App" +
                "\n\nNguyễn Đức Trọng-23IT.B234"+
                "\n\nVăn Khánh Trình-23IT.B233"

    }
}
