package com.hazelhope.dubster.nightblast

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyFactory
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

suspend fun setPublicKey(context: Context, phoneNumber: String, publicKey: String) {
    val key = stringPreferencesKey(phoneNumber)
    context.dataStore.updateData {
        it.toMutablePreferences().also { preferences ->
            preferences[key] = publicKey
        }
    }
}

fun fetchContacts(context: Context): List<Contact> {
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
            contacts.add(Contact(id, name, number))
        }
    }

    return contacts
}

fun findContact(context: Context, phoneNumber: String): Contact? {
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
            return Contact(
                id = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.CONTACT_ID
                    )
                ),
                name = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )
                ),
                number = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.NUMBER
                    )
                )
            )
        }
    }

    return null
}

data class Contact(
    val id: String,
    val name: String,
    val number: String
)