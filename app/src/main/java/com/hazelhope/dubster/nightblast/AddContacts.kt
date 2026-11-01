package com.hazelhope.dubster.nightblast

import android.telephony.SmsManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp

@Composable
fun AddContacts(contacts: List<Contact>, modifier: Modifier = Modifier) {
    var attemptingToConnect by remember { mutableStateOf(listOf<String>()) }

    val context = LocalContext.current
    val localResources = LocalResources.current
    val sms = remember { context.getSystemService(SmsManager::class.java) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        items(contacts) { contact ->
            ContactRow(
                contact,
                isSelected = contact.hasPublicKey,
                modifier = Modifier
                    .clickable {
                        attemptingToConnect += listOf(contact.number)
                        sms.sendTextMessage(
                            contact.number,
                            null,
                            localResources.getString(
                                R.string.connect_sms_message,
                                "NIGHTBLAST:CONNECT"
                            ),
                            null,
                            null
                        )
                        sendPublicKey(context, contact.number)
                    },
                isLoading = attemptingToConnect.contains(contact.number)
            )
        }
    }
}