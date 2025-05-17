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
 * adapter for displaying a list of schedule entries for a specific room and day
 * used in the left panel of the schedule screen to show today's bookings
 */
class ScheduleEntryAdapter(private val items: List<Booking>) :
    RecyclerView.Adapter<ScheduleEntryAdapter.ScheduleViewHolder>() {

    // formatter for showing just the hour and minute (e.g. 13:00)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    /*** holds references to views in a single schedule entry row */
    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.timeRange) // shows time range (e.g. 10:00 - 11:00)
        val purpose: TextView = view.findViewById(R.id.meetingPurpose) // shows meeting name or purpose
    }

    /*** inflates the schedule item layout and returns the viewholder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_entry, parent, false)
        return ScheduleViewHolder(view)
    }

    /***
     * binds each booking to a schedule row
     * parses and formats the booking start/end times and purpose
     */
    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val booking = items[position]

        // removes milliseconds if they exist (e.g. .0 or .123)
        val cleanedStart = booking.start_time.split(".")[0]
        val cleanedEnd = booking.end_time.split(".")[0]

        // parses start/end timestamps
        val start = LocalDateTime.parse(cleanedStart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val end = LocalDateTime.parse(cleanedEnd, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        // displays formatted time and purpose
        holder.time.text = "${start.format(timeFormat)} - ${end.format(timeFormat)}"
        holder.purpose.text = booking.purpose
    }

    /*** returns how many entries are in the list */
    override fun getItemCount(): Int = items.size
}
