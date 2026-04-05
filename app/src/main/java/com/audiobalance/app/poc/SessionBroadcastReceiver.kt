package com.audiobalance.app.poc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * Receives ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION broadcasts from media players
 * that announce their audio sessions. Used for Approach C (per-session fallback).
 */
class SessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"
        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.d("POC", "Broadcast OPEN session=$sessionId pkg=$pkg")
                observedSessions[sessionId] = pkg
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.d("POC", "Broadcast CLOSE session=$sessionId pkg=$pkg")
                observedSessions.remove(sessionId)
            }
        }
    }

    companion object {
        /** Sessions observed via broadcast, keyed by session id with package name value. */
        val observedSessions: MutableMap<Int, String> = mutableMapOf()
    }
}
