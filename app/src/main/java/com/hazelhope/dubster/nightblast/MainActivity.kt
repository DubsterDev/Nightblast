package com.hazelhope.dubster.nightblast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

        val db = Room.databaseBuilder(
            application,
            NightblastDatabase::class.java,
            "nightblast-db"
        ).build()

        val keyDao = db.keyDao()

        val alertHistoryDao = db.alertHistoryDao()

        val hasSendSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReadContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasSystemAlertWindow = Settings.canDrawOverlays(this)

        val needsToGenerateKey = needsToGenerateKey()

        val hasAllPermissions = hasSendSms && hasReceiveSms && hasReadContacts && hasSystemAlertWindow && !needsToGenerateKey

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
        val modifierWithPadding = Modifier
            .padding(innerPadding)
            .padding(12.dp)
        NavHost(navController = navController, startDestination = SendMessageScreen) {
            composable<SendMessageScreen> {
                SendMessage(
                    sendMessage,
                    contacts,
                    loadingContacts,
                    openConnectDialog = {
                        navController.navigate(AddContactsScreen)
                    },
                    markUserVerified = { contact ->
                        val oldKey = keyDao?.findByNumber(contact.number)?.publicKey
                        oldKey?.let { key ->
                            keyDao.upsertKey(
                                Key(
                                    phoneNumber = contact.number,
                                    publicKey = key,
                                    verified = true
                                )
                            )
                            ReloadBus.reload.tryEmit(Unit)
                        }
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
            composable<SettingsScreen> {
                Settings(
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
        SettingsScreen::class.qualifiedName to R.string.settings_title,
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
                IconButton({
                    navController.navigate(SettingsScreen)
                }) {
                    Icon(
                        painterResource(R.drawable.outline_settings),
                        contentDescription = stringResource(R.string.settings_title)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Bold
                )
                if (!contact.verified) {
                    Text(
                        text = "(not verified)",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = contact.nationalNumber
            )
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    NightblastTheme {
        App({ _, _, _ -> }, null, null)
    }
}