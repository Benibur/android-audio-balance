package com.audiobalance.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiobalance.app.R
import com.audiobalance.app.ui.state.DeviceUiState
import kotlin.math.roundToInt

@Composable
fun DeviceCard(
    device: DeviceUiState,
    onBalanceChange: (Float) -> Unit,
    onBalanceFinished: (Float) -> Unit,
    onAutoApplyToggle: (Boolean) -> Unit,
    onGainOffsetChange: (Float) -> Unit,
    onGainOffsetFinished: (Float) -> Unit
) {
    val context = LocalContext.current

    val cardModifier = Modifier
        .fillMaxWidth()
        .then(if (!device.isConnected) Modifier.alpha(0.72f) else Modifier)
        .then(
            if (device.isConnected) Modifier.border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium)
            else Modifier
        )

    ElevatedCard(modifier = cardModifier) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Row 1 — Device header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: device name
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Connected badge (if connected)
                if (device.isConnected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.connected_badge)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.BluetoothConnected,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Auto-apply toggle
                Switch(
                    checked = device.autoApplyEnabled,
                    onCheckedChange = onAutoApplyToggle,
                    modifier = Modifier.semantics {
                        contentDescription = context.getString(
                            R.string.auto_apply_content_description,
                            device.name
                        )
                    }
                )
            }

            // Row 2 — Balance label
            Spacer(modifier = Modifier.height(8.dp))

            val balanceInt = device.balance.roundToInt()
            val balanceText = when {
                balanceInt == 0 -> stringResource(R.string.balance_center)
                balanceInt > 0  -> stringResource(R.string.balance_right, balanceInt)
                else            -> stringResource(R.string.balance_left, -balanceInt)
            }
            val balanceLabelColor = if (balanceInt == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }

            Text(
                text = balanceText,
                style = MaterialTheme.typography.labelMedium,
                color = balanceLabelColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Row 3 — Slider row
            Spacer(modifier = Modifier.height(8.dp))

            // Reduce opacity of slider row when auto-apply is off
            val sliderAlpha = if (device.autoApplyEnabled) 1f else 0.5f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(sliderAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.slider_label_left),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )

                // Convert -100..+100 to 0..1 for Slider API
                val sliderValue = (device.balance + 100f) / 200f
                val centerTickColor = MaterialTheme.colorScheme.onSurface

                Slider(
                    value = sliderValue,
                    onValueChange = { normalized ->
                        val balance = (normalized * 200f) - 100f
                        onBalanceChange(balance)
                    },
                    onValueChangeFinished = {
                        onBalanceFinished(device.balance)
                    },
                    enabled = device.autoApplyEnabled,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .drawWithContent {
                            drawContent()
                            val tickHeight = 14.dp.toPx()
                            val tickStroke = 2.dp.toPx()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawLine(
                                color = centerTickColor,
                                start = Offset(cx, cy - tickHeight / 2f),
                                end = Offset(cx, cy + tickHeight / 2f),
                                strokeWidth = tickStroke
                            )
                        }
                )

                Text(
                    text = stringResource(R.string.slider_label_right),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
            }

            // Row 4 — Gain offset label
            Spacer(modifier = Modifier.height(12.dp))

            val gainOffsetInt = device.gainOffset.roundToInt()
            val gainLabelColor = if (gainOffsetInt == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }

            Text(
                text = stringResource(R.string.gain_offset_label, gainOffsetInt),
                style = MaterialTheme.typography.labelMedium,
                color = gainLabelColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Row 5 — Gain offset slider
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(sliderAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "-12",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )

                // Convert -12..0 dB to 0..1 for Slider API
                val gainSliderValue = (device.gainOffset + 12f) / 12f

                Slider(
                    value = gainSliderValue.coerceIn(0f, 1f),
                    onValueChange = { normalized ->
                        val gainDb = normalized * 12f + (-12f)
                        onGainOffsetChange(gainDb)
                    },
                    onValueChangeFinished = {
                        onGainOffsetFinished(device.gainOffset)
                    },
                    enabled = device.autoApplyEnabled,
                    steps = 11,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "0",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}
