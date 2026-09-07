package com.hazelhope.dubster.nightblast

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class Alert : ComponentActivity() {
    var mediaPlayer = MediaPlayer()
    var vibrator: Vibrator? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val message = intent.getStringExtra("MESSAGE") ?: "Oops"
        val sender = intent.getStringExtra("SENDER") ?: "Oops"
        val priority = intent.getIntExtra("PRIORITY", 0)

        if (priority != 0) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                mediaPlayer.release()
                mediaPlayer = MediaPlayer()
            }

            val audioFiles = arrayOf("music/andromeda.mp3")

            if (priority - 1 < audioFiles.size) {
                val descriptor = assets.openFd(audioFiles[priority - 1])
                mediaPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                descriptor.close()


                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                mediaPlayer.prepare()
                mediaPlayer.setVolume(1f, 1f)
                mediaPlayer.playbackParams
                mediaPlayer.isLooping = true
                mediaPlayer.start()
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            val pattern = longArrayOf(0, 300, 150, 150, 500, 150, 150, 150, 500, 300, 150, 300, 150, 150, 500, 150, 150, 150, 150, 150, 150, 150, 500, 300, 500, 300, 150, 150, 150, 150, 150, 150, 500, 150, 150, 300, 150, 150, 150, 150, 500, 150, 150, 300, 500, 150, 150, 150, 150, 150, 500, 300, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator!!.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator!!.vibrate(pattern, 0)
            }
        }

        setContent {
            var isPlaying by remember { mutableStateOf(true) }
            NightblastTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Popup(
                        text = message,
                        sender = sender,
                        onClose = {
                            finish()
                        },
                        isPlaying,
                        {
                            mediaPlayer.stop()
                            mediaPlayer.release()
                            mediaPlayer = MediaPlayer()
                            vibrator?.let {
                                vibrator!!.cancel()
                            }
                            isPlaying = false
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
        vibrator?.let {
            vibrator!!.cancel()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Popup(
    text: String,
    sender: String,
    onClose: () -> Unit,
    isPlaying: Boolean,
    silenceAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        confirmButton = {},
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painterResource(R.drawable.nightblast_logo),
                            contentDescription = "Nightblast logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Nightblast",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        { onClose() }
                    ) {
                        Icon(
                            painterResource(R.drawable.outline_close),
                            contentDescription = "Close"
                        )
                    }
                }
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sent by $senderName",
                    color = Color.DarkGray,
                    fontStyle = FontStyle.Italic
                )
                AnimatedVisibility(isPlaying) {
                    Button(
                        {
                            silenceAlert()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Silence alert"
                        )
                    }
                }
            }
        }
    )
}

@Preview(widthDp = 300, heightDp = 600, showBackground = true)
@Composable
fun PopupPreview() {
    NightblastTheme {
        Popup(
            "The neighbors are trying to eat all my hot dogs!",
            "+15555555555",
            {},
            true,
            {}
        )
    }
}