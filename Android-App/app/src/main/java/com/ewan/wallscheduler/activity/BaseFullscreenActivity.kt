package com.ewan.wallscheduler.activity

import android.view.View
import androidx.appcompat.app.AppCompatActivity

/***
 * base activity that forces immersive fullscreen mode.
 * all other activities can extend this to apply fullscreen styling.
 */
open class BaseFullscreenActivity : AppCompatActivity() {

    /*** when focus changes, ensure fullscreen is applied again */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullscreen()
        }
    }

    /*** applies immersive fullscreen flags to the window decor view */
    private fun enableFullscreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
    }
}
