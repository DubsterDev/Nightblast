package com.hazelhope.dubster.nightblast

import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "publicKeys")

suspend fun getPublicKey(
    context: Context,
    phoneNumber: String
): PublicKey? {
    val key = stringPreferencesKey(phoneNumber)

    val b64 = context.dataStore.data.first()[key] ?: return null

    val bytes = Base64.decode(b64, Base64.NO_WRAP)

    val keySpec = X509EncodedKeySpec(bytes)
    return KeyFactory.getInstance("RSA").generatePublic(keySpec)
}

suspend fun hasPublicKey(
    context: Context,
    phoneNumber: String
): Boolean {
    val key = stringPreferencesKey(phoneNumber)

    return context.dataStore.data.first()[key] != null
}

suspend fun setPublicKey(context: Context, phoneNumber: String, publicKey: String) {
    val key = stringPreferencesKey(phoneNumber)
    context.dataStore.updateData {
        it.toMutablePreferences().also { preferences ->
            preferences[key] = publicKey
        }
    }
}

suspend fun fetchContacts(context: Context): List<Contact> {
    val contentResolver = context.contentResolver
    val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null, null
    )

    val contacts = mutableListOf<Contact>()
    cursor?.use {
        while (it.moveToNext()) {
            val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
            val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
            val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            val normalizedNumber = normalizeNumber(context, number)

            val photoUri = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.PHOTO_URI),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(id),
                null
            )?.use { photoCursor ->
                if (photoCursor.moveToFirst()) {
                    photoCursor.getString(
                        photoCursor.getColumnIndexOrThrow(
                            ContactsContract.Contacts.PHOTO_URI
                        )
                    )
                } else {
                    null
                }
            }

            if (normalizedNumber != null) {
                val hasKey = hasPublicKey(context, normalizedNumber)
                contacts.add(
                    Contact(id, name, normalizedNumber, hasKey, photoUri, normalizeNumber(context, normalizedNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL) ?: number)
                )
            }
        }
    }

    return contacts
}

suspend fun findContact(context: Context, phoneNumber: String): Contact? {
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber)
    )

    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.PhoneLookup.CONTACT_ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER
        ),
        null,
        null,
        null
    )?.use { cursor ->

        if (cursor.moveToFirst()) {
            val number = normalizeNumber(context,
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.NUMBER
                    )
                )
            ) ?: return null


            val hasKey = hasPublicKey(context, number)

            val contactId = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    ContactsContract.PhoneLookup.CONTACT_ID
                )
            )

            val photoUri = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.PHOTO_URI),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId),
                null
            )?.use { photoCursor ->
                if (photoCursor.moveToFirst()) {
                    photoCursor.getString(
                        photoCursor.getColumnIndexOrThrow(
                            ContactsContract.Contacts.PHOTO_URI
                        )
                    )
                } else {
                    null
                }
            }

            return Contact(
                id = contactId,
                name = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )
                ),
                number = number,
                hasPublicKey = hasKey,
                photo = photoUri,
                nationalNumber = normalizeNumber(context, number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL) ?: number
            )
        }
    }

    return null
}

fun normalizeNumber(context: Context, phoneNumber: String, format: PhoneNumberUtil.PhoneNumberFormat = PhoneNumberUtil.PhoneNumberFormat.E164): String? {
    val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    val phoneNumberParsed = try {
        phoneNumberUtil.parse(phoneNumber, telephonyManager.simCountryIso.uppercase())
    } catch (_: Exception) {
        return null
    }

    if (phoneNumberUtil.isValidNumber(phoneNumberParsed)) {
        val validPhoneNumber = phoneNumberUtil.format(
            phoneNumberParsed,
            format
        )
        return validPhoneNumber
    }
    return null
}

fun sendPublicKey(context: Context, to: String) {
    val sms = context.getSystemService(SmsManager::class.java)

    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val publicKey = keyStore.getCertificate(myKeyAlias).publicKey

    val encodedBytes = publicKey.encoded
    val b64 = Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
    val message = "NIGHTBLAST:RECVPUBKEY:$b64"
    val parts = sms.divideMessage(message)

    sms.sendMultipartTextMessage(to, null, parts, null, null)
}

data class Contact(
    val id: String,
    val name: String,
    val number: String,
    val hasPublicKey: Boolean,
    val photo: String?,
    val nationalNumber: String,
)

object ReloadBus {
    val reload = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1
    )
}