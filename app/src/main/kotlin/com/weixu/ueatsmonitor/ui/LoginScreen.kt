package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weixu.ueatsmonitor.App
import com.weixu.ueatsmonitor.action.Cloud
import com.weixu.ueatsmonitor.domain.Words
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One card in the middle of the screen: the mark, email, password, sign in.
 * Sign-up is the quiet line under it, because it happens once.
 *
 * Both remembered from last time, it signs in on its own and shows only a
 * spinner: the driver is never asked to type at the wheel. Errors are shown
 * in plain words rather than the server's English.
 */
@Composable
fun LoginScreen() {
    val words = words()
    val scope = rememberCoroutineScope()
    val store = App.instance.settingsStore
    val focus = LocalFocusManager.current
    val passwordField = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var signingInQuietly by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun attempt(what: String, action: suspend () -> Unit) {
        val trimmedEmail = email.trim()
        problem = when {
            !trimmedEmail.contains('@') -> words.badEmail
            password.length < 6 -> words.shortPassword
            else -> null
        }
        if (problem != null) return
        focus.clearFocus()
        busy = true
        scope.launch {
            runCatching { action() }
                .onSuccess { store.rememberLogin(trimmedEmail, password) }
                .onFailure { problem = plainWords(what, it, words) }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        val saved = store.settings.first()
        email = saved.lastEmail
        password = saved.lastPassword
        if (saved.lastEmail.isNotEmpty() && saved.lastPassword.isNotEmpty()) {
            attempt(words.signIn) { Cloud.signIn(saved.lastEmail, saved.lastPassword) }
        }
        signingInQuietly = false
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Dash.Ground).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        if (signingInQuietly || (busy && problem == null)) {
            QuietSignIn()
            return@Box
        }
        Column(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mark()
            Spacer(Modifier.height(18.dp))
            Text(words.appName, style = MaterialTheme.typography.headlineMedium, color = Dash.Ink, fontWeight = FontWeight.Bold)
            Text(
                words.rulesShared,
                style = MaterialTheme.typography.bodyMedium,
                color = Dash.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(28.dp))
            Panel(padding = PaddingValues(20.dp), spacing = 12.dp) {
                LoginField(
                    label = words.email,
                    value = email,
                    icon = { Icon(Icons.Filled.Email, null, tint = Dash.Muted) },
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    actions = KeyboardActions(onNext = { passwordField.requestFocus() }),
                ) { email = it; problem = null }
                LoginField(
                    label = words.password,
                    value = password,
                    icon = { Icon(Icons.Filled.Lock, null, tint = Dash.Muted) },
                    trailing = {
                        Text(
                            if (showPassword) words.hide else words.show,
                            color = Dash.Gold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { showPassword = !showPassword }.padding(8.dp),
                        )
                    },
                    hide = !showPassword,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    actions = KeyboardActions(onDone = { attempt(words.signIn) { Cloud.signIn(email.trim(), password) } }),
                    modifier = Modifier.focusRequester(passwordField),
                ) { password = it; problem = null }
                problem?.let { Problem(it) }
                Spacer(Modifier.height(4.dp))
                GoldButton(words.signIn, Modifier.fillMaxWidth()) {
                    attempt(words.signIn) { Cloud.signIn(email.trim(), password) }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(words.noAccountYet, color = Dash.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    words.signUp,
                    color = Dash.Gold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { attempt(words.signUp) { Cloud.signUp(email.trim(), password) } }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            Text(
                words.fillBothFirst,
                color = Dash.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** The app's mark: a gold ring with the one character that says what it does. */
@Composable
private fun Mark() {
    val words = words()
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Dash.GoldDeep)
            .border(2.dp, Dash.Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(words.markGlyph, color = Dash.Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

/** A spinner and one line, while a remembered login is being tried. */
@Composable
private fun QuietSignIn() {
    val words = words()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(color = Dash.Gold, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        Text(words.signingIn, color = Dash.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The reason a sign-in failed, in a soft box so it reads as a message, not a fault line. */
@Composable
private fun Problem(text: String) {
    Text(
        text,
        color = Dash.Orange,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dash.ControlShape)
            .background(Dash.OrangeDeep)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    keyboard: KeyboardOptions,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    hide: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = icon,
        trailingIcon = trailing,
        singleLine = true,
        keyboardOptions = keyboard,
        keyboardActions = actions,
        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = Dash.ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Dash.Gold,
            unfocusedBorderColor = Dash.Line,
            focusedLabelColor = Dash.Gold,
            unfocusedLabelColor = Dash.Muted,
            cursorColor = Dash.Gold,
            focusedTextColor = Dash.Ink,
            unfocusedTextColor = Dash.Ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Calculation. The server's words, in the driver's. */
internal fun plainWords(what: String, error: Throwable, words: Words): String {
    val text = error.message.orEmpty().lowercase()
    return when {
        "invalid login credentials" in text || "invalid_credentials" in text -> words.wrongEmailOrPassword
        "already registered" in text || "already been registered" in text || "user_already_exists" in text -> words.alreadyRegistered
        "email not confirmed" in text -> words.emailNotConfirmed
        "network" in text || "unable to resolve host" in text || "timeout" in text || "connect" in text -> words.noNetwork
        else -> words.failed(what, error.message.orEmpty())
    }
}

