package com.ewan.wallscheduler.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.adapter.RoomAdapter
import com.ewan.wallscheduler.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/***
 * displays a list of rooms for the selected building.
 * allows filtering via text input and navigation to the room view.
 */
class RoomSelectionActivity : BaseFullscreenActivity() {

    // adapter for filtering and displaying rooms
    private lateinit var roomAdapter: RoomAdapter

    // input field for searching rooms
    private lateinit var searchInput: EditText

    // recycler view that lists all the rooms
    private lateinit var recycler: RecyclerView

    // title for the current building name
    private lateinit var title: TextView

    /*** called when the view is created and ready to be shown */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_selection)
        enableEdgeToEdge()

        // inflate loading dialog from xml
        val loadingView = layoutInflater.inflate(R.layout.item_loading_rooms, null)
        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(loadingView)
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // pull building info passed through the intent
        val buildingId = intent.getIntExtra("buildingId", -1)
        val buildingName = intent.getStringExtra("buildingName") ?: "Unknown"

        // wire up ui references
        title = findViewById(R.id.roomSchedulerTitle)
        recycler = findViewById(R.id.roomRecycler)
        searchInput = findViewById(R.id.editSearchRooms)

        // set title text
        title.text = buildingName

        // initialise recycler layout
        recycler.layoutManager = LinearLayoutManager(this)

        /*** fetch room list from backend and apply to the adapter */
        CoroutineScope(Dispatchers.IO).launch {
            val response = RetrofitClient.api.getRooms(buildingId)

            // hide the loading popup once the call is complete
            loadingDialog.dismiss()

            if (response.isSuccessful && response.body() != null) {
                val rooms = response.body()!!.sortedBy { it.name }

                withContext(Dispatchers.Main) {
                    // setup adapter with click logic to move to room status view
                    roomAdapter = RoomAdapter(rooms) { room ->
                        val intent = Intent(this@RoomSelectionActivity, RoomStatusActivity::class.java).apply {
                            putExtra("ROOM_ID", room.id)
                            putExtra("ROOM_NAME", room.name)
                        }
                        startActivity(intent)
                    }

                    recycler.adapter = roomAdapter

                    // hook up search field with live filtering
                    searchInput.addTextChangedListener(object: TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                            roomAdapter.filter(s.toString())
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    })
                }
            }
        }

        // return to previous screen if back card is tapped
        findViewById<CardView>(R.id.buildingViewCard).setOnClickListener {
            finish()
        }
    }
}
