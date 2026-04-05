package com.audiobalance.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audiobalance.app.poc.AudioEffectPoc
import com.audiobalance.app.poc.InternalAudioSource
import com.audiobalance.app.ui.theme.AudioBalanceTheme

class MainActivity : ComponentActivity() {

    private var effectRef: AudioEffectPoc? = null
    private var sourceRef: InternalAudioSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioBalanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    val source = remember { InternalAudioSource(context) }
                    val effect = remember {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            AudioEffectPoc()
                        } else null
                    }

                    // Keep references for onDestroy cleanup
                    sourceRef = source
                    effectRef = effect

                    var logText by remember { mutableStateOf("Ready. Tap 'Play internal tone' to start.") }
                    var balanceLabel by remember { mutableStateOf("Center") }

                    DisposableEffect(Unit) {
                        onDispose {
                            effect?.releaseEffect()
                            source.release()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AudioBalance POC",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try {
                                    val sessionId = source.prepare(R.raw.test_tone)
                                    val effectCreated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        effect?.createOnSession(sessionId) ?: false
                                    } else {
                                        false
                                    }
                                    source.play()
                                    logText = buildString {
                                        append("Session ID: $sessionId\n")
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && effect != null) {
                                            append(effect.lastAttemptLog)
                                        } else {
                                            append("DynamicsProcessing not available (API < 28)")
                                        }
                                    }
                                    balanceLabel = "Center"
                                } catch (e: Exception) {
                                    logText = "Play failed: ${e.javaClass.simpleName}: ${e.message}"
                                }
                            }
                        ) {
                            Text("Play internal tone")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { source.pause() }
                        ) {
                            Text("Stop")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    effect?.applyBalance(-1f)
                                }
                                balanceLabel = "Full Left"
                                logText = (logText.substringBefore("\nBalance:")) + "\nBalance: Full Left (L=0dB R=-60dB)"
                            }
                        ) {
                            Text("Full Left")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    effect?.applyBalance(0f)
                                }
                                balanceLabel = "Center"
                                logText = (logText.substringBefore("\nBalance:")) + "\nBalance: Center (L=0dB R=0dB)"
                            }
                        ) {
                            Text("Center")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    effect?.applyBalance(1f)
                                }
                                balanceLabel = "Full Right"
                                logText = (logText.substringBefore("\nBalance:")) + "\nBalance: Full Right (L=-60dB R=0dB)"
                            }
                        ) {
                            Text("Full Right")
                        }

                        Text(
                            text = "--- Log ---\n$logText",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        effectRef?.releaseEffect()
        sourceRef?.release()
    }
}
