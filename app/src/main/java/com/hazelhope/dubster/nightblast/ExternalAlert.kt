package com.hazelhope.dubster.nightblast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class ExternalAlert : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent.getStringExtra("MESSAGE") ?: "Oops"
        val priority = intent.getIntExtra("PRIORITY", 0)

        val activityIntent = Intent(applicationContext, Alert::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("MESSAGE", message)
            putExtra("SENDER", "_EXTERNAL")
            putExtra("PRIORITY", priority)
        }
        startActivity(activityIntent)
        finish()
    }
}