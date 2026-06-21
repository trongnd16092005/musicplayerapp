package com.example.mpa23itb234

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.mpa23itb234.databinding.ActivitySettingsBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** Màn hình quản lý tài khoản, giao diện và cách sắp xếp thư viện nhạc. */
class SettingsActivity : AppCompatActivity() {

    lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val songsRef = FirebaseDatabase.getInstance().getReference("songs")
    private var currentUsername = ""

    /** Khởi tạo dữ liệu tài khoản và gắn sự kiện cho các tùy chọn cài đặt. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.ACTIVE_NAV_THEME)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        auth = FirebaseAuth.getInstance()
        loadAccountInfo()
        binding.changeUsernameBtn.setOnClickListener { showChangeUsernameDialog() }
        binding.changePasswordBtn.setOnClickListener { showChangePasswordDialog() }
        binding.sortBtn.setOnClickListener {
            val menuList = arrayOf(
                getString(R.string.sort_recently_added),
                getString(R.string.sort_song_title),
                getString(R.string.sort_file_size)
            )
            var currentSort = MainActivity.sortOrder
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(getString(R.string.sorting_title))
                .setPositiveButton(getString(R.string.ok)){ _, _ ->
                    val editor = getSharedPreferences("SORTING", MODE_PRIVATE).edit()
                    editor.putInt("sortOrder", currentSort)
                    editor.apply()
                }
                .setSingleChoiceItems(menuList, currentSort){ _,which->
                    currentSort = which
                }
            val customDialog = builder.create()
            customDialog.show()

            setDialogBtnBackground(this, customDialog)
        }
    }

    /** Xử lý nút quay lại trên ActionBar. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Tải username từ Firebase và email từ Firebase Authentication. */
    private fun loadAccountInfo() {
        val user = auth.currentUser ?: return
        usersRef.child(user.uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUsername = snapshot.child("username").getValue(String::class.java)
                    ?: user.email?.substringBefore("@").orEmpty()
                updateAccountViews(currentUsername, user.email ?: "Unknown")
            }

            override fun onCancelled(error: DatabaseError) {
                currentUsername = user.email?.substringBefore("@").orEmpty()
                updateAccountViews(currentUsername, user.email ?: "Unknown")
            }
        })
    }

    /** Cập nhật hai trường thông tin tài khoản trên giao diện. */
    private fun updateAccountViews(username: String, email: String) {
        binding.accountUsername.text = username
        binding.accountEmail.text = email
    }

    /** Hiển thị form đổi username và kiểm tra dữ liệu rỗng. */
    private fun showChangeUsernameDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.new_username)
            setText(currentUsername)
            setSingleLine()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.change_username))
            .setView(input)
            .setPositiveButton(getString(R.string.ok), null)
            .setNegativeButton(getString(R.string.no), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = input.text.toString().trim()
                if (username.isBlank()) {
                    input.error = getString(R.string.auth_username_required)
                } else {
                    updateUsername(username)
                    dialog.dismiss()
                }
            }
            setDialogBtnBackground(this, dialog)
        }
        dialog.show()
    }

    /** Cập nhật username tại users/{uid} và các bài hát thuộc người dùng. */
    private fun updateUsername(newUsername: String) {
        val user = auth.currentUser ?: return
        val userUpdates = mapOf<String, Any>(
            "uid" to user.uid,
            "username" to newUsername,
            "email" to (user.email ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )

        usersRef.child(user.uid).updateChildren(userUpdates)
            .addOnSuccessListener {
                currentUsername = newUsername
                updateAccountViews(newUsername, user.email ?: "Unknown")
                updateOwnedSongsUsername(user.uid, newUsername)
                Toast.makeText(this, getString(R.string.username_updated), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.update_failed, it.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
    }

    /** Đồng bộ tên chủ sở hữu trên mọi bài hát có ownerUid tương ứng. */
    private fun updateOwnedSongsUsername(uid: String, newUsername: String) {
        songsRef.orderByChild("ownerUid").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (songSnap in snapshot.children) {
                        songSnap.ref.updateChildren(
                            mapOf(
                                "ownerUsername" to newUsername,
                                "artist" to newUsername,
                                "album" to newUsername,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /** Hiển thị form nhập mật khẩu hiện tại và mật khẩu mới. */
    private fun showChangePasswordDialog() {
        val currentPassword = passwordInput(getString(R.string.current_password))
        val newPassword = passwordInput(getString(R.string.new_password))
        val confirmPassword = passwordInput(getString(R.string.confirm_new_password))
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
            addView(currentPassword)
            addView(newPassword)
            addView(confirmPassword)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.change_password))
            .setView(container)
            .setPositiveButton(getString(R.string.ok), null)
            .setNegativeButton(getString(R.string.no), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val oldPass = currentPassword.text.toString()
                val newPass = newPassword.text.toString()
                val confirm = confirmPassword.text.toString()

                when {
                    oldPass.isBlank() -> currentPassword.error = getString(R.string.auth_password_required)
                    newPass.length < 6 -> newPassword.error = getString(R.string.auth_password_short)
                    newPass != confirm -> confirmPassword.error = getString(R.string.password_not_match)
                    else -> {
                        updatePassword(oldPass, newPass)
                        dialog.dismiss()
                    }
                }
            }
            setDialogBtnBackground(this, dialog)
        }
        dialog.show()
    }

    /** Tạo EditText mật khẩu dùng chung cho dialog đổi mật khẩu. */
    private fun passwordInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
    }

    /** Xác thực lại tài khoản trước khi gọi Firebase cập nhật mật khẩu. */
    private fun updatePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.password_updated), Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, getString(R.string.update_failed, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, getString(R.string.update_failed, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
    }

}
