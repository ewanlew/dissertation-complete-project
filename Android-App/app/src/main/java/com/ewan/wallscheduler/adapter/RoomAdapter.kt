package com.ewan.wallscheduler.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.ewan.wallscheduler.R
import Room

/***
 * adapter used to display a scrollable list of room cards
 * used in room selection activity when browsing by building
 */
class RoomAdapter(
    private val allRooms: List<Room>, // full list of rooms from backend
    private val onClick: (Room) -> Unit // callback when a room is tapped
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    // current list shown in recycler (may be filtered)
    private val filteredRooms = allRooms.toMutableList()

    /*** represents a single row (card) in the recycler */
    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.roomNameText) // shows room name (e.g. GH004)
        val id: TextView = itemView.findViewById(R.id.roomIdText) // shows room id (e.g. #2)
        val card: CardView = itemView.findViewById(R.id.roomCard) // full clickable card layout
    }

    /*** inflates the room item layout for each row */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    /*** returns the current number of rooms being displayed */
    override fun getItemCount(): Int = filteredRooms.size

    /*** binds data from a room into a row in the recycler */
    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = filteredRooms[position]

        // populate card fields with room name and id
        holder.name.text = room.name
        holder.id.text = "#${room.id}"

        // allow tapping the card to trigger the onClick callback
        holder.itemView.setOnClickListener { onClick(room) }
    }

    /***
     * filters the list of rooms based on the query string.
     * if blank, it shows all rooms sorted by name.
     * otherwise, it filters rooms containing the query text.
     */
    fun filter(query: String) {
        filteredRooms.clear()

        if (query.isBlank()) {
            // show all rooms alphabetically
            filteredRooms.addAll(allRooms.sortedBy { it.name })
        } else {
            // show only rooms matching the search text
            filteredRooms.addAll(
                allRooms.filter {
                    it.name.contains(query, ignoreCase = true)
                }.sortedBy { it.name }
            )
        }

        // notify recycler to update the list
        notifyDataSetChanged()
    }
}
