package com.ewan.wallscheduler.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.cardview.widget.CardView
import com.ewan.wallscheduler.R
import com.ewan.wallscheduler.fragment.AdminLoginDialogFragment

/***
 * entry screen that displays all buildings the user can select from.
 * allows navigation to room list based on building selection.
 * tapping the title triggers an admin login popup.
 */
class BuildingSelectionActivity : BaseFullscreenActivity() {

    /*** called when the activity is created */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_building_selection)

        // card views representing each building and admin access
        val greatHallCard = findViewById<CardView>(R.id.greatHallCard)
        val engineeringCentralCard = findViewById<CardView>(R.id.engineeringCentralCard)
        val computationalFoundryCard = findViewById<CardView>(R.id.computationalFoundryCard)
        val titleView = findViewById<CardView>(R.id.adminViewCard)

        // opens room list for great hall
        greatHallCard.setOnClickListener {
            openRoomList(1, "Great Hall")
        }

        // opens room list for engineering central
        engineeringCentralCard.setOnClickListener {
            openRoomList(2, "Engineering Central")
        }

        // opens room list for computational foundry
        computationalFoundryCard.setOnClickListener {
            openRoomList(3, "Computational Foundry")
        }

        // opens admin login dialog when tapping the title
        titleView.setOnClickListener {
            val loginDialog = AdminLoginDialogFragment.newInstance {
                val intent = Intent(this, AdminDashboardActivity::class.java)
                startActivity(intent)
            }
            loginDialog.show(supportFragmentManager, "admin_login")
        }
    }

    /***
     * navigates to the room list for the selected building
     * @param buildingId the id of the building
     * @param buildingName the readable name of the building
     */
    private fun openRoomList(buildingId: Int, buildingName: String) {
        val intent = Intent(this, RoomSelectionActivity::class.java).apply {
            putExtra("buildingId", buildingId)
            putExtra("buildingName", buildingName)
        }
        startActivity(intent)
    }

    /*** re-applies immersive fullscreen if the window regains focus */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullscreen()
        }
    }

    /*** enforces immersive fullscreen ui flags */
    private fun enableFullscreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }
}
