package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.weixu.ueatsmonitor.action.Gazetteer
import com.weixu.ueatsmonitor.action.RulesWriter
import com.weixu.ueatsmonitor.domain.SuburbPicker

/**
 * The one rule that changes while the driver is out: which suburbs to go to.
 *
 * No map and no save button - tick a box and it is written. Everything else
 * still belongs on the laptop.
 */
@Composable
fun SuburbScreen() {
    val context = LocalContext.current
    val all = remember { Gazetteer.suburbs(context).map { it.name } }
    val profile = remember { RulesWriter.activeProfile(context) }
    var chosen by remember { mutableStateOf(RulesWriter.allowedSuburbs(context)) }
    var query by remember { mutableStateOf("") }

    val rows = SuburbPicker.rows(all, chosen, query)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (profile == null) "去 " + chosen.size + " 个区" else "「" + profile + "」去 " + chosen.size + " 个区",
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜区名，加新的区") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (rows.isEmpty()) {
            Text(
                text = if (query.isBlank()) "还一个区都没选。上面搜个区名，勾上就去。" else "没有这个区",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows, key = { it.name }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = SuburbPicker.toggle(chosen, row.name)
                            if (RulesWriter.setAllowedSuburbs(context, next)) chosen = next
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = row.chosen, onCheckedChange = null)
                    Text(row.name, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
