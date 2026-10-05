package com.weixu.ueatsmonitor.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.weixu.ueatsmonitor.action.RulesEditor
import com.weixu.ueatsmonitor.domain.Words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * One way for every screen to change the rules and say how it went.
 *
 * A change is a calculation; everything around it - the file, the cloud, the
 * word to the driver - is the same every time, so it lives here once.
 */
@Composable
fun rememberRuleWriter(onSaved: () -> Unit): RuleWriter {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val words = words()
    return RuleWriter(context, scope, words, onSaved)
}

class RuleWriter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val words: Words,
    private val onSaved: () -> Unit,
) {
    /** The rules as they stand, for a screen to draw from. */
    fun rules(): JsonObject = RulesEditor.read(context)

    operator fun invoke(change: (JsonObject) -> JsonObject) {
        scope.launch {
            val said = when (val saved = RulesEditor.edit(context, change)) {
                RulesEditor.Saved.Pushed -> words.savedToCloud
                is RulesEditor.Saved.OnPhoneOnly -> words.savedOnPhoneOnly
                RulesEditor.Saved.CloudMovedOn -> words.cloudMovedOn
            }
            Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }
}
