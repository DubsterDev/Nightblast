package com.hazelhope.dubster.nightblast

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import java.util.Enumeration
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

const val myKeyAlias = "nightblast"
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createKeyIfNeeded()



        enableEdgeToEdge()
        setContent {
            NightblastTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SendMessage(
                        { phoneNumber, message ->
                            sendMessage(phoneNumber, message)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    fun sendMessage(phoneNumber: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val sms = applicationContext.getSystemService(SmsManager::class.java)

            val publicKey = getPublicKey(applicationContext, phoneNumber)

            if (publicKey == null) {
                Log.d("TAG", "sendMessage: No public key")
                return@launch
            }

            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            val oaepSpec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
            )

            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
            val encryptedBytes = cipher.doFinal(message.encodeToByteArray())
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)

            val parts = sms.divideMessage("NIGHTBLAST:MSG:$encryptedBase64")
            sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        }
    }

    fun createKeyIfNeeded() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val aliases: Enumeration<String?> = keyStore.aliases()
        var needsToGenerateKey = true
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (alias == myKeyAlias) {
                needsToGenerateKey = false
            }
        }

        if (needsToGenerateKey) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
            )

            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    myKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA1
                    )
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setKeySize(2048)
                    .build()
            )

            kpg.generateKeyPair()
        }
    }
}

@Composable
fun SendMessage(sendMessage: (phoneNumber: String, message: String) -> Unit, modifier: Modifier = Modifier) {
    val phoneNumberTextFieldState = rememberTextFieldState()
    val messageTextFieldState = rememberTextFieldState()
    Column(
        modifier = modifier
    ) {
        TextField(
            phoneNumberTextFieldState
        )
        TextField(
            messageTextFieldState,
            placeholder = {
                Text(
                    "Message (max length 190 chars)"
                )
            }
        )
        Button({
            val message = messageTextFieldState.text.trim().toString()
            val phoneNumber = phoneNumberTextFieldState.text.trim().toString()

            if (message.length <= 180) {
                sendMessage(phoneNumber, message)
            }

        }) {
            Text("Send it")
        }
    }
}