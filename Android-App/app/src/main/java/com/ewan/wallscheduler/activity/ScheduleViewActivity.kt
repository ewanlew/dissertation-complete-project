package com.ewan.wallscheduler.activity

import Booking
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.adapter.ScheduleEntryAdapter
import com.ewan.wallscheduler.fragment.BookingDialogFragment
import com.ewan.wallscheduler.api.RetrofitClient
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.view.WeekCalendarView
import com.kizitonwose.calendar.view.WeekDayBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/***
 * shows a full calendar view and today’s bookings for a given room.
 * users can tap empty timeslots to submit a new booking.
 */
class ScheduleViewActivity : BaseFullscreenActivity() {

    // name of the selected room from intent
    private lateinit var roomName: String

    // id of the selected room from intent
    private var roomId: Int = -1

    // which date is currently selected in the calendar
    private var selectedDate: LocalDate? = null

    // recycler view for today’s list of bookings
    private lateinit var todayRecycler: RecyclerView

    // adapter to manage today’s bookings
    private lateinit var todayAdapter: ScheduleEntryAdapter

    // calendar view for showing weekly layout
    private lateinit var calendarView: WeekCalendarView

    // list of all bookings fetched
    private var allBookings: List<Booking> = emptyList()

    /*** initialises ui, calendar, and loads bookings */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_schedule)
        enableEdgeToEdge()

        // get room name and id from intent
        roomName = intent.getStringExtra("ROOM_NAME") ?: "Unknown"
        roomId = intent.getIntExtra("ROOM_ID", -1)

        // display room name in title bar
        findViewById<TextView>(R.id.roomScheduleTitle).text = getString(R.string.roomSchedule, roomName)

        // setup recycler for today's bookings
        todayRecycler = findViewById(R.id.todayScheduleRecycler)
        todayRecycler.layoutManager = LinearLayoutManager(this)

        // populate list of today's approved bookings
        loadTodaySchedule()

        // setup calendar scrolling from 100 weeks back to 100 weeks forward
        calendarView = findViewById(R.id.calendarView)
        val startDate = LocalDate.now().minusWeeks(100)
        val endDate = LocalDate.now().plusWeeks(100)
        val today = LocalDate.now()

        calendarView.setup(startDate, endDate, DayOfWeek.MONDAY)
        calendarView.scrollToDate(today)

        // fallback blank calendar if nothing loaded
        calendarView.dayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: WeekDay) {
                container.dayText.text = data.date.dayOfMonth.toString()
                container.dayLabel.text = data.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                container.timeGrid.removeAllViews()
                container.eventOverlay.removeAllViews()
            }
        }

        // once all bookings are loaded, bind calendar fully
        loadFullSchedule { bookingsByDate ->
            calendarView.dayBinder = getDayBinder(bookingsByDate)
        }

        // back button returns to previous screen
        findViewById<CardView>(R.id.returnToRoomCard).setOnClickListener {
            finish()
        }
    }

    /*** provides dynamic binding logic for every day cell in the calendar view */
    private fun getDayBinder(bookingsByDate: Map<LocalDate, List<Booking>>): WeekDayBinder<DayViewContainer> {
        val today = LocalDate.now()

        return object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View): DayViewContainer = DayViewContainer(view)

            override fun bind(container: DayViewContainer, data: WeekDay) {
                val date = data.date
                val events = bookingsByDate[date].orEmpty()

                container.dayText.text = date.dayOfMonth.toString()
                container.dayLabel.text = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar {
                    it.uppercase() }

                // highlight today’s date
                if (date == today) {
                    container.dayText.setBackgroundResource(R.drawable.bg_today_highlight)
                } else {
                    container.dayText.setBackgroundResource(0)
                }

                // remove all previous views
                container.timeGrid.removeAllViews()
                container.eventOverlay.removeAllViews()

                // height of each hour row in pixels
                val hourHeightPx = (50 * container.view.resources.displayMetrics.density).toInt()
                val dividerHeightPx = (2 * container.view.resources.displayMetrics.density).toInt()

                // build each time slot for the day (8am–7pm)
                for (hour in 8..19) {
                    val row = LinearLayout(container.view.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            hourHeightPx
                        )
                        orientation = LinearLayout.VERTICAL

                        // clicking a free hour launches booking popup with prefilled range
                        setOnClickListener {
                            val tappedStart = date.atTime(hour, 0)
                            val tappedEnd = tappedStart.plusHours(1)

                            val dialog = BookingDialogFragment.newInstance(
                                roomId = this@ScheduleViewActivity.roomId,
                                roomName = this@ScheduleViewActivity.roomName,
                                startTime = tappedStart,
                                endTime = tappedEnd,
                                onBookingSuccess = {
                                    loadTodaySchedule()
                                    loadFullSchedule { newMap ->
                                        calendarView.dayBinder = getDayBinder(newMap)
                                    }
                                }
                            )
                            dialog.show(supportFragmentManager, "BookingDialog")
                        }
                    }

                    // visual separator for each hour block
                    val divider = View(container.view.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dividerHeightPx
                        )
                        setBackgroundColor(ContextCompat.getColor(context, R.color.calendar_divider))
                    }

                    row.addView(divider)
                    container.timeGrid.addView(row)
                }

                // draw every event block on top of hour rows
                for (event in events) {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]")
                    val backendZone = java.time.ZoneId.of("UTC")
                    val deviceZone = java.time.ZoneId.systemDefault()

                    // convert backend UTC time to device local time
                    val start = LocalDateTime.parse(event.start_time, formatter)
                        .atZone(backendZone)
                        .withZoneSameInstant(deviceZone)
                        .toLocalDateTime()

                    val end = LocalDateTime.parse(event.end_time, formatter)
                        .atZone(backendZone)
                        .withZoneSameInstant(deviceZone)
                        .toLocalDateTime()

                    // calculate block positioning based on start and duration
                    val minutesSince8am = (start.hour - 8) * 60 + start.minute
                    val durationMinutes = java.time.Duration.between(start, end).toMinutes().toInt()

                    val offsetY = (minutesSince8am * hourHeightPx) / 60
                    val height = (durationMinutes * hourHeightPx) / 60

                    val eventView = View(container.view.context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            height
                        ).apply {
                            topMargin = offsetY + dpToPx(12)
                            marginStart = 4
                            marginEnd = 4
                        }

                        // colour depending on booking status
                        val colour = when (event.status.lowercase()) {
                            "approved" -> ContextCompat.getColor(context, R.color.calendar_red)
                            "pending" -> ContextCompat.getColor(context, R.color.calendar_orange)
                            else -> ContextCompat.getColor(context, android.R.color.darker_gray)
                        }
                        setBackgroundColor(colour)

                        // show purpose as toast on tap
                        setOnClickListener {
                            Toast.makeText(context, event.purpose, Toast.LENGTH_SHORT).show()
                        }
                    }
                    container.eventOverlay.addView(eventView)
                }

                // update selected highlight if user taps date
                container.view.setOnClickListener {
                    if (selectedDate != date) {
                        selectedDate?.let { calendarView.notifyDateChanged(it) }
                        selectedDate = date
                        calendarView.notifyDateChanged(date)
                    }
                }
            }
        }
    }

    /*** loads today's schedule and wires it to recycler view */
    private fun loadTodaySchedule() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]")
        val today = LocalDate.now()

        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.api.getSchedule(roomId, null)
            if (response.isSuccessful && response.body() != null) {
                // only include approved bookings
                val bookings = response.body()!!.filter { it.status.lowercase() == "approved" }
                allBookings = bookings

                val todayBookings = bookings.filter {
                    LocalDateTime.parse(it.start_time, formatter).toLocalDate() == today
                }

                withContext(Dispatchers.Main) {
                    todayAdapter = ScheduleEntryAdapter(todayBookings)
                    todayRecycler.adapter = todayAdapter
                }

                // calendar redraws
                withContext(Dispatchers.Main) {
                    findViewById<WeekCalendarView>(R.id.calendarView).notifyCalendarChanged()
                }
            }
        }
    }

    /*** loads full schedule and passes grouped data to callback */
    private fun loadFullSchedule(onComplete: (Map<LocalDate, List<Booking>>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.api.getSchedule(roomId, null)
            if (response.isSuccessful && response.body() != null) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]")
                val body = response.body()

                if (body == null) {
                    withContext(Dispatchers.Main) { onComplete(emptyMap()) }
                    return@launch
                }

                val bookings = body
                    .filter { it.status.lowercase() != "denied" }
                    .map {
                        it.copy(risk_assessment = it.risk_assessment ?: "")
                    }

                val bookingsByDate = bookings.groupBy {
                    LocalDateTime.parse(it.start_time, formatter).toLocalDate()
                }

                withContext(Dispatchers.Main) {
                    onComplete(bookingsByDate)
                }
            }
        }
    }

    /*** converts dp into px based on screen density */
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /*** view container for a single calendar day cell */
    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val dayLabel: TextView = view.findViewById(R.id.dayLabel)
        val dayText: TextView = view.findViewById(R.id.dayText)
        val timeGrid: LinearLayout = view.findViewById(R.id.timeGrid)
        val eventOverlay: FrameLayout = view.findViewById(R.id.eventOverlay)
    }
}
