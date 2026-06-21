package com.example.mpa23itb234

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mpa23itb234.databinding.ActivityAuthBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase

/**
 * Màn hình xác thực duy nhất của ứng dụng.
 *
 * Hỗ trợ cả đăng nhập và đăng ký bằng Firebase Authentication; thông tin
 * username bổ sung được lưu tại node users/{uid}.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private var isRegisterMode = false

    /** Khởi tạo Firebase Auth, bỏ qua màn hình nếu đã đăng nhập và gắn sự kiện form. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.ACTIVE_THEME)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            openMainActivity()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.authButton.setOnClickListener { submitAuthForm() }
        binding.switchModeButton.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateMode()
        }
    }

    /** Chuyển giao diện giữa chế độ đăng nhập và đăng ký. */
    private fun updateMode() {
        clearErrors()
        binding.titleText.setText(if (isRegisterMode) R.string.register_title else R.string.login_title)
        binding.subtitleText.setText(if (isRegisterMode) R.string.register_subtitle else R.string.login_subtitle)
        binding.authButton.setText(if (isRegisterMode) R.string.register else R.string.login)
        binding.switchModeButton.setText(if (isRegisterMode) R.string.switch_to_login else R.string.switch_to_register)
        binding.usernameLayout.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        binding.confirmPasswordLayout.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
    }

    /** Kiểm tra form rồi gọi API đăng nhập hoặc tạo tài khoản Firebase. */
    private fun submitAuthForm() {
        clearErrors()

        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val confirmPassword = binding.confirmPasswordInput.text?.toString().orEmpty()

        if (!validateForm(email, username, password, confirmPassword)) return

        setLoading(true)
        if (isRegisterMode) {
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { result ->
                if (result.isSuccessful) {
                    saveUserProfile(username, email)
                } else {
                    setLoading(false)
                    showAuthError(result.exception)
                }
            }
        } else {
            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this) { result ->
                setLoading(false)
                if (result.isSuccessful) {
                    openMainActivity()
                } else {
                    showAuthError(result.exception)
                }
            }
        }
    }

    /** Xác thực dữ liệu đầu vào và hiển thị lỗi ngay tại từng trường. */
    private fun validateForm(email: String, username: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (email.isBlank()) {
            binding.emailLayout.error = getString(R.string.auth_email_required)
            isValid = false
        }

        if (isRegisterMode && username.isBlank()) {
            binding.usernameLayout.error = getString(R.string.auth_username_required)
            isValid = false
        }

        if (password.isBlank()) {
            binding.passwordLayout.error = getString(R.string.auth_password_required)
            isValid = false
        } else if (password.length < 6) {
            binding.passwordLayout.error = getString(R.string.auth_password_short)
            isValid = false
        }

        if (isRegisterMode && password != confirmPassword) {
            binding.confirmPasswordLayout.error = getString(R.string.auth_password_mismatch)
            isValid = false
        }

        return isValid
    }

    /** Xóa lỗi cũ trước mỗi lần đổi chế độ hoặc gửi form. */
    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.usernameLayout.error = null
        binding.passwordLayout.error = null
        binding.confirmPasswordLayout.error = null
    }

    /** Lưu hồ sơ cơ bản vào Realtime Database sau khi đăng ký thành công. */
    private fun saveUserProfile(username: String, email: String) {
        val uid = auth.currentUser?.uid ?: return
        val userMap = mapOf(
            "uid" to uid,
            "username" to username,
            "email" to email,
            "createdAt" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance().getReference("users").child(uid).setValue(userMap)
            .addOnCompleteListener { result ->
                setLoading(false)
                if (result.isSuccessful) {
                    openMainActivity()
                } else {
                    Toast.makeText(this, getString(R.string.auth_profile_save_failed), Toast.LENGTH_LONG).show()
                }
            }
    }

    /** Hiển thị thông báo xác thực đã được chuyển sang nội dung tiếng Việt. */
    private fun showAuthError(error: Exception?) {
        Toast.makeText(this, authErrorMessage(error), Toast.LENGTH_LONG).show()
    }

    /** Ánh xạ exception Firebase thành thông báo thân thiện cho người dùng. */
    private fun authErrorMessage(error: Exception?): String {
        val message = error?.message.orEmpty()
        return when {
            error is FirebaseAuthUserCollisionException ->
                getString(R.string.auth_email_in_use)
            error is FirebaseAuthInvalidUserException ->
                getString(R.string.auth_user_not_found)
            error is FirebaseNetworkException ->
                getString(R.string.auth_network_error)
            error is FirebaseAuthInvalidCredentialsException &&
                message.contains("email", ignoreCase = true) ->
                getString(R.string.auth_invalid_email)
            error is FirebaseAuthInvalidCredentialsException ->
                getString(R.string.auth_invalid_credentials)
            message.contains("password", ignoreCase = true) ->
                getString(R.string.auth_invalid_credentials)
            message.contains("network", ignoreCase = true) ->
                getString(R.string.auth_network_error)
            else -> getString(R.string.auth_failed)
        }
    }

    /** Khóa nút thao tác trong lúc Firebase đang xử lý yêu cầu. */
    private fun setLoading(isLoading: Boolean) {
        binding.authButton.isEnabled = !isLoading
        binding.switchModeButton.isEnabled = !isLoading
    }

    /** Mở màn hình chính, xóa back stack và reset phiên đồng bộ thư viện. */
    private fun openMainActivity() {
        FirebaseLibraryStore.resetSession()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
