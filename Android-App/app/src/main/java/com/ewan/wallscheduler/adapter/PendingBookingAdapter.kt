package com.ewan.wallscheduler.adapter

import Booking
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ewan.wallscheduler.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/***
 * recycler view adapter used to show the list of pending bookings in the admin panel.
 * uses a custom item layout and formats each booking with its room, building, attendees and time.
 */
class PendingBookingAdapter(
    private var bookings: List<Booking>,
    private val roomNameLookup: Map<Int, Pair<String, String>>, // maps room_id -> (building, room)
    private val onItemClicked: (Booking) -> Unit // callback when a booking is tapped
) : RecyclerView.Adapter<PendingBookingAdapter.BookingViewHolder>() {

    /*** viewholder representing a single pending booking item */
    inner class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roomName = view.findViewById<TextView>(R.id.bookingRoomName) // room name text
        val buildingName = view.findViewById<TextView>(R.id.bookingBuildingName) // building name text
        val attendees = view.findViewById<TextView>(R.id.bookingAttendees) // number of attendees
        val startTime = view.findViewById<TextView>(R.id.bookingStartTime) // formatted start time
    }

    /*** inflates the layout for each booking row */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_booking, parent, false)
        return BookingViewHolder(view)
    }

    /*** binds data from a booking into a row */
    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        // formatter for how start times should appear
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")

        // get building and room name from lookup, fallback if missing
        val (building, room) = roomNameLookup[booking.room_id] ?: ("Unknown" to "Room #${booking.room_id}")

        // populate text fields
        holder.roomName.text = "$room"
        holder.buildingName.text = "$building, "
        holder.attendees.text = "${booking.attendees} people"

        // parse and format the booking's start time
        val startTime = LocalDateTime.parse(booking.start_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"))
        holder.startTime.text = formatter.format(startTime) + ", "

        // attach click listener to whole row
        holder.itemView.setOnClickListener { onItemClicked(booking) }
    }

    /*** returns total number of bookings to display */
    override fun getItemCount(): Int = bookings.size

    /*** replaces current bookings list and refreshes the view */
    fun update(newBookings: List<Booking>) {
        bookings = newBookings
        notifyDataSetChanged()
    }
}
