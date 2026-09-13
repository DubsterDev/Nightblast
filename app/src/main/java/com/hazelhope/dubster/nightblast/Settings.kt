package com.hazelhope.dubster.nightblast

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme

@Composable
fun Settings(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isLicenseDialogOpen by remember { mutableStateOf(false) }

    if (isLicenseDialogOpen) {
        LicenseDialog({ isLicenseDialogOpen = false })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SettingsSeparator(stringResource(R.string.about_nightblast))
        IconSettingsCard(
            title = stringResource(R.string.open_source),
            description = stringResource(R.string.nightblast_open_source),
            position = "top",
            icon = R.drawable.outline_open_in_new,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/DubsterDev/Nightblast".toUri())
                context.startActivity(intent)
            }
        )
        SettingsCard(
            title = stringResource(R.string.sounds),
            description = stringResource(R.string.settings_sounds_summary),
            position = "middle",
            content = {},
            modifier = Modifier.clickable {
                isLicenseDialogOpen = true
            }
        )
        SettingsCard(
            title = stringResource(R.string.version),
            description = BuildConfig.VERSION_NAME,
            position = "bottom",
            content = {}
        )

    }
}

@Composable
fun LicenseDialog(
    dismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { dismiss() },
        title = {
            Text(
                text = stringResource(R.string.sounds)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.sounds_andromeda),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.sounds_andromeda_license)
                )
            }
        },
        confirmButton = {
            Button({ dismiss() }) {
                Text(
                    text = stringResource(R.string.close_button_text)
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun SettingsSeparator(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier.padding(6.dp, 18.dp, 6.dp, 6.dp)
    )
}

@Composable
fun IconSettingsCard(
    title: String,
    description: String,
    position: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title,
        description,
        position,
        content = {
            Icon(
                painterResource(icon),
                null,
                modifier = Modifier.size(48.dp)
            )
        },
        modifier = modifier.clickable {
            onClick()
        }
    )
}

@Composable
fun BooleanSettingsCard(
    title: String,
    description: String,
    position: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(
        title,
        description,
        position,
        content = {
            Switch(
                isEnabled,
                null,
                modifier = it
            )
        },
        modifier = modifier
            .toggleable(
                value = isEnabled,
                onValueChange = onToggle,
                role = Role.Switch
            )
    )
}

@Composable
fun SettingsCard(
    title: String,
    description: String,
    position: String,
    content: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = when (position) {
        "top" -> RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 12.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        "bottom" -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 12.dp,
            bottomEnd = 12.dp
        )
        "only_card" -> RoundedCornerShape(12.dp)
        else -> RoundedCornerShape(0.dp)
    }


    Card(
        shape = shape,
        colors = CardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.background,
            disabledContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(0.8f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = description
                )
            }
            content(modifier.weight(0.2f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    NightblastTheme {
        Settings()
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 400)
@Composable
fun LicenseDialogPreview() {
    NightblastTheme {
        LicenseDialog({})
    }
}