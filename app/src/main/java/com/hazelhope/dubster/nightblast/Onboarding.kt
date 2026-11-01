package com.hazelhope.dubster.nightblast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Onboarding(
    exit: () -> Unit,
    createKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var canAdvance by remember { mutableStateOf(true) }
    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        val maxSteps = 3

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            AnimatedContent(step,
                transitionSpec = {
                    val direction = if (targetState > initialState) {
                        1
                    } else {
                        -1
                    }

                    (fadeIn() + slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { fullWidth -> fullWidth * direction })).togetherWith(
                        fadeOut(
                            animationSpec = tween(
                                150
                            )
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { animatedStep ->
                when (animatedStep) {
                    0 -> {
                        OnboardingOne()
                    }

                    1 -> {
                        OnboardingTwo(
                            { canAdvance = it }
                        )
                    }
                    2 -> {
                        OnboardingGenerateKey(
                            { canAdvance = it },
                            { createKey() },
                            { step++ }
                        )
                    }
                    3 -> {
                        OnboardingFour()
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    {
                        step--
                        canAdvance = true
                    },
                    modifier = Modifier
                        .weight(1f),
                    enabled = step > 0
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            painterResource(R.drawable.outline_arrow_back),
                            contentDescription = null
                        )
                        Text(
                            text = "Previous"
                        )
                    }
                }
                TextButton(
                    {
                        if (step < maxSteps) {
                            step++
                        } else {
                            exit()
                        }
                    },
                    modifier = Modifier
                        .weight(1f),
                    enabled = canAdvance
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Next"
                        )
                        Icon(
                            painterResource(R.drawable.outline_arrow_forward),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingOne(modifier: Modifier = Modifier) {
    val localResources = LocalResources.current

    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Image(
            painterResource(R.drawable.nightblast_logo),
            contentDescription = localResources.getString(R.string.nightblast_logo_content_description),
            modifier = Modifier.size(128.dp)
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_to_nightblast),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_short_summary),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingTwo(
    setAdvanceable: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    var hasContactsPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

        )
    }

    var hasSmsPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

        )
    }

    var hasDisplayOverOtherAppsPermissions by remember {
        mutableStateOf(
            Settings.canDrawOverlays(context)
        )
    }

    LaunchedEffect(hasContactsPermissions, hasSmsPermissions, hasDisplayOverOtherAppsPermissions) {
        setAdvanceable(hasContactsPermissions && hasSmsPermissions && hasDisplayOverOtherAppsPermissions)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.filter { it.value }

        if (granted.containsKey(Manifest.permission.RECEIVE_SMS) && granted.containsKey(Manifest.permission.SEND_SMS)) {
            hasSmsPermissions = true
        } else if (granted.containsKey(Manifest.permission.READ_CONTACTS)) {
            hasContactsPermissions = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasDisplayOverOtherAppsPermissions = Settings.canDrawOverlays(context)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_heading),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_permissions_short_summary),
            textAlign = TextAlign.Center
        )
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_contacts),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Left,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_contacts_summary)
                )
                Button(
                    {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    },
                    enabled = !hasContactsPermissions,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (hasContactsPermissions) stringResource(R.string.granted_text)
                        else stringResource(R.string.grant_text)
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_sms),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Left,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_sms_summary)
                )
                Button(
                    {
                        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS))
                    },
                    enabled = !hasSmsPermissions,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (hasSmsPermissions) stringResource(R.string.granted_text)
                        else stringResource(R.string.grant_text)
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_display_over_other_apps),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Left,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_display_over_other_apps_summary)
                )
                Button(
                    {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )

                        Toast.makeText(context,
                            resources.getString(R.string.toast_find_nightblast), Toast.LENGTH_LONG).show()

                        context.startActivity(intent)

                    },
                    enabled = !hasDisplayOverOtherAppsPermissions,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (hasDisplayOverOtherAppsPermissions) stringResource(R.string.granted_text)
                        else stringResource(R.string.grant_text)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingGenerateKey(
    setAdvanceable: (Boolean) -> Unit,
    generateKey: () -> Unit,
    advanceNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        setAdvanceable(false)
        withContext(Dispatchers.Default) {
            generateKey()
        }
        setAdvanceable(true)
        advanceNow()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = stringResource(R.string.generating_encryption_key_text),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.please_wait_a_moment_text),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingFour(
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.everything_is_ready_text),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_sms_disclaimer),
            textAlign = TextAlign.Left
        )
    }
}

@Preview
@Composable
fun OnboardingPreview() {
    NightblastTheme {
        Onboarding({}, {})
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingTwoPreview() {
    NightblastTheme {
        OnboardingTwo({})
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingGenerateKeyPreview() {
    NightblastTheme {
        OnboardingGenerateKey({},  {}, {})
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingFourPreview() {
    NightblastTheme {
        OnboardingFour()
    }
}