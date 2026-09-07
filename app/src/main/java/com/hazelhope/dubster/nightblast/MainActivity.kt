package com.hazelhope.dubster.nightblast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        val hasSendSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReadSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReadContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasSystemAlertWindow = Settings.canDrawOverlays(this)

        val needsToGenerateKey = needsToGenerateKey()

        val hasAllPermissions = hasSendSms && hasReadSms && hasReceiveSms && hasReadContacts && hasSystemAlertWindow && !needsToGenerateKey

        enableEdgeToEdge()
        setContent {
            NightblastTheme {
                var showOnboarding by remember { mutableStateOf(!hasAllPermissions) }

                AnimatedContent(showOnboarding,
                    transitionSpec = {
                        // Slide in from left, slide out to left
                        (fadeIn() + slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> fullWidth })).togetherWith(
                            fadeOut(
                                animationSpec = tween(
                                    150
                                )
                            )
                        )
                    }
                ) { shouldShowOnboarding ->

                    if (!shouldShowOnboarding) {
                        SendMessage(
                            { phoneNumber, message, priority ->
                                sendMessage(phoneNumber, message, priority)
                            }
                        )
                    } else {
                        Onboarding(
                            {
                                showOnboarding = false
                            },
                            {
                                createKeyIfNeeded()
                            }
                        )
                    }
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

    fun needsToGenerateKey(): Boolean {
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

        return needsToGenerateKey
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

    var connectDialogOpen by remember { mutableStateOf(false) }

    var contactsRefreshKey by remember { mutableIntStateOf(0) }


    LaunchedEffect(contactsRefreshKey) {
        contacts = fetchContacts(context)
    }

    if (connectDialogOpen) {
        InviteContactsDialog(contacts, {connectDialogOpen = false})
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painterResource(R.drawable.nightblast_logo),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Nightblast"
                        )
                    }
                },
                actions = {
                    IconButton({
                        contactsRefreshKey++
                    }) {
                        Icon(
                            painterResource(R.drawable.outline_refresh),
                            contentDescription = "Refresh contacts"
                        )
                    }
                    IconButton({
                        connectDialogOpen = true
                    }) {
                        Icon(
                            painterResource(R.drawable.outline_person_add),
                            contentDescription = "Add contacts"
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .padding(12.dp)
        ) {
            val maxMessageHeight = maxHeight * 0.35f
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Recipients",
                    style = MaterialTheme.typography.headlineSmall
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f)
                        .fillMaxWidth()
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
                            Box {
                                if (contact.photo != null) {
                                    AsyncImage(
                                        model = contact.photo,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = contact.name.substring(0, 1)
                                    )
                                }

                                val scale by animateFloatAsState(
                                    targetValue = if (selectedContacts.contains(contact.number)) 1f else 0f,
                                    animationSpec = tween(100)
                                )

                                Icon(
                                    painterResource(R.drawable.outline_check),
                                    contentDescription =  null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            transformOrigin = TransformOrigin.Center
                                        }
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text(
                                text = contact.name
                            )
                        }
                    }
                }

                var selectedPriority by remember { mutableIntStateOf(0) }
                ButtonGroup(
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(
                            menuState = menuState
                        )

                    }
                ) {
                    toggleableItem(
                        checked = selectedPriority == 0,
                        label = "Vibrations",
                        onCheckedChange = {
                            selectedPriority = 0
                        }
                    )

                    toggleableItem(
                        checked = selectedPriority == 1,
                        label = "Vibrations and sound",
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
                                "Message"
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
                            }
                        }) {
                            Icon(
                                painterResource(R.drawable.outline_send),
                                contentDescription = "Send message"
                            )
                        }
                    }
                }

            }
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
                                sms.sendTextMessage(
                                    contact.number,
                                    null,
                                    "NIGHTBLAST:GETPUBLICKEY",
                                    null,
                                    null
                                )
                                sendPublicKey(context, contact.number)
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = contact.name
                        )
                        Text(
                            text = if (attemptingToConnect.contains(contact.number)) {
                                "Attempting to connect. Status will not update in real time"
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
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Image(
            painterResource(R.drawable.nightblast_logo),
            contentDescription = "Nightblast logo",
            modifier = Modifier.size(128.dp)
        )
        Text(
            text = "Welcome to Nightblast",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Maybe I should write a short summary about how it works. :D",
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
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
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

        if (granted.containsKey(Manifest.permission.READ_SMS) && granted.containsKey(Manifest.permission.RECEIVE_SMS) && granted.containsKey(Manifest.permission.SEND_SMS)) {
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
            text = "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Nightblast requires a few permissions to function properly.",
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
                    text = "Contacts",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Left,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Nightblast uses your contacts to allow you to connect with other Nightblast users and send blasts to people in your contacts."
                )
                Button(
                    {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    },
                    enabled = !hasContactsPermissions,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (hasContactsPermissions) "Granted"
                        else "Grant"
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
                    text = "Send and Receive SMS",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Left,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Nightblast uses SMS to send and receive blasts, instead of using a internet-based service."
                )
                Button(
                    {
                        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    },
                    enabled = !hasSmsPermissions,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (hasSmsPermissions) "Granted"
                        else "Grant"
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
                    text = "Display over other apps",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Left,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "When you receive a blast from someone else, you probably want to see it right away. Nightblast uses the display over other apps to open the dialog box over anything you're currently using."
                )
                Button(
                    {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )

                        Toast.makeText(context, "Find Nightblast, tap it, and turn on Allow display over other apps", Toast.LENGTH_LONG).show()

                        context.startActivity(intent)

                    },
                    enabled = !hasDisplayOverOtherAppsPermissions,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (hasDisplayOverOtherAppsPermissions) "Granted"
                        else "Grant"
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
            text = "Generating encryption key",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Please wait a moment",
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
            text = "Everything is ready!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Press next to continue, then add people by clicking the add people button in the top right corner",
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
fun SendMessagePreview() {
    NightblastTheme {
        SendMessage({ _, _, _ -> })
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