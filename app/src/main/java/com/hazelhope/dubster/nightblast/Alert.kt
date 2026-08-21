package com.hazelhope.dubster.nightblast

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme

class Alert : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val message = intent.getStringExtra("MESSAGE") ?: "Oops"
        val sender = intent.getStringExtra("SENDER") ?: "Oops"

        val contact = findContact(applicationContext, sender)

        val contactDisplayName = contact?.name ?: sender

        setContent {
            NightblastTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Popup(
                        text = message,
                        sender = contactDisplayName,
                        onClose = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Popup(text: String, sender: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = { onClose() },
        confirmButton = {
             Button(
                 {onClose()}
             ) {
                 Text("Close")
             }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
            ) {
                Text(
                    text = text
                )
                Text(
                    text = "Sent by $sender using Nightblast",
                    color = Color.DarkGray,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    )
}