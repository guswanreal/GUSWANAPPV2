package com.example.myapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.Ink700
import com.example.myapplication.ui.theme.Ink750
import com.example.myapplication.ui.theme.Ink800
import com.example.myapplication.ui.theme.Line
import com.example.myapplication.ui.theme.LineStrong
import com.example.myapplication.ui.theme.Accent
import com.example.myapplication.ui.theme.Ink950
import com.example.myapplication.ui.theme.MonoLabel
import com.example.myapplication.ui.theme.TextHigh
import com.example.myapplication.ui.theme.TextLow
import com.example.myapplication.ui.theme.TextMid

/** Surface tile. Passing [onClick] makes it pressable, with a short scale-down as tactile feedback. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = Ink800,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "panelScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(color)
            .border(if(focused) 2.dp else 1.dp, if(focused) Accent else if (pressed) LineStrong else Line, shape)
            .onFocusChanged { focused=it.isFocused }
            .then(
                if (onClick != null) Modifier.clickable(interaction, LocalIndication.current, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                else Modifier
            )
    ) { content() }
}

/** Icon button with a 44dp touch target and an animated active state. */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = TextMid,
    activeColor: Color = Accent,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (active) activeColor.copy(alpha = 0.18f) else Color.Transparent, tween(180), label = "toolBg")
    val fg by animateColorAsState(
        when {
            !enabled -> TextLow.copy(alpha = 0.5f)
            active -> activeColor
            else -> tint
        }, tween(180), label = "toolFg"
    )
    Box(
        modifier
            .size(size.coerceAtLeast(44.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if(focused) 2.dp else 0.dp,if(focused) Accent else Color.Transparent,RoundedCornerShape(12.dp))
            .onFocusChanged { focused=it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
    }
}

/** Solid button: accent fill for the primary action, graphite for everything else. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "btnScale")
    val fg = if (primary) Ink950 else TextHigh
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) Accent else Ink700)
            .border(if(focused) 2.dp else 0.dp,if(focused) (if(primary) Ink950 else TextHigh) else Color.Transparent,RoundedCornerShape(10.dp))
            .onFocusChanged { focused=it.isFocused }
            .then(if (primary) Modifier else Modifier.border(1.dp, Line, RoundedCornerShape(10.dp)))
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Accent,
) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.2f) else Ink750, tween(160), label = "chipBg")
    val border by animateColorAsState(if (selected) color.copy(alpha = 0.7f) else Line, tween(160), label = "chipBorder")
    Row(
        modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) color else TextMid, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) TextHigh else TextMid)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Labelled slider with a monospace value readout, used by both property inspectors. */
@Composable
fun PropertySlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { "%.2f".format(it) },
    color: Color = Accent,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMid, modifier = Modifier.weight(1f))
            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                color = TextHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Ink700)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            trailing?.invoke()
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = color,
                inactiveTrackColor = LineStrong,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun StatPill(text: String, modifier: Modifier = Modifier, color: Color = TextMid) {
    Text(
        text,
        style = MonoLabel,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit, label: String) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 34.dp else 28.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else LineStrong, CircleShape)
        )
    }
}

