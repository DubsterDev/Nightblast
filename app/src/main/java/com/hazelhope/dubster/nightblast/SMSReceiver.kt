package com.hazelhope.dubster.nightblast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.time.Clock

class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            var sender: String? = null
            var body = ""
            messages.forEach { sms ->
                sender = sms.originatingAddress
                body += sms.displayMessageBody
            }

            if (body.startsWith("NIGHTBLAST:") && sender != null) {
                val db = Room.databaseBuilder(
                    context,
                    NightblastDatabase::class.java,
                    "nightblast-db"
                ).build()

                val keyDao = db.keyDao()

                val contact = runBlocking {
                    withContext(Dispatchers.IO) {
                        findContact(context, sender, keyDao)
                    }
                }

                if (contact == null) {
                    Log.d("TAG", "onReceive: $sender tried to send a message; ignoring as not in contacts")
                    return
                }

                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val entry = keyStore.getEntry(myKeyAlias, null)
                val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey

                val command = body.removePrefix("NIGHTBLAST:")
                if (command.split("\n")[0] == "CONNECT") {
                    sendPublicKey(context, sender)
                } else if (command.startsWith("RECVPUBKEY:")) {
                    val pubKey = command.removePrefix("RECVPUBKEY:")
                    CoroutineScope(Dispatchers.IO).launch {
                        setPublicKey(keyDao, sender, pubKey)
                        ReloadBus.reload.tryEmit(Unit)
                    }
                } else if (command.startsWith("MSG:")) {
                    val alertHistoryDao = db.alertHistoryDao()

                    val encryptedPayload = command.removePrefix("MSG:").split("@")
                    val encryptedMessage = encryptedPayload[0]
                    val priority = if (encryptedPayload.size < 2) 0
                        else encryptedPayload[1].toIntOrNull() ?: 0

                    val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
                    val oaepSpec = OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA1,
                        PSource.PSpecified.DEFAULT
                    )
                    cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
                    val encryptedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)

                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    val decryptedMessage = decryptedBytes.decodeToString()

                    CoroutineScope(Dispatchers.IO).launch {
                        alertHistoryDao.insert(AlertHistory(
                            phoneNumber = sender,
                            priority = priority,
                            message = decryptedMessage,
                            time = Clock.System.now().epochSeconds
                        ))
                    }

                    val activityIntent = Intent(context, Alert::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("MESSAGE", decryptedMessage)
                        putExtra("SENDER", sender)
                        putExtra("PRIORITY", priority)
                    }
                    context.startActivity(activityIntent)
                }
            }
        }
    }
}