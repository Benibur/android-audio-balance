package com.audiobalance.app

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audiobalance.app.poc.AudioEffectPoc
import com.audiobalance.app.poc.FallbackProbes
import com.audiobalance.app.poc.InternalAudioSource
import com.audiobalance.app.poc.SessionBroadcastReceiver
import com.audiobalance.app.ui.theme.AudioBalanceTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

                    // ---- Plan 01 state ----
                    var logText by remember { mutableStateOf("Ready. Tap 'Play internal tone' to start.") }
                    var balanceLabel by remember { mutableStateOf("Center") }

                    // ---- Plan 02 state ----
                    var externalEffect by remember { mutableStateOf<DynamicsProcessing?>(null) }
                    var loudnessEffect by remember { mutableStateOf<LoudnessEnhancer?>(null) }
                    var discoveredSessionsText by remember { mutableStateOf("(none)") }
                    var attachedExternalSessionId by remember { mutableStateOf(-1) }
                    val logLines = remember { mutableStateListOf<String>() }

                    fun ts(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    fun log(msg: String) { logLines.add("[${ts()}] $msg") }

                    DisposableEffect(Unit) {
                        onDispose {
                            effect?.releaseEffect()
                            effect?.releaseGlobal()
                            source.release()
                            try { externalEffect?.setEnabled(false); externalEffect?.release() } catch (_: Exception) {}
                            try { loudnessEffect?.setEnabled(false); loudnessEffect?.release() } catch (_: Exception) {}
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

                        // ================================================================
                        // PLAN 01 — Internal tone balance (preserved exactly)
                        // ================================================================

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
                                    log("Play internal tone: session=$sessionId")
                                } catch (e: Exception) {
                                    logText = "Play failed: ${e.javaClass.simpleName}: ${e.message}"
                                    log("Play FAILED: ${e.message}")
                                }
                            }
                        ) {
                            Text("Play internal tone")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { source.pause(); log("Stop") }
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
                                log("Internal Full Left")
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
                                log("Internal Center")
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
                                log("Internal Full Right")
                            }
                        ) {
                            Text("Full Right")
                        }

                        Text(
                            text = "--- Log ---\n$logText",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // ================================================================
                        // PLAN 02 — External audio fallbacks
                        // ================================================================

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "--- External audio fallbacks ---",
                            style = MaterialTheme.typography.labelMedium
                        )

                        // Button 1: Discover active sessions
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val sessions = FallbackProbes.discoverActiveSessions(context)
                                val broadcastSessions = SessionBroadcastReceiver.observedSessions.entries
                                    .joinToString(separator = "\n") { "  broadcast: id=${it.key} pkg=${it.value}" }
                                val audioManagerText = if (sessions.isEmpty()) {
                                    "  AudioManager: none"
                                } else {
                                    sessions.joinToString(separator = "\n") { "  AudioManager: id=${it.sessionId} usage=${it.usage}" }
                                }
                                val broadcastText = if (broadcastSessions.isBlank()) {
                                    "  Broadcast: none yet"
                                } else {
                                    broadcastSessions
                                }
                                discoveredSessionsText = "AudioManager (${sessions.size}):\n$audioManagerText\nBroadcast:\n$broadcastText"
                                log("Discover sessions: AudioManager=${sessions.size} broadcast=${SessionBroadcastReceiver.observedSessions.size}")
                            }
                        ) {
                            Text("Discover active sessions")
                        }

                        Text(
                            text = discoveredSessionsText,
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Button 2: Attach DP to first external session
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val sessions = FallbackProbes.discoverActiveSessions(context)
                                    val internalId = try { source.prepare(R.raw.test_tone) } catch (_: Exception) { -1 }
                                    val target = sessions.firstOrNull { it.sessionId != 0 && it.sessionId != internalId && it.sessionId != -1 }
                                    if (target == null) {
                                        log("Attach DP: no external session found (start Spotify/YouTube first)")
                                    } else {
                                        try { externalEffect?.setEnabled(false); externalEffect?.release() } catch (_: Exception) {}
                                        val dp = FallbackProbes.tryDynamicsProcessingOnSession(target.sessionId)
                                        externalEffect = dp
                                        attachedExternalSessionId = target.sessionId
                                        if (dp != null) {
                                            log("Attach DP: session=${target.sessionId} OK — use External L/C/R buttons")
                                        } else {
                                            log("Attach DP: session=${target.sessionId} FAILED (see logcat)")
                                        }
                                    }
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("Attach DP to first external session")
                        }

                        // Button 3: External Full Left
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val dp = externalEffect
                                    if (dp != null) {
                                        try {
                                            dp.setInputGainbyChannel(0, 0f)
                                            dp.setInputGainbyChannel(1, -60f)
                                            log("External Full Left applied (session=$attachedExternalSessionId)")
                                        } catch (e: RuntimeException) {
                                            log("External Full Left FAILED: ${e.message}")
                                        }
                                    } else {
                                        log("External Full Left: no effect attached — tap 'Attach DP' first")
                                    }
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("External: Full Left")
                        }

                        // Button 4: External Center
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val dp = externalEffect
                                    if (dp != null) {
                                        try {
                                            dp.setInputGainbyChannel(0, 0f)
                                            dp.setInputGainbyChannel(1, 0f)
                                            log("External Center applied (session=$attachedExternalSessionId)")
                                        } catch (e: RuntimeException) {
                                            log("External Center FAILED: ${e.message}")
                                        }
                                    } else {
                                        log("External Center: no effect attached")
                                    }
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("External: Center")
                        }

                        // Button 5: External Full Right
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val dp = externalEffect
                                    if (dp != null) {
                                        try {
                                            dp.setInputGainbyChannel(0, -60f)
                                            dp.setInputGainbyChannel(1, 0f)
                                            log("External Full Right applied (session=$attachedExternalSessionId)")
                                        } catch (e: RuntimeException) {
                                            log("External Full Right FAILED: ${e.message}")
                                        }
                                    } else {
                                        log("External Full Right: no effect attached")
                                    }
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("External: Full Right")
                        }

                        // Button 6: Send OPEN broadcast for internal session
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try {
                                    val sessionId = source.prepare(R.raw.test_tone)
                                    FallbackProbes.sendOpenSessionBroadcast(context, sessionId)
                                    log("Sent OPEN broadcast for internal session=$sessionId")
                                } catch (e: Exception) {
                                    log("Send broadcast FAILED: ${e.message}")
                                }
                            }
                        ) {
                            Text("Send OPEN broadcast for internal session")
                        }

                        // Button 7: Probe LoudnessEnhancer session 0
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try { loudnessEffect?.setEnabled(false); loudnessEffect?.release() } catch (_: Exception) {}
                                val le = FallbackProbes.tryLoudnessEnhancerSession0()
                                loudnessEffect = le
                                if (le != null) {
                                    log("LoudnessEnhancer session=0 OK (probe only — cannot do L/R balance)")
                                } else {
                                    log("LoudnessEnhancer session=0 FAILED (see logcat for reason)")
                                }
                            }
                        ) {
                            Text("Probe LoudnessEnhancer session 0")
                        }
                        Text(
                            text = "Note: LoudnessEnhancer cannot do L/R balance — probe only",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Button 8: Release external + LE
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try { externalEffect?.setEnabled(false); externalEffect?.release() } catch (_: Exception) {}
                                externalEffect = null
                                try { loudnessEffect?.setEnabled(false); loudnessEffect?.release() } catch (_: Exception) {}
                                loudnessEffect = null
                                attachedExternalSessionId = -1
                                log("Released external DP + LoudnessEnhancer")
                            }
                        ) {
                            Text("Release external + LE")
                        }

                        // ================================================================
                        // SESSION 0 GLOBAL test — "create BEFORE media player" protocol
                        // ================================================================

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "--- Session 0 GLOBAL test (YouTube) ---",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Protocol: force-stop YouTube, tap Attach, tap Full Left, THEN open YouTube.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Button G1: Attach DP session 0 GLOBAL
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val result = effect?.createGlobalSession0()
                                        ?: "DynamicsProcessing not available (API < 28)"
                                    log("Global session=0: $result")
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("Attach DP session 0 GLOBAL")
                        }

                        // Button G2: Global Full Left
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val result = effect?.applyGlobalBalance(0f, -60f)
                                        ?: "DynamicsProcessing not available (API < 28)"
                                    log("Global Full Left: $result")
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("Global: Full Left")
                        }

                        // Button G3: Global Center
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val result = effect?.applyGlobalBalance(0f, 0f)
                                        ?: "DynamicsProcessing not available (API < 28)"
                                    log("Global Center: $result")
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("Global: Center")
                        }

                        // Button G4: Global Full Right
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val result = effect?.applyGlobalBalance(-60f, 0f)
                                        ?: "DynamicsProcessing not available (API < 28)"
                                    log("Global Full Right: $result")
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("Global: Full Right")
                        }

                        // Button G5: Release global
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    val result = effect?.releaseGlobal() ?: "n/a"
                                    log("Release global: $result")
                                } else {
                                    log("Not supported on this Android version (API < 28)")
                                }
                            }
                        ) {
                            Text("Release global")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Scrollable event log
                        Text(
                            text = "--- Event log ---",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (logLines.isEmpty()) {
                            Text(
                                text = "(no events yet)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = logLines.takeLast(30).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
