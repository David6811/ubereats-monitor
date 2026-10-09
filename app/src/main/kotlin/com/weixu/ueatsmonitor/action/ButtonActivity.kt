package com.weixu.ueatsmonitor.action

import android.app.Activity
import android.os.Bundle

/**
 * Action. What the buttons in the notification actually start.
 *
 * They were broadcasts, and a broadcast leaves the shade open. While it is
 * open it is the only window the reader can see, so the button that ends a
 * navigation went looking for Google Maps and found nothing at all - three
 * times, once per retry, and then gave up.
 *
 * Starting an activity collapses the shade, which is the whole reason this one
 * exists. It draws nothing and finishes at once; the work is handed to
 * [NotificationButtons], which is still where it lives.
 */
class ButtonActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        finish()
        // After finish, so the shade is on its way out before the screen behind
        // it is read.
        if (action != null) NotificationButtons.run(this, action)
    }
}
