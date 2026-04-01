package com.notefloat.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notefloat.app.service.NoteFloatService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NoteFloatService.start(context)
        }
    }
}
