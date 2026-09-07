package com.hazelhope.dubster.nightblast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BroadcastReceiver", "onReceive")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d("BroadcastReceiver", "SMS received")

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            var sender: String? = null
            var body = ""
            messages.forEach { sms ->
                sender = sms.originatingAddress
                body += sms.displayMessageBody
            }

            if (body.startsWith("NIGHTBLAST:")) {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val entry = keyStore.getEntry(myKeyAlias, null)
                val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey

                val command = body.replace("NIGHTBLAST:", "")
                if (command == "GETPUBLICKEY" && sender != null) {
                    sendPublicKey(context, sender)
                } else if (command.startsWith("RECVPUBKEY:") && sender != null) {
                    val pubKey = command.replace("RECVPUBKEY:", "")
                    Log.d("TAG", "onReceive: pub key $pubKey from $sender")
                    CoroutineScope(Dispatchers.IO).launch {
                        setPublicKey(context, sender, pubKey)
                    }
                } else if (command.startsWith("MSG:") && sender != null) {
                    val encryptedPayload = command.replace("MSG:", "").split("@")
                    val encryptedMessage = encryptedPayload[0]
                    val priority = encryptedPayload[1].toInt()

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

                    val activityIntent = Intent(context, Alert::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("MESSAGE", decryptedBytes.decodeToString())
                        putExtra("SENDER", sender)
                        putExtra("PRIORITY", priority)
                    }
                    context.startActivity(activityIntent)
                }
            }
        }
    }
}