package com.viberemote.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.CaptureActivity
import com.viberemote.mobile.net.RemoteClient
import com.viberemote.mobile.net.WsEvent
import com.viberemote.mobile.ui.MrVoice
import com.viberemote.mobile.ui.RemoteScreen
import com.viberemote.mobile.ui.VibeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var client: RemoteClient

    /** 待确认的配对码（服务器 pair_info → 对话框） */
    data class ConfirmInfo(val code: String, val expiresIn: Int)

    private val confirmInfo = MutableStateFlow<ConfirmInfo?>(null)
    private val errorTip = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        client = RemoteClient(scope) { ev -> handleEvent(ev) }

        // 语音方向命令 → 注入电脑按键（上/下/左/右/确认/返回/1-4）
        MrVoice.init(applicationContext) { cmd, label ->
            scope.launch {
                client.lastVoice.value = label
                client.keyTap(cmd)
            }
        }

        setContent {
            MaterialTheme(colorScheme = VibeColors()) {
                AppContent(
                    client = client,
                    confirmInfo = confirmInfo.collectAsState().value,
                    errorTip = errorTip.collectAsState().value,
                    onClearError = { errorTip.value = null },
                )
            }
        }
    }

    private fun handleEvent(ev: WsEvent) {
        scope.launch {
            when (ev) {
                is WsEvent.PairInfo -> confirmInfo.value = ConfirmInfo(ev.confirmCode, ev.expiresIn)
                is WsEvent.Paired -> confirmInfo.value = null
                is WsEvent.Error -> errorTip.value = ev.message.ifBlank { "连接错误 (${ev.code})" }
                else -> {}
            }
        }
    }

    override fun onDestroy() {
        client.disconnect()
        scope.cancel()
        super.onDestroy()
    }
}

// ---------------- 工具 ----------------

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun loadLastUrl(ctx: Context): String =
    ctx.getSharedPreferences("vibe_remote", Context.MODE_PRIVATE).getString("last_url", "") ?: ""

private fun saveLastUrl(ctx: Context, url: String) {
    ctx.getSharedPreferences("vibe_remote", Context.MODE_PRIVATE)
        .edit().putString("last_url", url).apply()
}

/** 解析用户输入的 URL / 扫码内容 → (wsUrl, pairToken) */
fun parsePairUrl(raw: String): Pair<String, String?> {
    var s = raw.trim()
    if (s.isEmpty()) return "" to null
    var token: String? = null
    val qi = s.indexOf('?')
    if (qi >= 0) {
        token = Regex("[?&]pair=([^&]+)").find(s.substring(qi))?.groupValues?.get(1)
        s = s.substring(0, qi)
    }
    // 分离路径（尾部 "/"、"/ws" 等），避免残留斜杠破坏解析
    var path = ""
    val proto = s.indexOf("://")
    val pathStart = if (proto >= 0) s.indexOf('/', proto + 3) else -1
    if (pathStart >= 0) {
        path = s.substring(pathStart).trimEnd('/')
        s = s.substring(0, pathStart)
    }
    var scheme = "ws"
    if (s.contains("://")) {
        val parts = s.split("://", limit = 2)
        scheme = parts[0].lowercase()
        s = parts[1]
    }
    val hp = if (s.contains(':')) s else "$s:8765"
    val wsScheme = if (scheme == "https" || scheme == "wss") "wss" else "ws"
    val base = "$wsScheme://$hp"
    return (if (path == "/ws") base else "$base/ws") to token
}

// ---------------- Compose 内容 ----------------

@Composable
private fun AppContent(
    client: RemoteClient,
    confirmInfo: MainActivity.ConfirmInfo?,
    errorTip: String?,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var pairUrl by rememberSaveable { mutableStateOf(loadLastUrl(context)) }

    // 扫码（zxing CaptureActivity 返回 SCAN_RESULT）
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringExtra("SCAN_RESULT")
        if (!text.isNullOrBlank()) {
            pairUrl = text
            saveLastUrl(context, text)
            doConnect(client, text)
        }
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) activity?.let { scanLauncher.launch(Intent(it, CaptureActivity::class.java)) }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // 启动即确保录音权限（语音识别 / 按住说话都依赖）
    LaunchedEffect(Unit) {
        val a = activity ?: return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun ensureMic() {
        val a = activity ?: return
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun onScanClick() {
        ensureMic()
        val a = activity ?: return
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) scanLauncher.launch(Intent(a, CaptureActivity::class.java))
        else camPerm.launch(Manifest.permission.CAMERA)
    }

    fun onConnectClick() {
        ensureMic()
        doConnect(client, pairUrl)
    }

    // Agent 页屏幕亮度策略：焦点任务激活时调亮；null 恢复系统亮度
    fun applyAgentBrightness(value: Float?) {
        val a = activity ?: return
        val lp = a.window.attributes
        lp.screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        a.window.attributes = lp
    }

    Box(Modifier.fillMaxSize()) {
        RemoteScreen(
            client = client,
            pairUrlInput = pairUrl,
            onPairUrlChange = { pairUrl = it },
            onConnect = { onConnectClick() },
            onScanQr = { onScanClick() },
            onNeedsMic = { ensureMic() },
            onAgentBrightness = { applyAgentBrightness(it) },
        )

        // 配对确认码弹窗
        confirmInfo?.let { info ->
            var codeText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { },
                containerColor = Color(0xFF1B1E24),
                title = { Text("配对确认", color = Color(0xFFE6E9EF), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "电脑端控制台显示了一组确认码，请核对后输入",
                            color = Color(0xFF8B93A3), fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "电脑端确认码：${info.code}",
                            color = Color(0xFF4F7CFF), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { codeText = it.take(8) },
                            label = { Text("输入确认码", color = Color(0xFF8B93A3)) },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { client.confirm(codeText.trim()) }) {
                        Text("确认配对", color = Color(0xFF4F7CFF))
                    }
                },
            )
        }

        // 错误提示条（顶栏下方独立行，避免遮挡标题/状态）
        errorTip?.let { tip ->
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF47067))
                    .clickable(onClick = onClearError)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(tip, color = Color.White, fontSize = 12.sp, maxLines = 3)
            }
        }
    }
}

private fun doConnect(client: RemoteClient, raw: String) {
    if (raw.isBlank()) return
    val (wsUrl, token) = parsePairUrl(raw)
    client.connect(wsUrl, token)
}