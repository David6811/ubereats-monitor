package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weixu.ueatsmonitor.action.Profiles

/**
 * Picking which set of suburbs is live - the one rule that changes mid-shift.
 *
 * The sets are drawn on the laptop, on the map. Here they are a list you tap.
 */
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(Profiles.list(context)) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("用哪套选区", fontWeight = FontWeight.Bold)
        if (profiles.isEmpty()) {
            Text(
                text = "手机上还没有规则。在电脑的编辑器里点一次「保存并推送到手机」。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        profiles.forEach { profile ->
            // The Card overload that takes onClick, not a clickable modifier: the
            // Surface inside a plain Card swallows the touch before it reaches one.
            Card(
                onClick = {
                    Profiles.choose(context, profile.name)
                    profiles = Profiles.list(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (profile.active) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = profile.active, onClick = null)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(profile.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = "去 " + profile.suburbs + " 个区",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
