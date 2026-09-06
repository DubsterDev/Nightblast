package com.hazelhope.dubster.nightblast

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        setContent {
            NightblastTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Popup(
                        text = message,
                        sender = sender,
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
    var senderName by remember { mutableStateOf(sender) }

    val context = LocalContext.current

    LaunchedEffect(sender) {
        CoroutineScope(Dispatchers.IO).launch {
            val contact = findContact(context, sender)
            senderName = contact?.name ?: sender
        }
    }

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
                    text = "Sent by $senderName using Nightblast",
                    color = Color.DarkGray,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    )
}