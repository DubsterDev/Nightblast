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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import coil3.compose.AsyncImage
import com.hazelhope.dubster.nightblast.ui.theme.NightblastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Enumeration
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource


const val myKeyAlias = "nightblast"
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            application,
            NightblastDatabase::class.java,
            "nightblast-db"
        ).build()

        val keyDao = db.keyDao()

        val alertHistoryDao = db.alertHistoryDao()

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
                        App(
                            { phoneNumber, message, priority ->
                                sendMessage(phoneNumber, message, priority, keyDao)
                            },
                            keyDao,
                            alertHistoryDao
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

    fun sendMessage(
        phoneNumber: String,
        message: String,
        priority: Int,
        keyDao: KeyDao
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val sms = applicationContext.getSystemService(SmsManager::class.java)

            val publicKey = getPublicKey(keyDao, phoneNumber)

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(
    sendMessage: (phoneNumber: String, message: String, priority: Int) -> Unit,
    keyDao: KeyDao?,
    alertHistoryDao: AlertHistoryDao?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var loadingContacts by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (keyDao != null) {
            contacts = fetchContacts(context, keyDao).sortedBy { it.name }
            loadingContacts = false
            ReloadBus.reload.collect {
                contacts = fetchContacts(context, keyDao).sortedBy { it.name }
            }
        }
    }

    val navController = rememberNavController()

    Scaffold(
        topBar = {
            TopBar(navController)
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val modifierWithPadding = Modifier.padding(innerPadding).padding(12.dp)
        NavHost(navController = navController, startDestination = SendMessageScreen) {
            composable<SendMessageScreen> {
                SendMessage(
                    sendMessage,
                    contacts,
                    loadingContacts,
                    openConnectDialog = {
                        navController.navigate(AddContactsScreen)
                    },
                    modifier = modifierWithPadding
                )
            }
            composable<HistoryScreen> {
                History(contacts, alertHistoryDao, modifierWithPadding)
            }
            composable<AddContactsScreen> {
                AddContacts(
                    contacts,
                    modifierWithPadding
                )
            }
        }
    }
}

@Composable
fun TopBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val titles = mapOf(
        HistoryScreen::class.qualifiedName to R.string.history_title,
        AddContactsScreen::class.qualifiedName to R.string.connect_to_contacts_title,
    )

    TopAppBar(
        title = {
            if (currentRoute == SendMessageScreen::class.qualifiedName) {
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
                        text = stringResource(R.string.app_name)
                    )
                }
            } else {
                Text(
                    text = stringResource(titles[currentRoute] ?: R.string.app_name)
                )
            }
        },
        actions = {
            if (currentRoute == SendMessageScreen::class.qualifiedName) {
                IconButton({
                    navController.navigate(AddContactsScreen)
                }) {
                    Icon(
                        painterResource(R.drawable.outline_person_add),
                        contentDescription = stringResource(R.string.button_add_contacts)
                    )
                }
                IconButton({
                    navController.navigate(HistoryScreen)
                }) {
                    Icon(
                        painterResource(R.drawable.outline_history),
                        contentDescription = stringResource(R.string.button_view_history)
                    )
                }
            }
        },
        navigationIcon = {
            if (
                navController.previousBackStackEntry != null
                && currentRoute != SendMessageScreen::class.qualifiedName
            ) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painterResource(R.drawable.outline_arrow_back),
                        contentDescription = "Back"
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun SendMessage(
    sendMessage: (phoneNumber: String, message: String, priority: Int) -> Unit,
    contacts: List<Contact>,
    loadingContacts: Boolean,
    openConnectDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messageTextFieldState = rememberTextFieldState()

    val localResources = LocalResources.current

    var selectedContacts by remember { mutableStateOf(listOf<String>()) }

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactRow(
    contact: Contact,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(
                        painterResource(R.drawable.outline_person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .size(36.dp)
                    )
                }
            }

            val scale by animateFloatAsState(
                targetValue = if (isSelected || isLoading) 1f else 0f,
                animationSpec = tween(100)
            )

            if (isSelected) {
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
            } else if (isLoading) {
                ContainedLoadingIndicator(
                    modifier = Modifier
                        .size(49.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin.Center
                        }
                        .clip(CircleShape)
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.name,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = contact.nationalNumber
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        modifier = modifier.fillMaxWidth()
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
                        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
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

                        Toast.makeText(context, "Find Nightblast, tap it, and turn on Allow display over other apps", Toast.LENGTH_LONG).show()

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
fun AppPreview() {
    NightblastTheme {
        App({ _, _, _ -> }, null, null)
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