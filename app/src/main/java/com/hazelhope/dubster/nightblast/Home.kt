package com.hazelhope.dubster.nightblast

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SendMessage(
    sendMessage: (phoneNumber: String, message: String, priority: Int) -> Unit,
    contacts: List<Contact>,
    loadingContacts: Boolean,
    openConnectDialog: () -> Unit,
    markUserVerified: suspend (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    val messageTextFieldState = rememberTextFieldState()

    val localResources = LocalResources.current

    var selectedContacts by remember { mutableStateOf(listOf<String>()) }

    val unverifiedUsers = contacts.filter { !it.verified }

    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val maxMessageHeight = maxHeight * 0.35f
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(!unverifiedUsers.isEmpty()) {
                UnverifiedUsersWarning(
                    unverifiedUsers,
                    {
                        CoroutineScope(Dispatchers.IO).launch {
                            unverifiedUsers.forEach {
                                markUserVerified(it)
                            }
                        }
                    },
                    modifier = Modifier
                        .heightIn(max = maxMessageHeight)
                        .verticalScroll(rememberScrollState())
                )
            }

            Text(
                text = stringResource(R.string.choose_recipients),
                style = MaterialTheme.typography.headlineSmall
            )
            if (contacts.none { it.hasPublicKey } && !loadingContacts) {
                Text(
                    text = stringResource(R.string.no_connections_yet)
                )
                Button(
                    {
                        openConnectDialog()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.button_add_contacts)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                contacts.filter { it.hasPublicKey }.sortedBy { it.name }.forEach { contact ->
                    ContactRow(
                        contact,
                        selectedContacts.contains(contact.number),
                        modifier = Modifier.selectable(
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
                    )
                }
            }

            var selectedPriority by remember { mutableIntStateOf(1) }
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(
                        menuState = menuState
                    )

                }
            ) {
                toggleableItem(
                    checked = selectedPriority == 0,
                    label = localResources.getString(R.string.priority_just_vibrations),
                    onCheckedChange = {
                        selectedPriority = 0
                    }
                )

                toggleableItem(
                    checked = selectedPriority == 1,
                    label = localResources.getString(R.string.priority_vibrations_and_sound),
                    onCheckedChange = {
                        selectedPriority = 1
                    }
                )

            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    messageTextFieldState,
                    placeholder = {
                        Text(
                            stringResource(R.string.text_field_message_placeholder)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = maxMessageHeight)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (messageTextFieldState.text.length > 400) {
                        Text(
                            text = (440 - messageTextFieldState.text.length).coerceAtLeast(0)
                                .toString(),
                            color = if (messageTextFieldState.text.length > 440) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    }
                    FilledIconButton({
                        val message = messageTextFieldState.text.trim().toString()

                        if (message.length <= 440) {
                            selectedContacts.forEach { phoneNumber ->
                                sendMessage(phoneNumber, message, selectedPriority)
                            }
                            selectedContacts = emptyList()
                            selectedPriority = 1
                            messageTextFieldState.clearText()

                            Toast.makeText(context,
                                localResources.getString(R.string.toast_sending_alert), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context,
                                localResources.getString(R.string.toast_message_is_too_long), Toast.LENGTH_SHORT).show()
                        }
                    },
                        enabled = messageTextFieldState.text.trim().toString().length <= 440 && !selectedContacts.isEmpty()
                    ) {
                        Icon(
                            painterResource(R.drawable.outline_send),
                            contentDescription = stringResource(R.string.send_button_content_description)
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun UnverifiedUsersWarning(
    unverifiedUsers: List<Contact>,
    markVerified: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardColors(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.change_in_encryption_keys_alert_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            unverifiedUsers.forEach {
                Text(
                    text = stringResource(
                        R.string.unverified_user_warning_message,
                        it.name,
                        it.name
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    { markVerified() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.button_mark_as_verified)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SendMessagePreview() {
    NightblastTheme {
        SendMessage(
            sendMessage = { _, _, _ -> },
            contacts = listOf(
                Contact(
                    id = "DEMO",
                    name = "Demo User",
                    number = "+15555555555",
                    hasPublicKey = true,
                    photo = null,
                    nationalNumber = "(555) 555-5555",
                    verified = true
                ),
                Contact(
                    id = "DEMO2",
                    name = "Demo User 2",
                    number = "+15555555556",
                    hasPublicKey = true,
                    photo = null,
                    nationalNumber = "(555) 555-5556",
                    verified = false
                )
            ),
            loadingContacts = false,
            openConnectDialog = { },
            markUserVerified = {}
        )
    }
}