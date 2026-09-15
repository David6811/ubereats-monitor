package com.weixu.ueatsmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle

/**
 * The look of the app: a car's instrument cluster. Near-black, one gold accent,
 * hairlines instead of shadows, numbers in tabular figures so they do not
 * shuffle as they change.
 *
 * Read at a glance in a moving car, in sun and at night - so the contrast is
 * high, the type is large, and colour never carries meaning alone. Blue and
 * orange, never green and red: the driver cannot tell those two apart.
 */
object Dash {
    val Ground = Color(0xFF0A0A0B)
    val Panel = Color(0xFF141416)
    val Raised = Color(0xFF1C1C1F)
    val Line = Color(0xFF2A2A2E)
    val Ink = Color(0xFFF3F1EC)
    val Muted = Color(0xFF8F8C85)
    val Gold = Color(0xFFE8B64C)
    val GoldDeep = Color(0xFF3A2E14)
    val Blue = Color(0xFF5AA9FF)
    val BlueDeep = Color(0xFF142637)
    val Orange = Color(0xFFF0883E)
    val OrangeDeep = Color(0xFF3A2414)

    val PanelShape = RoundedCornerShape(18.dp)
    val ControlShape = RoundedCornerShape(14.dp)

    /** Figures that line up in a column and do not jitter as they change. */
    val Numbers = TextStyle(fontFeatureSettings = "tnum")
}

@Composable
fun DashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Dash.Gold,
            onPrimary = Dash.Ground,
            primaryContainer = Dash.GoldDeep,
            onPrimaryContainer = Dash.Gold,
            secondary = Dash.Blue,
            background = Dash.Ground,
            onBackground = Dash.Ink,
            surface = Dash.Ground,
            onSurface = Dash.Ink,
            surfaceVariant = Dash.Panel,
            onSurfaceVariant = Dash.Muted,
            outline = Dash.Line,
            error = Dash.Orange,
            errorContainer = Dash.OrangeDeep,
            onErrorContainer = Dash.Orange,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 17.sp),
            bodyMedium = TextStyle(fontSize = 15.sp),
            bodySmall = TextStyle(fontSize = 13.sp),
            labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        ),
        content = content,
    )
}

/** A panel on the ground: hairline edge, no shadow. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    spacing: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Dash.PanelShape)
            .background(Dash.Panel)
            .border(1.dp, Dash.Line, Dash.PanelShape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The small spaced capitals above a group, the way a gauge is labelled. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Dash.Muted) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** The one thing to do on a panel. */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = Dash.ControlShape,
        colors = ButtonDefaults.buttonColors(containerColor = Dash.Gold, contentColor = Dash.Ground),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Anything else on a panel: present, but quieter than the gold one. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Dash.Ink,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = Dash.ControlShape,
        border = BorderStroke(1.dp, Dash.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A square outlined button that says what it does with a picture, for a row with no room for words. */
@Composable
fun GlyphButton(
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = Dash.ControlShape,
        border = BorderStroke(1.dp, Dash.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Dash.Ink),
        contentPadding = PaddingValues(0.dp),
    ) {
        androidx.compose.material3.Icon(glyph, contentDescription = description)
    }
}

/** A small label with a tinted ground, for a fact about the thing beside it. */
@Composable
fun Tag(text: String, ink: Color, ground: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ground)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A lamp: lit in its colour when on, a dim ring when off. */
@Composable
fun Lamp(on: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(if (on) color else Color.Transparent)
            .border(1.5.dp, if (on) color else Dash.Muted, CircleShape),
    )
}

/** One setting: its words on the left, the switch on the right, the whole row tappable. */
@Composable
fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Dash.Ink)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Dash.Muted) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Dash.Ground,
                checkedTrackColor = Dash.Gold,
                checkedBorderColor = Dash.Gold,
                uncheckedThumbColor = Dash.Muted,
                uncheckedTrackColor = Dash.Raised,
                uncheckedBorderColor = Dash.Line,
            ),
        )
    }
}

/** A hairline between rows inside a panel. */
@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Dash.Line))
}

/** A number the driver types, with its unit beside it rather than inside the label. */
@Composable
fun DashField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.merge(Dash.Numbers),
        shape = Dash.ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Dash.Gold,
            unfocusedBorderColor = Dash.Line,
            focusedLabelColor = Dash.Gold,
            unfocusedLabelColor = Dash.Muted,
            cursorColor = Dash.Gold,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A segmented control: a few choices in one rounded track, the chosen one lifted
 * out in gold. Counts sit beside the words in tabular figures.
 */
@Composable
fun <T> Segments(
    choices: List<Pair<T, String>>,
    chosen: T,
    modifier: Modifier = Modifier,
    onChoose: (T) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Dash.ControlShape)
            .background(Dash.Panel)
            .border(1.dp, Dash.Line, Dash.ControlShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { (value, label) ->
            val on = value == chosen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Dash.Gold else Color.Transparent)
                    .clickable { onChoose(value) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.merge(Dash.Numbers),
                    color = if (on) Dash.Ground else Dash.Muted,
                )
            }
        }
    }
}

/** A row of equal-width buttons. */
@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/**
 * A value re-read every [everyMillis] while the screen is actually showing, and
 * not at all once it is behind another app. A plain loop in produceState keeps
 * running after Home is pressed, and two of them were most of what the app spent
 * sitting in the background.
 */
@Composable
fun <T> rememberPolled(
    initial: T,
    everyMillis: Long,
    vararg keys: Any?,
    read: () -> T,
): androidx.compose.runtime.State<T> {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    return androidx.compose.runtime.produceState(initial, owner, *keys) {
        owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                value = read()
                kotlinx.coroutines.delay(everyMillis)
            }
        }
    }
}
