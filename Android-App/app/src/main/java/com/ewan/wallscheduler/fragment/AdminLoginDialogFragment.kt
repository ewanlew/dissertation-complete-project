package com.ewan.wallscheduler.fragment

import AdminLogin
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/***
 * this fragment handles login functionality for admins.
 * it uses a transparent dialog layout and performs a retrofit call to verify credentials.
 * @param onLoginSuccess callback invoked if credentials are valid.
 */
class AdminLoginDialogFragment(
    private val onLoginSuccess: () -> Unit
) : DialogFragment() {

    /*** inflates the login dialog view ***/
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_login_dialog, container, false)
    }

    /*** makes the dialog fullscreen and translucent when it starts ***/
    override fun onStart() {
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar)
        super.onStart()
    }

    /*** sets up logic and click handlers after the view is inflated ***/
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // input fields
        val usernameField = view.findViewById<EditText>(R.id.usernameEntry)
        val passwordField = view.findViewById<EditText>(R.id.passwordEntry)

        // buttons
        val submitBtn = view.findViewById<Button>(R.id.btnSubmit)
        val cancelBtn = view.findViewById<Button>(R.id.btnCancel)

        // dismisses dialog when cancelled
        cancelBtn.setOnClickListener { dismiss() }

        // handles admin login when submit is pressed
        submitBtn.setOnClickListener {
            val username = usernameField.text.toString().trim()
            val password = passwordField.text.toString()

            // early return if any fields are missing
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            // packages up login payload
            val loginRequest = AdminLogin(username, password)

            // attempts login on background thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitClient.api.login(loginRequest)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(requireContext(), "Login successful", Toast.LENGTH_SHORT)
                                .show()
                            dismiss()
                            onLoginSuccess()
                        } else {
                            Toast.makeText(requireContext(), "Login failed", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    // catches any thrown network errors
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    /*** factory function to create new login fragment with a callback ***/
    companion object {
        fun newInstance(onLoginSuccess: () -> Unit): AdminLoginDialogFragment {
            return AdminLoginDialogFragment(onLoginSuccess)
        }
    }
}
