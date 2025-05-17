package com.ewan.wallscheduler.fragment

import Booking
import BookingRequest
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.api.Credentials
import com.ewan.wallscheduler.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/***
 * dialog fragment that allows users to submit a new booking for a room.
 * handles booking details, date/time validation, overlap detection, and confirmation.
 */
class BookingDialogFragment(
    private val roomId: Int,
    private val prefillStart: LocalDateTime,
    private val prefillEnd: LocalDateTime,
    private var existingBookings: List<Booking> = emptyList(),
    private val onBookingSuccess: (() -> Unit)? = null
) : DialogFragment() {

    /*** sets the dialog to fullscreen dimensions */
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    /*** hides system ui (fullscreen immersive mode) */
    override fun onResume() {
        super.onResume()
        dialog?.window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    /*** inflates the main layout and applies transparent background */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.fragment_schedule_room, container, false)
    }

    /*** main logic for wiring up fields, validators, and the submit action */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // inputs and controls
        val purposeField = view.findViewById<EditText>(R.id.purposeEntry)
        val emailField = view.findViewById<EditText>(R.id.emailEntry)
        val attendeesField = view.findViewById<EditText>(R.id.atendeesEntry)
        val startTimeField = view.findViewById<EditText>(R.id.startTimeEntry)
        val endTimeField = view.findViewById<EditText>(R.id.endingTimeEntry)
        val roomLabel = view.findViewById<TextView>(R.id.roomBeingBookedLabel)
        val riskField = view.findViewById<EditText>(R.id.riskEvaluationEntry)
        val rulesCheck = view.findViewById<CheckBox>(R.id.agreeToRulesEntry)
        val submitBtn = view.findViewById<Button>(R.id.btnSubmit)
        val cancelBtn = view.findViewById<Button>(R.id.btnCancel)

        // autofill user email
        view.findViewById<TextView>(R.id.emailText).setOnClickListener {
            emailField.setText(Credentials.EMAIL)
        }

        val roomName = arguments?.getString("ROOM_NAME") ?: "Unknown"
        val displayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val storageFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // set default values for the time fields
        startTimeField.setText(prefillStart.format(displayFormat))
        endTimeField.setText(prefillEnd.format(displayFormat))
        roomLabel.text = roomName

        /*** validates all input fields, times, and overlap */
        fun validator() {
            val hasAllFields = !purposeField.text.isNullOrBlank() &&
                    !emailField.text.isNullOrBlank() &&
                    !attendeesField.text.isNullOrBlank() &&
                    !startTimeField.text.isNullOrBlank() &&
                    !endTimeField.text.isNullOrBlank() &&
                    !riskField.text.isNullOrBlank() &&
                    rulesCheck.isChecked

            if (!hasAllFields) {
                submitBtn.isEnabled = false
                return
            }

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]")
            val start = LocalDateTime.parse(startTimeField.text.toString(), displayFormat)
            val end = LocalDateTime.parse(endTimeField.text.toString(), displayFormat)

            val isInRange = start.hour in 8..18 && end.hour in 9..19
            val isChronological = start.isBefore(end)

            val overlaps = existingBookings.any { booking ->
                val existingStart = LocalDateTime.parse(booking.start_time, formatter)
                val existingEnd = LocalDateTime.parse(booking.end_time, formatter)
                start < existingEnd && end > existingStart
            }

            submitBtn.isEnabled = isInRange && isChronological && !overlaps
        }

        /*** pulls all approved and pending bookings for this room */
        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.api.getSchedule(roomId, null)
            if (response.isSuccessful && response.body() != null) {
                existingBookings = response.body()!!.filter {
                    it.status.equals("approved", true) || it.status.equals("pending", true)
                }
                withContext(Dispatchers.Main) {
                    validator()
                }
            }
        }

        /*** shows a date and time picker, combining them together into a single datetime result */
        fun showDateTimePicker(initial: LocalDateTime, onResult: (LocalDateTime) -> Unit) {
            val date = initial.toLocalDate()
            val time = initial.toLocalTime()

            val datePicker = android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)

                val timePicker = android.app.TimePickerDialog(requireContext(), { _, hour, minute ->
                    val selectedDateTime = selectedDate.atTime(hour, minute)
                    onResult(selectedDateTime)
                }, time.hour, time.minute, true)

                timePicker.show()
            }, date.year, date.monthValue - 1, date.dayOfMonth)

            datePicker.show()
        }

        // open date/time picker when either time field is tapped
        startTimeField.setOnClickListener {
            showDateTimePicker(prefillStart) { selected ->
                startTimeField.setText(selected.format(displayFormat))
                validator()
            }
        }

        endTimeField.setOnClickListener {
            showDateTimePicker(prefillEnd) { selected ->
                endTimeField.setText(selected.format(displayFormat))
                validator()
            }
        }

        // monitor text inputs and revalidate whenever they change
        listOf(purposeField, emailField, attendeesField, startTimeField, endTimeField, riskField).forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    validator()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        // revalidate if rules checkbox is toggled
        rulesCheck.setOnCheckedChangeListener { _, _ -> validator() }

        // dismiss the popup
        cancelBtn.setOnClickListener { dismiss() }

        /*** sends the booking to the backend and displays the confirmation dialog */
        submitBtn.setOnClickListener {
            submitBtn.isEnabled = false
            val booking = BookingRequest(
                room_id = roomId,
                user_email = emailField.text.toString(),
                purpose = purposeField.text.toString(),
                attendees = attendeesField.text.toString().toInt(),
                start_time = LocalDateTime.parse(startTimeField.text.toString(), displayFormat).format(storageFormat),
                end_time = LocalDateTime.parse(endTimeField.text.toString(), displayFormat).format(storageFormat),
                risk_assessment = riskField.text.toString()
            )

            CoroutineScope(Dispatchers.IO).launch {
                val response = RetrofitClient.api.makeBooking(booking)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        // inflate and show success dialog
                        val dialogView = LayoutInflater.from(requireContext())
                            .inflate(R.layout.dialog_booking_success, null)

                        val successDialog = AlertDialog.Builder(requireContext())
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()

                        dialogView.findViewById<CardView>(R.id.cardOkay).setOnClickListener {
                            successDialog.dismiss()
                            dismiss()
                            onBookingSuccess?.invoke() // refresh calendar to show pending booking
                        }

                        successDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        successDialog.show()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Toast.makeText(requireContext(), "Error: ${response.code()} $errorBody", Toast.LENGTH_LONG).show()
                    }
                }
            }
            submitBtn.isEnabled = true
        }
    }

    /*** creates a new instance of the fragment with arguments pre-attached */
    companion object {
        fun newInstance(roomId: Int,
                        roomName: String,
                        startTime: LocalDateTime,
                        endTime: LocalDateTime,
                        onBookingSuccess: (() -> Unit)? = null): BookingDialogFragment {
            return BookingDialogFragment(
                roomId = roomId,
                prefillStart = startTime,
                prefillEnd = endTime,
                onBookingSuccess = onBookingSuccess
            ).apply {
                arguments = Bundle().apply {
                    putString("ROOM_NAME", roomName)
                }
            }
        }
    }
}
