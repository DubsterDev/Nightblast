package com.hazelhope.dubster.nightblast

import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Base64
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

suspend fun getPublicKey(
    keyDao: KeyDao,
    phoneNumber: String
): PublicKey? {
    val b64 = keyDao.findByNumber(phoneNumber)?.publicKey ?: return null

    val bytes = Base64.decode(b64, Base64.NO_WRAP)

    val keySpec = X509EncodedKeySpec(bytes)
    return KeyFactory.getInstance("RSA").generatePublic(keySpec)
}

fun setPublicKey(
    keyDao: KeyDao,
    phoneNumber: String,
    publicKey: String
) {
    keyDao.upsertKey(Key(
        phoneNumber,
        publicKey
    ))
}

suspend fun fetchContacts(context: Context, keyDao: KeyDao): List<Contact> {
    val publicKeys = keyDao.getAll().associate {
        it.phoneNumber to it.publicKey
    }

    val region = getRegion(context)

    val contentResolver = context.contentResolver
    val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        ),
        null, null, null
    )

    val contacts = mutableListOf<Contact>()
    cursor?.use {
        while (it.moveToNext()) {
            val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
            val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
            val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            val photoUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI))
            val normalizedNumbers = normalizeNumber(region, number)

            if (normalizedNumbers != null) {
                val hasKey = publicKeys.containsKey(normalizedNumbers.internationalNumber)

                contacts.add(
                    Contact(id, name, normalizedNumbers.internationalNumber, hasKey, photoUri, normalizedNumbers.nationalNumber)
                )
            }
        }
    }

    return contacts
}

suspend fun findContact(context: Context, phoneNumber: String, keyDao: KeyDao?): Contact? {
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
            val numbers = normalizeNumber(getRegion(context),
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.NUMBER
                    )
                )
            ) ?: return null

            val hasKey = keyDao?.findByNumber(numbers.internationalNumber) != null

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
                number = numbers.internationalNumber,
                hasPublicKey = hasKey,
                photo = photoUri,
                nationalNumber = numbers.nationalNumber
            )
        }
    }

    return null
}

fun getRegion(context: Context): String {
    val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    return telephonyManager.simCountryIso.uppercase()
}

fun normalizeNumber(region: String, phoneNumber: String): TwoPhoneNumbers? {
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    val phoneNumberParsed = try {
        phoneNumberUtil.parse(phoneNumber, region)
    } catch (_: Exception) {
        return null
    }

    if (phoneNumberUtil.isValidNumber(phoneNumberParsed)) {
        val internationalNumber = phoneNumberUtil.format(
            phoneNumberParsed,
            PhoneNumberUtil.PhoneNumberFormat.E164
        )
        val nationalNumber = phoneNumberUtil.format(
            phoneNumberParsed,
            PhoneNumberUtil.PhoneNumberFormat.NATIONAL
        )

        return TwoPhoneNumbers(
            internationalNumber,
            nationalNumber
        )
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

data class TwoPhoneNumbers(
    val internationalNumber: String,
    val nationalNumber: String
)

data class AlertHistoryWithMoreData(
    val id: Int,
    val phoneNumber: String,
    val contactName: String,
    val priority: Int,
    val message: String,
    val time: String
)
object ReloadBus {
    val reload = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1
    )
}