package com.hazelhope.dubster.nightblast

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun History(
    contacts: List<Contact>,
    historyDao: AlertHistoryDao?,
    modifier: Modifier = Modifier
) {
    val formatter = SimpleDateFormat("MMMM d, yyyy h:mm a", LocalLocale.current.platformLocale)

    var history by remember { mutableStateOf(emptyList<AlertHistoryWithMoreData>())}

    LaunchedEffect(contacts, historyDao) {
        withContext(Dispatchers.IO) {
            val alertHistory = historyDao?.getAll() ?: return@withContext

            val contactsMapped = contacts.associateBy { it.number }

            history = alertHistory.map {
                AlertHistoryWithMoreData(
                    id = it.id,
                    phoneNumber = it.phoneNumber,
                    contactName = contactsMapped[it.phoneNumber]?.name ?: it.phoneNumber,
                    priority = it.priority,
                    message = it.message,
                    time = formatter.format(Date(it.time * 1000))
                )
            }
        }
    }

    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        history.forEach {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val activityIntent = Intent(context, Alert::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("MESSAGE", it.message)
                            putExtra("SENDER", it.phoneNumber)
                            putExtra("PRIORITY", it.priority)
                        }
                        context.startActivity(activityIntent)
                    }
            ) {
                Text(
                    text = it.contactName,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = it.time
                )
            }
        }
    }
}