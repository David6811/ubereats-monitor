package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.Cloud
import kotlinx.coroutines.launch

/**
 * Email and password, sign in or sign up. Nothing else: the rules behind it
 * are the driver's own, and this is only how the cloud knows whose they are.
 *
 * Errors are shown in plain words under the buttons rather than as the
 * server's English, which names things like "credentials" and "JWT".
 */
@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun attempt(what: String, action: suspend () -> Unit) {
        val trimmedEmail = email.trim()
        problem = when {
            !trimmedEmail.contains('@') -> "邮箱不对"
            password.length < 6 -> "密码至少 6 位"
            else -> null
        }
        if (problem != null) return
        busy = true
        scope.launch {
            runCatching { action() }
                .onFailure { problem = plainWords(what, it) }
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("接单助手", style = MaterialTheme.typography.headlineMedium, color = Dash.Ink)
        Text(
            "登录后，规则在手机和电脑上是同一份。",
            style = MaterialTheme.typography.bodyMedium,
            color = Dash.Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
        LoginField("邮箱", email, KeyboardType.Email) { email = it }
        Spacer(Modifier.height(12.dp))
        LoginField("密码", password, KeyboardType.Password, secret = true) { password = it }
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Dash.Orange, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(20.dp))
        GoldButton(if (busy) "请稍等…" else "登录", Modifier.fillMaxWidth()) {
            if (!busy) attempt("登录") { Cloud.signIn(email.trim(), password) }
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("注册新账号", Modifier.fillMaxWidth()) {
            if (!busy) attempt("注册") { Cloud.signUp(email.trim(), password) }
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    keyboard: KeyboardType,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.titleMedium,
        shape = Dash.ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Dash.Gold,
            unfocusedBorderColor = Dash.Line,
            focusedLabelColor = Dash.Gold,
            unfocusedLabelColor = Dash.Muted,
            cursorColor = Dash.Gold,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Calculation. The server's words, in the driver's. */
internal fun plainWords(what: String, error: Throwable): String {
    val text = error.message.orEmpty().lowercase()
    return when {
        "invalid login credentials" in text || "invalid_credentials" in text -> "邮箱或密码不对"
        "already registered" in text || "already been registered" in text || "user_already_exists" in text -> "这个邮箱已经注册过，直接登录"
        "email not confirmed" in text -> "邮箱还没确认，去邮箱点一下链接"
        "network" in text || "unable to resolve host" in text || "timeout" in text || "connect" in text -> "没网，连不上"
        else -> what + "失败：" + (error.message ?: "未知原因")
    }
}
