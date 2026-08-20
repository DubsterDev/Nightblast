package com.hazelhope.dubster.nightblast

import android.content.Context
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