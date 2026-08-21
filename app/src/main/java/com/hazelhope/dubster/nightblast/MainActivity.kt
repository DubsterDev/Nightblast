package com.hazelhope.dubster.nightblast

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.i18n.phonenumbers.PhoneNumberUtil
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
                        { phoneNumber, message, priority ->
                            sendMessage(phoneNumber, message, priority)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    fun sendMessage(phoneNumber: String, message: String, priority: Int) {
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

            val parts = sms.divideMessage("NIGHTBLAST:MSG:$encryptedBase64@$priority")
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
                    .setKeySize(4096)
                    .build()
            )

            kpg.generateKeyPair()
        }
    }
}

@Composable
fun SendMessage(sendMessage: (phoneNumber: String, message: String, priority: Int) -> Unit, modifier: Modifier = Modifier) {
    val messageTextFieldState = rememberTextFieldState()

    val context = LocalContext.current

    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContacts by remember { mutableStateOf(listOf<String>()) }
    var contactPickerOpen by remember { mutableStateOf(false) }

    var connectDialogOpen by remember { mutableStateOf(false) }

    var contactsRefreshKey by remember { mutableIntStateOf(0) }


    LaunchedEffect(Unit) {
        contacts = fetchContacts(context)
    }

    if (contactPickerOpen) {
        AlertDialog(
            title = {
                Text(text = "Choose recipients")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    contacts.filter { it.hasPublicKey }.forEach { contact ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = selectedContacts.contains(contact.number),
                                    onClick = {
                                        if (selectedContacts.contains(contact.number)) {
                                            selectedContacts =
                                                selectedContacts.filter { it != contact.number }
                                        } else {
                                            selectedContacts += listOf(contact.number)
                                        }
                                    },
                                    role = Role.Checkbox
                                )
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Checkbox(
                                checked = selectedContacts.contains(contact.number),
                                onCheckedChange = null
                            )
                            Text(
                                text = contact.name
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable {
                                contactsRefreshKey++
                            }
                    ) {
                        Text(
                            text = "Refresh"
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable {
                                connectDialogOpen = true
                            }
                    ) {
                        Text(
                            text = "Connect to more contacts"
                        )
                    }
                }
            },
            onDismissRequest = {
                contactPickerOpen = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactPickerOpen = false
                    }
                ) {
                    Text("Close")
                }
            }
        )

    }

    if (connectDialogOpen) {
        InviteContactsDialog(contacts, {connectDialogOpen = false})
    }

    Column(
        modifier = modifier
    ) {
        Button({
            contactPickerOpen = true
        }) {
            Text(
                "Select recipients"
            )
        }
        TextField(
            messageTextFieldState,
            placeholder = {
                Text(
                    "Message (max length 440 chars)"
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        val priorities = listOf("Just a notification", "Vibrating dialog", "Dialog with vibrations and noise")
        var selectedPriority by remember { mutableIntStateOf(0) }
        Column(Modifier.selectableGroup()) {
            priorities.forEachIndexed { index, priority ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (index == selectedPriority),
                            onClick = {
                                selectedPriority = index
                            },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (index == selectedPriority),
                        onClick = null
                    )
                    Text(
                        text = priority,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
        Button({
            val message = messageTextFieldState.text.trim().toString()

            if (message.length <= 440) {
                selectedContacts.forEach { phoneNumber ->
                    sendMessage(phoneNumber, message, selectedPriority)
                }
            }

        }) {
            Text("Send it")
        }
    }
}

@Composable
fun InviteContactsDialog(contacts: List<Contact>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var attemptingToConnect by remember { mutableStateOf(listOf<String>()) }

    val context = LocalContext.current
    val sms = remember { context.getSystemService(SmsManager::class.java) }
    AlertDialog(
        title = {
            Text(text = "Tap to connect")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                contacts.filter { !it.hasPublicKey }.forEach { contact ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                attemptingToConnect += listOf(contact.number)
                                sms.sendTextMessage(contact.number, null, "NIGHTBLAST:GETPUBLICKEY", null, null)
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = contact.name
                        )
                        Text(
                            text = if (attemptingToConnect.contains(contact.number)) {
                                "Attempting to connect. Will not update in real time"
                            } else "Tap to send connection message",
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        },
        onDismissRequest = {
            onDismiss()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}

@Preview
@Composable
fun SendMessagePreview() {
    NightblastTheme {
        SendMessage({ _, _, _ -> })
    }
}