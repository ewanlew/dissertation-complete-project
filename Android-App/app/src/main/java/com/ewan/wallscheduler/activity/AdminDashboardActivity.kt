package com.ewan.wallscheduler.activity

import Booking
import RoomLockSettings
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.adapter.PendingBookingAdapter
import com.ewan.wallscheduler.api.RetrofitClient
import com.ewan.wallscheduler.util.CustomTypefaceSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AdminDashboardActivity : BaseFullscreenActivity() {

    /*** recycler view for showing pending bookings in a scrollable list */
    private lateinit var pendingRecycler: RecyclerView

    /*** adapter to handle binding bookings to recycler items */
    private lateinit var pendingAdapter: PendingBookingAdapter

    /*** list holding all current pending bookings */
    private val pendingBookings = mutableListOf<Booking>()

    /*** map for quickly looking up room name via room id (maps room_id -> (building, room)) */
    private val roomNameLookup = mutableMapOf<Int, Pair<String, String>>()

    /*** textview to show full booking details */
    private lateinit var detailInfo: TextView

    /*** textview that shows risk assessment text */
    private lateinit var riskText: TextView

    /*** card used to approve a selected booking */
    private lateinit var cardApprove: CardView

    /*** card used to reject a selected booking */
    private lateinit var cardReject: CardView

    /*** currently selected booking, if any */
    private var selectedBooking: Booking? = null

    /*** called when the activity is first created and displayed */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_view)

        val requirePasscodeCheckbox = findViewById<CheckBox>(R.id.requirePasscodeCheckBox)
        val passcodeEntry = findViewById<EditText>(R.id.passcodeEntry)
        val cardApply = findViewById<CardView>(R.id.applyCard)

        detailInfo = findViewById(R.id.detailInfo)
        riskText = findViewById(R.id.riskEvalText)
        cardApprove = findViewById(R.id.cardApprove)
        cardReject = findViewById(R.id.cardReject)

        pendingRecycler = findViewById(R.id.pendingBookingsRecycler)
        pendingRecycler.layoutManager = LinearLayoutManager(this)

        /*** fetches the passcode requirement settings and displays them */
        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.api.getRoomLockSettings()
            if (response.isSuccessful) {
                val settings = response.body()
                if (settings != null) {
                    withContext(Dispatchers.Main) {
                        requirePasscodeCheckbox.isChecked = settings.enabled
                        passcodeEntry.setText(settings.passcode)
                    }
                }
            }
        }

        /*** updates the room lock settings when "apply" is clicked */
        cardApply.setOnClickListener {
            val passcode = passcodeEntry.text.toString()
            val enabled = requirePasscodeCheckbox.isChecked

            CoroutineScope(Dispatchers.IO).launch {
                val update = RoomLockSettings(enabled, passcode)
                val response = RetrofitClient.api.setRoomLockSettings(update)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AdminDashboardActivity, "Settings updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@AdminDashboardActivity, "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        /*** returns to the building list screen */
        findViewById<CardView>(R.id.returnToBuildingsCard).setOnClickListener {
            finish()
        }

        /*** fetches all room + building data, then pending bookings */
        CoroutineScope(Dispatchers.IO).launch {
            val roomsResponse = RetrofitClient.api.getAllRooms()
            val buildingsResponse = RetrofitClient.api.getBuildings()

            if (
                roomsResponse.isSuccessful && roomsResponse.body() != null &&
                buildingsResponse.isSuccessful && buildingsResponse.body() != null
            ) {
                val allRooms = roomsResponse.body()!!
                val allBuildings = buildingsResponse.body()!!

                val buildingMap = allBuildings.associateBy { it.id }

                roomNameLookup.putAll(
                    allRooms.associate { room ->
                        val buildingName = buildingMap[room.building_id]?.name ?: "Unknown"
                        room.id to (buildingName to room.name)
                    }
                )

                val bookingsResponse = RetrofitClient.api.getPendingBookings()
                if (bookingsResponse.isSuccessful && bookingsResponse.body() != null) {
                    val bookings = bookingsResponse.body()!!

                    withContext(Dispatchers.Main) {
                        pendingBookings.clear()
                        pendingBookings.addAll(bookings)

                        /*** sets up adapter and item click listener */
                        pendingAdapter = PendingBookingAdapter(
                            pendingBookings,
                            roomNameLookup,
                            onItemClicked = { booking ->
                                selectedBooking = booking

                                val (building, room) = roomNameLookup[booking.room_id] ?: ("Unknown" to "Room #${booking.room_id}")
                                val inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")
                                val outputFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

                                val start = LocalDateTime.parse(booking.start_time, inputFormat).format(outputFormat)
                                val end = LocalDateTime.parse(booking.end_time, inputFormat).format(outputFormat)

                                val semibold = ResourcesCompat.getFont(this@AdminDashboardActivity, R.font.dmsans_bold)!!
                                val regular = ResourcesCompat.getFont(this@AdminDashboardActivity, R.font.dmsans_regular)!!

                                val builder = SpannableStringBuilder()

                                /*** appends bolded label and regular value with optional newline */
                                fun appendStyled(label: String, value: String, newline: Boolean? = true) {
                                    val startLabel = builder.length
                                    builder.append(label)
                                    builder.setSpan(CustomTypefaceSpan(semibold), startLabel, startLabel + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                                    val startValue = builder.length
                                    builder.append(value)
                                    builder.setSpan(CustomTypefaceSpan(regular), startValue, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                                    if (newline ?: true) { builder.append("\n") }
                                }

                                appendStyled("Purpose: ", booking.purpose)
                                appendStyled("Email: ", booking.user_email)
                                appendStyled("Room: ", "$building - $room")
                                appendStyled("Attendees: ", "${booking.attendees}")
                                appendStyled("Timeframe: ", "$start – $end", false)

                                detailInfo.text = builder
                                riskText.text = booking.risk_assessment
                            }
                        )

                        pendingRecycler.adapter = pendingAdapter
                    }
                }
            }
        }

        /*** handles approve button click */
        cardApprove.setOnClickListener {
            selectedBooking?.let { booking ->
                CoroutineScope(Dispatchers.IO).launch {
                    val response = RetrofitClient.api.approveBooking(booking.id)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@AdminDashboardActivity, "Approved", Toast.LENGTH_SHORT).show()
                            removeBooking(booking)
                        } else {
                            Toast.makeText(this@AdminDashboardActivity, "Failed to approve", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        /*** handles reject button click */
        cardReject.setOnClickListener {
            selectedBooking?.let { booking ->
                CoroutineScope(Dispatchers.IO).launch {
                    val response = RetrofitClient.api.denyBooking(booking.id)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@AdminDashboardActivity, "Rejected", Toast.LENGTH_SHORT).show()
                            removeBooking(booking)
                        } else {
                            Toast.makeText(this@AdminDashboardActivity, "Failed to reject", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /*** removes a booking from the list and clears the detail panel */
    private fun removeBooking(booking: Booking) {
        pendingBookings.remove(booking)
        pendingAdapter.update(pendingBookings)
        selectedBooking = null
        detailInfo.setText(R.string.selectBookingPrompt)
        riskText.setText(R.string.riskEvaluationAppearsHere)
    }
}
