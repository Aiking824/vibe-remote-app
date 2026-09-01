package com.viberemote.mobile.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 手机端语音方向命令：按住按钮 → Android 本机语音识别（zh-CN），
 * 识别词表：上/下/左/右/前/后/确认/确定/回车/返回/退出/取消/1-4/一二三四。
 * 命中后将命令（up/down/left/right/enter/escape/n1-n4）回传，由 UI 注入电脑。
 */
class VoiceCmd(
    private val context: Context,
    private val onCmd: (cmd: String, label: String) -> Unit,
    private val onState: (listening: Boolean) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    private val words = listOf(
        "上", "下", "左", "右", "前", "后",
        "确认", "确定", "回车", "返回", "退出", "取消",
        "1", "2", "3", "4", "一", "二", "三", "四",
    )

    private val map = mapOf(
        "上" to "up", "前" to "up",
        "下" to "down", "后" to "down",
        "左" to "left",
        "右" to "right",
        "确认" to "enter", "确定" to "enter", "回车" to "enter",
        "返回" to "escape", "退出" to "escape", "取消" to "escape",
        "1" to "n1", "2" to "n2", "3" to "n3", "4" to "n4",
        "一" to "n1", "二" to "n2", "三" to "n3", "四" to "n4",
    )

    private val labels = mapOf(
        "up" to "上 ↑", "down" to "下 ↓", "left" to "左 ←", "right" to "右 →",
        "enter" to "确认 ↵", "escape" to "返回",
        "n1" to "数字 1", "n2" to "数字 2", "n3" to "数字 3", "n4" to "数字 4",
    )

    /** 触发一次识别；返回是否成功启动 */
    fun recognize(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return false
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context) ?: return false
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            r.startListening(intent)
            onState(true)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun stop() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        onState(false)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val arr = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = arr?.firstOrNull()?.trim() ?: ""
            handle(text)
            onState(false)
            destroy()
        }

        override fun onError(error: Int) {
            onState(false)
            destroy()
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handle(text: String) {
        if (text.isEmpty()) return
        for (w in words) {
            if (text.contains(w)) {
                val cmd = map[w] ?: continue
                onCmd(cmd, labels[cmd] ?: cmd)
                return
            }
        }
    }

    private fun destroy() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }
}