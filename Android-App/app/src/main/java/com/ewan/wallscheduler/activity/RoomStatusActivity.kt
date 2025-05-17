package com.ewan.wallscheduler.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/***
 * activity that shows the current status of a selected room,
 * updating every second and providing access to the full schedule view.
 * if a room lock is enabled, the user will be prompted for a passcode to exit.
 */
class RoomStatusActivity : BaseFullscreenActivity() {

    // handler used for scheduling periodic updates
    private val handler = Handler(Looper.getMainLooper())

    // runnable that updates the time + checks room status
    private lateinit var timeUpdater: Runnable

    // room id passed in from intent
    private var roomId: Int = -1

    // name of the room shown in the header
    private lateinit var roomName: String

    /*** initial setup of view, data, and status polling */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_view)
        enableEdgeToEdge()

        // retrieve room data from the intent
        roomId = intent.getIntExtra("ROOM_ID", -1)
        roomName = intent.getStringExtra("ROOM_NAME") ?: "Unknown"
        if (roomId == -1) {
            finish()
            return
        }

        // sets the room title text at the top of the screen
        findViewById<TextView>(R.id.roomNumber).text = roomName

        // launches the week view when schedule card is tapped
        findViewById<CardView>(R.id.scheduleCard).setOnClickListener {
            val intent = Intent(this, ScheduleViewActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                putExtra("ROOM_NAME", roomName)
            }
            startActivity(intent)
        }

        // handle back button press with conditional lock prompt
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                CoroutineScope(Dispatchers.IO).launch {
                    val response = RetrofitClient.api.getRoomLockSettings()
                    if (response.isSuccessful) {
                        val settings = response.body()
                        if (settings?.enabled == true) {
                            withContext(Dispatchers.Main) {
                                promptForPasscode(settings.passcode)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                finish()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@RoomStatusActivity,
                                "Error checking lock",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    }
                }
            }
        })

        // begin ticking time updates + evaluate status immediately
        startStatusUpdate()
        evalRoomStatus(LocalDateTime.now())
    }

    /*** schedules the UI to update every second */
    private fun startStatusUpdate() {
        timeUpdater = object : Runnable {
            override fun run() {
                val now = LocalDateTime.now()

                // update clock in top right
                updateDateTime(now)

                // check status at start of each new minute
                if (now.second == 0) {
                    evalRoomStatus(now)
                }

                // rerun this every second
                handler.postDelayed(this, 1000)
            }
        }

        // start the repeating runnable
        handler.post(timeUpdater)
    }

    /*** updates the time and date displays */
    private fun updateDateTime(now: LocalDateTime) {
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val date = now.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))

        findViewById<TextView>(R.id.currentTime).text = time
        findViewById<TextView>(R.id.currentDate).text = date
    }

    /***
     * evaluates the current room status based on current time,
     * then sets the appropriate background and description.
     */
    @SuppressLint("StringFormatMatches")
    private fun evalRoomStatus(now: LocalDateTime) {
        val roomLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        val statusText = findViewById<TextView>(R.id.roomStatus)
        val statusDesc = findViewById<TextView>(R.id.roomStatusDescription)
        val statusSubtext = findViewById<TextView>(R.id.roomStatusSubtext)
        val scheduleCard = findViewById<CardView>(R.id.scheduleCard)
        val backBorder = findViewById<FrameLayout>(R.id.backBorder)

        val roomId = intent.getIntExtra("ROOM_ID", -1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")

        // get schedule from backend
        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.api.getSchedule(roomId, null)
            if (response.isSuccessful && response.body() != null) {
                val bookings = response.body()!!
                    .filter { it.status.equals("approved", ignoreCase = true) }

                // find the booking currently active (if any)
                val currentBooking = bookings.find {
                    val start = LocalDateTime.parse(it.start_time, formatter)
                    val end = LocalDateTime.parse(it.end_time, formatter)
                    now.isAfter(start) && now.isBefore(end)
                }

                // find next upcoming booking after now
                val nextBooking = bookings
                    .mapNotNull {
                        val start = LocalDateTime.parse(it.start_time, formatter)
                        if (start.isAfter(now)) it to start else null
                    }
                    .minByOrNull { it.second }

                // update ui based on logic outcome
                withContext(Dispatchers.Main) {
                    when {
                        // currently occupied
                        currentBooking != null -> {
                            val start = LocalDateTime.parse(currentBooking.start_time, formatter)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                            val end = LocalDateTime.parse(currentBooking.end_time, formatter)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))

                            roomLayout.setBackgroundResource(R.drawable.bg_occupied)
                            statusText.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.occupied_border))
                            statusText.text = getString(R.string.occupied)
                            statusDesc.text = getString(R.string.statusDescCurrentTimeframe, start, end)
                            statusSubtext.text = currentBooking.purpose
                            scheduleCard.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.occupied_border))
                            backBorder.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.occupied_border))
                        }

                        // next booking within 15 minutes
                        nextBooking != null && now.until(nextBooking.second, java.time.temporal.ChronoUnit.MINUTES)
                                <= 15 -> {
                            roomLayout.setBackgroundResource(R.drawable.bg_near)
                            statusText.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.near_border))
                            statusText.text = getString(R.string.near)
                            val minsUntil = now.until(nextBooking.second, java.time.temporal.ChronoUnit.MINUTES)
                            statusDesc.text = getString(R.string.statusDescNextMeeting, minsUntil)
                            statusSubtext.text = nextBooking.first.purpose
                            scheduleCard.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.near_border))
                            backBorder.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.near_border))
                        }

                        // room is available
                        else -> {
                            roomLayout.setBackgroundResource(R.drawable.bg_available)
                            statusText.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.available_border))
                            statusText.text = getString(R.string.available)

                            // if next meeting today, show when
                            if (nextBooking != null && nextBooking.second.toLocalDate() == now.toLocalDate()) {
                                val start = nextBooking.second.format(DateTimeFormatter.ofPattern("HH:mm"))
                                statusDesc.text = getString(R.string.statusDescNextMeetingDay, start)
                                statusSubtext.text = nextBooking.first.purpose
                            } else {
                                statusDesc.text = getString(R.string.statusDescNoMeetings)
                                statusSubtext.text = ""
                            }

                            scheduleCard.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.available_border))
                            backBorder.setBackgroundColor(ContextCompat.getColor(this@RoomStatusActivity,
                                R.color.available_border))
                        }
                    }
                }
            }
        }
    }

    /***
     * prompts user for passcode if leaving is locked
     * @param expected the expected passcode to compare against
     */
    private fun promptForPasscode(expected: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_passcode, null)
        val input = dialogView.findViewById<EditText>(R.id.passcodeInput)
        val cardSubmit = dialogView.findViewById<CardView>(R.id.cardSubmit)
        val cardCancel = dialogView.findViewById<CardView>(R.id.cardCancel)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        cardSubmit.setOnClickListener {
            if (input.text.toString() == expected) {
                dialog.dismiss()
                finish()
            } else {
                Toast.makeText(this, "Incorrect passcode", Toast.LENGTH_SHORT).show()
            }
        }

        cardCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /*** cleans up scheduled updates on exit */
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeUpdater)
    }
}
