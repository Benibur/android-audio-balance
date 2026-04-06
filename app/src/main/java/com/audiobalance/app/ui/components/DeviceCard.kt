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
    onAutoApplyToggle: (Boolean) -> Unit
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Convert -100..+100 to 0..1 for Slider API
                val sliderValue = (device.balance + 100f) / 200f

                Slider(
                    value = sliderValue,
                    onValueChange = { normalized ->
                        val balance = (normalized * 200f) - 100f
                        onBalanceChange(balance)
                    },
                    onValueChangeFinished = {
                        onBalanceFinished(device.balance)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = stringResource(R.string.slider_label_right),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
