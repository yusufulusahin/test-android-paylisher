package com.paylisher.test

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paylisher.Paylisher

@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    val context = LocalContext.current
    var userId by remember { mutableStateOf("") }
    val deviceId = remember { DeviceIdHelper.get(context) }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text(stringResource(R.string.login_userid_label)) },
                    placeholder = { Text(stringResource(R.string.login_userid_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Text("deviceID: $deviceId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val prefs = context.getSharedPreferences("paylisher_prefs", Context.MODE_PRIVATE)
                        val token = prefs.getString("fcm_token", null)
                        val props = mutableMapOf<String, Any>("deviceID" to deviceId, "platform" to "android")
                        if (!token.isNullOrBlank()) props["token"] = token
                        Paylisher.identify(userId, userProperties = props)
                        Log.d("SDK", "identify($userId)  props: $props")
                        // Auth-gate: login olundu → bekleyen (cold-start) wallet deeplink'i tamamlanır.
                        DeepLinkBus.setAuthenticated(true)
                        onLogin(userId)
                    },
                    enabled = userId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.login_button))
                }
            }

            LanguageMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}
