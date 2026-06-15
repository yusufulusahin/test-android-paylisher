package com.paylisher.test

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

/**
 * Dil seçici (🌐). Sistem / Türkçe / İngilizce arasında geçiş yapar.
 * Seçim yapıldığında dil SharedPreferences'a yazılır ve Activity `recreate()`
 * edilerek yeni dil anında uygulanır.
 */
@Composable
fun LanguageMenu(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val current = LocaleHelper.getPersistedLanguage(context)

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Text("🌐", fontSize = 20.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LanguageItem(stringResource(R.string.language_system), LocaleHelper.SYSTEM, current) {
                expanded = false; selectLanguage(context, it)
            }
            LanguageItem(stringResource(R.string.language_turkish), "tr", current) {
                expanded = false; selectLanguage(context, it)
            }
            LanguageItem(stringResource(R.string.language_english), "en", current) {
                expanded = false; selectLanguage(context, it)
            }
        }
    }
}

@Composable
private fun LanguageItem(
    label: String,
    code: String,
    current: String,
    onSelect: (String) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onSelect(code) },
        trailingIcon = if (code == current) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else null,
    )
}

private fun selectLanguage(context: Context, language: String) {
    if (language == LocaleHelper.getPersistedLanguage(context)) return
    LocaleHelper.persistLanguage(context, language)
    context.findActivity()?.recreate()
}
