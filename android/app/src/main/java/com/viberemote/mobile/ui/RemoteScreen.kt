package com.viberemote.mobile.ui

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viberemote.mobile.R
import com.viberemote.mobile.net.AgentStatus
import com.viberemote.mobile.net.KeyDef
import com.viberemote.mobile.net.PeerInfo
import com.viberemote.mobile.net.RemoteClient
import com.viberemote.mobile.AgentGridUiState
import com.viberemote.mobile.ScreenBrightnessPolicy
import com.viberemote.mobile.domain.ControlAction
import com.viberemote.mobile.domain.LinkState
import com.viberemote.mobile.domain.StateSnapshotPayload
import com.viberemote.mobile.domain.TaskDashboardProjection
import com.viberemote.mobile.domain.TaskDashboardProjector
import com.viberemote.mobile.domain.toDomain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════
// 主题（像素字体 + AgentPager 调色板）已迁移至 AgentGridTheme.kt：
//   • PixelFont  = PixelFontFamily 的兼容别名（AgentGridTheme.kt 内定义）
//   • AgColors   = AgentGridColors  的兼容别名（AgentGridTheme.kt 内定义）
// 本文件直接引用，不再重复定义。
// ════════════════════════════════════════════════════════════════════

// 便捷别名，AgentPager 风格：无论系统主题都是深色像素风（AgentPager 只做暗色）
@Composable
private fun ui() = UiPalette(
    bg = AgColors.Bg,
    panel = AgColors.Surface,
    panel2 = AgColors.SurfaceRaised,
    border = AgColors.Divider,
    fg = AgColors.Text,
    sub = AgColors.Muted,
)

private data class UiPalette(
    val bg: Color, val panel: Color, val panel2: Color,
    val border: Color, val fg: Color, val sub: Color,
)

// Material3 ColorScheme（AgentPager 暗色主题，不再跟随系统切换）
@Composable
fun VibeColors(): androidx.compose.material3.ColorScheme {
    return darkColorScheme(
        primary = AgColors.Amber,
        onPrimary = AgColors.Bg,
        secondary = AgColors.Blue,
        onSecondary = AgColors.Text,
        background = AgColors.Bg,
        onBackground = AgColors.Text,
        surface = AgColors.Surface,
        onSurface = AgColors.Text,
        surfaceVariant = AgColors.SurfaceRaised,
        onSurfaceVariant = AgColors.Muted,
        outline = AgColors.Divider,
        error = AgColors.Red,
        onError = AgColors.Bg,
    )
}

// -- Agent 来源徽标（AgentPager 风格配色） --
private fun sourceMark(source: String): Pair<String, Color> = when (source.lowercase()) {
    "claude" -> "C" to Color(0xFFD97757)
    "codex" -> "X" to Color(0xFF6E56CF)
    "trae" -> "T" to AgColors.Amber
    "deepseek" -> "D" to AgColors.Blue
    "trae solo cn", "solo" -> "S" to AgColors.Violet
    else -> "?" to AgColors.Muted
}

// -- 状态 → 中文/颜色（AgentPager 状态色） --
private fun stateCnOf(s: String) = when (s) {
    "READY" -> "空闲"
    "WORKING", "RUNNING" -> "工作中"
    "THINKING" -> "思考中"
    "CONFIRM", "WAITING" -> "待确认"
    "ERROR", "FAILED" -> "出错"
    else -> s
}

private fun stateColorOf(s: String) = when (s) {
    "READY" -> AgColors.Green
    "WORKING", "RUNNING" -> AgColors.Amber
    "THINKING" -> AgColors.Cyan
    "CONFIRM", "WAITING" -> AgColors.Blue
    "ERROR", "FAILED" -> AgColors.Red
    else -> AgColors.Muted
}

// ---------------- 页面枚举 ----------------
private enum class Page { PAD, MOUSE, HOT, AGENT }

// ---------------- 3 种连接方式 ----------------
private enum class ConnWay(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val hint: String,
    val defaultUrl: String,
) {
    WIFI(
        id = "wifi",
        title = "连接到同一网络",
        icon = Icons.Filled.SignalWifi4Bar,
        hint =
            "1. 确保手机和电脑连到同一个 Wi-Fi（推荐 5GHz，延迟更低）\n" +
                "2. 电脑打开 http://localhost:8765/pair/console\n" +
                "3. 扫码或复制控制台显示的 pairing URL 粘贴到下方即可",
        defaultUrl = "",
    ),
    USB(
        id = "usb",
        title = "USB 直连电脑",
        icon = Icons.Filled.Cable,
        hint =
            "1. 电脑启动本服务（1_usb_direct.bat），会自动执行 adb reverse tcp:8765 tcp:8765\n" +
                "2. 手机打开“开发者选项 → USB 调试”，用数据线连接电脑\n" +
                "3. 手机上允许 USB 调试后，下方会自动填入 127.0.0.1:8765，点连接即可\n" +
                "4. 此方式不占用 Wi-Fi，语音/按键几乎零延迟",
        defaultUrl = "http://127.0.0.1:8765/",
    ),
    SHARE(
        id = "share",
        title = "手机对电脑 USB 共享网络",
        icon = Icons.Filled.Share,
        hint =
            "1. 手机：设置 → 移动网络 → USB 网络共享 → 开启\n" +
                "2. 电脑会出现一个“远程 NDIS”/“以太网 2”新网卡\n" +
                "3. 打开电脑 cmd 执行 ipconfig，找到该网卡的 IPv4 地址\n" +
                "4. App 填：http://该IP:8765/",
        defaultUrl = "",
    ),
}

/** 主界面 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(
    client: RemoteClient,
    pairUrlInput: String,
    onPairUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onScanQr: () -> Unit,
    onNeedsMic: () -> Unit,
    onAgentBrightness: (Float?) -> Unit,
) {
    val c by client.status.collectAsState()
    val paired by client.paired.collectAsState()
    val keymap by client.keymap.collectAsState()
    val conn by client.connText.collectAsState()
    val phase by client.sessionPhase.collectAsState()
    val sessionMsg by client.sessionMsg.collectAsState()
    val clients by client.clients.collectAsState()
    val peers by client.peers.collectAsState()
    val maxClients by client.maxClients.collectAsState()
    val u = ui()
    var page by remember { mutableStateOf(Page.PAD) }

    Scaffold(
        containerColor = u.bg,
        bottomBar = {
            if (paired) {
                NavigationBar(containerColor = u.panel, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = page == Page.PAD,
                        onClick = { page = Page.PAD },
                        icon = { Icon(Icons.Filled.Keyboard, contentDescription = "遥控器") },
                        label = { Text("遥控", fontSize = 11.sp, fontFamily = PixelFont) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgColors.Amber,
                            selectedTextColor = AgColors.Amber,
                            unselectedIconColor = u.sub,
                            unselectedTextColor = u.sub,
                            indicatorColor = AgColors.Amber.copy(alpha = 0.16f),
                        ),
                    )
                    NavigationBarItem(
                        selected = page == Page.MOUSE,
                        onClick = { page = Page.MOUSE },
                        icon = { Icon(Icons.Filled.Mouse, contentDescription = "鼠标") },
                        label = { Text("鼠标", fontSize = 11.sp, fontFamily = PixelFont) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgColors.Amber,
                            selectedTextColor = AgColors.Amber,
                            unselectedIconColor = u.sub,
                            unselectedTextColor = u.sub,
                            indicatorColor = AgColors.Amber.copy(alpha = 0.16f),
                        ),
                    )
                    NavigationBarItem(
                        selected = page == Page.HOT,
                        onClick = { page = Page.HOT },
                        icon = { Icon(Icons.Filled.SmartButton, contentDescription = "快捷键") },
                        label = { Text("快捷键", fontSize = 11.sp, fontFamily = PixelFont) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgColors.Amber,
                            selectedTextColor = AgColors.Amber,
                            unselectedIconColor = u.sub,
                            unselectedTextColor = u.sub,
                            indicatorColor = AgColors.Amber.copy(alpha = 0.16f),
                        ),
                    )
                    NavigationBarItem(
                        selected = page == Page.AGENT,
                        onClick = { page = Page.AGENT },
                        icon = { Icon(Icons.Filled.Devices, contentDescription = "Agent") },
                        label = { Text("Agent", fontSize = 11.sp, fontFamily = PixelFont) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgColors.Amber,
                            selectedTextColor = AgColors.Amber,
                            unselectedIconColor = u.sub,
                            unselectedTextColor = u.sub,
                            indicatorColor = AgColors.Amber.copy(alpha = 0.16f),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // —— 顶栏 · 第 1 行：标题 + 设备入口 ——
                var showDeviceDialog by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibe Remote", color = u.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (paired) {
                        DeviceChip(
                            count = clients,
                            max = maxClients,
                            onClick = { showDeviceDialog = true },
                        )
                    } else {
                        Text("设备 0/0", color = u.sub, fontSize = 11.sp)
                    }
                }
                if (paired && showDeviceDialog) {
                    DeviceDialog(
                        peers = peers,
                        onReconnect = { onConnect() },
                        onDelete = { pid -> client.deletePeer(pid) },
                        onDisconnect = { client.disconnect() },
                        onDismiss = { showDeviceDialog = false },
                    )
                }
                Spacer(Modifier.height(6.dp))
                // —— 顶栏 · 第 2 行：连接状态（独立整行，避免遮挡） ——
                ConnPill(
                    text = conn,
                    ok = paired,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                if (!paired) {
                    ConnectPage(
                        pairUrlInput = pairUrlInput,
                        onPairUrlChange = onPairUrlChange,
                        onConnect = onConnect,
                        onScanQr = onScanQr,
                    )
                } else {
                    when (page) {
                        Page.PAD -> PadPage(
                            client = client,
                            keys = keymap,
                            status = c,
                            phase = phase,
                            sessionMsg = sessionMsg,
                            onNeedsMic = onNeedsMic,
                        )
                        Page.MOUSE -> MousePage(client)
                        Page.HOT -> HotPage(client, keys = keymap)
                        Page.AGENT -> AgentPage(client = client, onAgentBrightness = onAgentBrightness)
                    }
                }
            }
        }
    }
}

// ================= 连接页：4 个大号按钮 + 所选方案说明 + 地址/扫码 =================
@Composable
private fun ConnectPage(
    pairUrlInput: String,
    onPairUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onScanQr: () -> Unit,
) {
    val u = ui()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ways = ConnWay.values().toList()
    var selected by remember { mutableStateOf(ConnWay.WIFI) }
    val scope = rememberCoroutineScope()

    // 选中某个方式后，若带有默认 URL，自动填充 + 立刻发起一次连接尝试
    fun selectWay(way: ConnWay) {
        selected = way
        if (way.defaultUrl.isNotBlank()) {
            onPairUrlChange(way.defaultUrl)
            // 延迟一小拍再连，让上层 state 已更新再取最新输入
            scope.launch {
                delay(120)
                onConnect()
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 3 个连接方案按钮（横屏 1×3，竖屏 1×3）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ways.forEach { way ->
                WayButton(
                    way = way,
                    selected = selected == way,
                    onClick = { selectWay(way) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 选中方案的使用说明（AgentPager 风格：sharp border + Surface）
        Box(
            Modifier
                .fillMaxWidth()
                .background(u.panel)
                .border(1.dp, u.border)
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(selected.icon, contentDescription = null, tint = AgColors.Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "如何使用「${selected.title}」",
                        color = u.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = PixelFont,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    selected.hint,
                    color = u.sub, fontSize = 12.sp, lineHeight = 18.sp,
                    fontFamily = PixelFont,
                )
            }
        }

        // 扫码 + 连接按钮（AgentPager 风格：RectangleShape 直角 + Amber 主色）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onScanQr,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AgColors.Amber),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgColors.Bg)
                Spacer(Modifier.width(6.dp))
                Text("扫码连接", color = AgColors.Bg, fontSize = 13.sp, fontFamily = PixelFont, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RectangleShape,
            ) {
                Icon(Icons.Filled.Cable, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgColors.Amber)
                Spacer(Modifier.width(6.dp))
                Text("直接连接", color = u.fg, fontSize = 13.sp)
            }
        }

        // URL 输入区（放在扫码/连接按钮下方，点按钮前后都能改）
        Box(
            Modifier
                .fillMaxWidth()
                .background(u.panel)
                .padding(12.dp),
        ) {
            Column {
                Text("电脑端配对链接 / 地址", color = u.fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "控制台 http://localhost:8765/pair/console 有二维码可直接扫；也可手动填地址",
                    color = u.sub, fontSize = 11.sp, lineHeight = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pairUrlInput,
                    onValueChange = onPairUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (selected.defaultUrl.isNotBlank()) selected.defaultUrl else "http://电脑IP:8765/?pair=…",
                            color = u.sub, fontSize = 12.sp,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RectangleShape,
                )
            }
        }
    }
}

@Composable
private fun WayButton(
    way: ConnWay,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    Box(
        modifier
            .background(if (selected) AgColors.Amber.copy(alpha = 0.14f) else u.panel)
            .border(1.dp, if (selected) AgColors.Amber else u.border)
            .height(72.dp)
            .pointerInput(way.id) {
                awaitEachGesture {
                    awaitFirstDown()
                    waitForUpOrCancellation()
                    onClick()
                }
            }
            .padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (selected) AgColors.Amber else u.panel2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    way.icon,
                    contentDescription = way.title,
                    tint = if (selected) AgColors.Bg else u.sub,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                way.title,
                color = if (selected) AgColors.Amber else u.fg,
                fontSize = 13.sp,
                fontFamily = PixelFont,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ================= 遥控页（一键全部可见，不再滚动） =================
@Composable
private fun PadPage(
    client: RemoteClient,
    keys: List<KeyDef>,
    status: AgentStatus,
    phase: String,
    sessionMsg: String,
    onNeedsMic: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dpad = keys.filter { it.group == "dpad" }
    val fn = keys.filter { it.group == "fn" }
    val num = keys.filter { it.group == "num" }
    fun k(id: String) = keys.firstOrNull { it.id == id }

    if (landscape) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard(status, Modifier.fillMaxWidth())
                VoiceBar(client, phase, sessionMsg, onNeedsMic, Modifier.fillMaxWidth())
            }
            Column(
                Modifier.weight(1.15f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DPad(dpad, client, k("enter"))
                FnRow(fn, client)
                NumRow(num, client)
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusCard(status, Modifier.fillMaxWidth())
            DPad(dpad, client, k("enter"))
            FnRow(fn, client)
            NumRow(num, client)
            VoiceBar(client, phase, sessionMsg, onNeedsMic, Modifier.fillMaxWidth())
        }
    }
}

/** 十字方向键 + 中间确认（圆形），经典遥控器 3x3 布局 */
@Composable
private fun DPad(dpad: List<KeyDef>, client: RemoteClient, enterDef: KeyDef?) {
    val u = ui()
    val cell = 66.dp
    fun k(id: String) = dpad.firstOrNull { it.id == id }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(cell))
            k("up")?.let { DirKeyButton(it, cell, client) }
            Spacer(Modifier.size(cell))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            k("left")?.let { DirKeyButton(it, cell, client) }
            Spacer(Modifier.width(4.dp))
            if (enterDef != null) {
                var pressed by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(cell)
                        .clip(CircleShape)
                        .background(if (pressed) AgColors.Amber.copy(alpha = 0.3f) else u.panel2, CircleShape)
                        .border(1.5.dp, if (pressed) AgColors.Amber else u.border, CircleShape)
                        .pointerInput(enterDef.id) {
                            awaitEachGesture {
                                awaitFirstDown()
                                pressed = true
                                client.keyTap(enterDef.id)
                                waitForUpOrCancellation()
                                pressed = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = enterDef.label,
                        tint = if (pressed) AgColors.Amber else u.fg,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(cell))
            }
            Spacer(Modifier.width(4.dp))
            k("right")?.let { DirKeyButton(it, cell, client) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(cell))
            k("down")?.let { DirKeyButton(it, cell, client) }
            Spacer(Modifier.size(cell))
        }
    }
}

/** 方向键按钮（可长按连发） */
@Composable
private fun DirKeyButton(def: KeyDef, cell: Dp, client: RemoteClient) {
    PressKeyButton(
        def = def,
        modifier = Modifier.size(cell),
        onTap = { client.keyTap(def.id) },
        onHoldTick = { client.keyDown(def.id); client.keyUp(def.id) },
        holdable = true,
    )
}

/** 功能键：终极确认 / 换行 / Home / TV / 截图 / 删除 + Esc （不再含复制/剪切/粘贴，已移至快捷键页） */
@Composable
private fun FnRow(fn: List<KeyDef>, client: RemoteClient) {
    val main = listOf("ctrl_enter", "shift_enter")
    val rest = fn.filter { it.id !in main }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            main.forEach { id ->
                val def = fn.firstOrNull { it.id == id } ?: return@forEach
                PressKeyButton(
                    def = def,
                    modifier = Modifier.width(92.dp).height(44.dp),
                    onTap = { client.keyTap(def.id) },
                    onHoldTick = {},
                    holdable = false,
                )
            }
        }
        if (rest.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.forEach { def ->
                    PressKeyButton(
                        def = def,
                        modifier = Modifier.width(58.dp).height(42.dp),
                        onTap = { client.keyTap(def.id) },
                        onHoldTick = {},
                        holdable = false,
                    )
                }
            }
        }
    }
}

/** 数字键 1-4 */
@Composable
private fun NumRow(num: List<KeyDef>, client: RemoteClient) {
    if (num.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        num.forEach { def ->
            PressKeyButton(
                def = def,
                modifier = Modifier.width(54.dp).height(40.dp),
                onTap = { client.keyTap(def.id) },
                onHoldTick = {},
                holdable = false,
            )
        }
    }
}

// ================= 触控板页 =================
@Composable
private fun MousePage(client: RemoteClient) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TouchArea(client, Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier.width(140.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MouseButton("左键", { client.mouseClick("left") }, Modifier.weight(1f))
                MouseButton("中键", { client.mouseClick("middle") }, Modifier.weight(1f))
                MouseButton("右键", { client.mouseClick("right") }, Modifier.weight(1f))
                ScrollButton(client, dy = 1, label = "滚轮上", Modifier.weight(1f))
                ScrollButton(client, dy = -1, label = "滚轮下", Modifier.weight(1f))
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            TouchArea(client, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MouseButton("左键", { client.mouseClick("left") }, Modifier.weight(1f))
                MouseButton("中键", { client.mouseClick("middle") }, Modifier.weight(1f))
                MouseButton("右键", { client.mouseClick("right") }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrollButton(client, dy = 1, label = "滚轮上", Modifier.weight(1f))
                ScrollButton(client, dy = -1, label = "滚轮下", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TouchArea(client: RemoteClient, modifier: Modifier) {
    val u = ui()
    var moving by remember { mutableStateOf(false) }
    var accX by remember { mutableIntStateOf(0) }
    var accY by remember { mutableIntStateOf(0) }
    var lastSent by remember { mutableStateOf(0L) }

    Box(
        modifier
            .background(u.panel)
            .border(1.dp, if (moving) AgColors.Amber else u.border)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragged = false
                    moving = true
                    fun flush() {
                        if (accX != 0 || accY != 0) {
                            client.mouseMove(accX, accY)
                            accX = 0; accY = 0
                            lastSent = System.currentTimeMillis()
                        }
                    }
                    drag(down.id) { change ->
                        change.consume()
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        accX += dx.toInt()
                        accY += dy.toInt()
                        dragged = true
                        val now = System.currentTimeMillis()
                        if (now - lastSent >= 30) flush()
                    }
                    waitForUpOrCancellation()
                    flush()
                    moving = false
                    if (!dragged) client.mouseClick("left")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Mouse, contentDescription = "触控板", tint = if (moving) AgColors.Amber else u.sub, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                if (moving) "移动中…" else "拖动移动鼠标 · 轻点左键",
                color = if (moving) AgColors.Yellow else u.sub,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MouseButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val u = ui()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (pressed) AgColors.Amber.copy(alpha = 0.25f) else u.panel2)
            .border(1.dp, if (pressed) AgColors.Amber else u.border)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(); pressed = true; onClick(); waitForUpOrCancellation(); pressed = false
                }
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = u.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScrollButton(client: RemoteClient, dy: Int, label: String, modifier: Modifier) {
    val u = ui()
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .background(if (pressed) AgColors.Amber.copy(alpha = 0.25f) else u.panel2)
            .border(1.dp, if (pressed) AgColors.Amber else u.border)
            .pointerInput(dy) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    var longFired = false
                    val job = scope.launch {
                        delay(280); longFired = true
                        while (true) { client.mouseScroll(dy); delay(150) }
                    }
                    waitForUpOrCancellation()
                    job.cancel(); pressed = false
                    if (!longFired) client.mouseScroll(dy)
                }
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                if (dy > 0) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = label, tint = u.fg, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(label, color = u.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ================= 快捷键页（复制 / 剪切 / 粘贴 + Win+D / Alt+Tab / Win+L 等） =================
@Composable
private fun HotPage(client: RemoteClient, keys: List<KeyDef>) {
    val u = ui()
    val clip = keys.filter { it.group == "clip" }
    val hot = keys.filter { it.group == "hot" }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    @Composable
    fun Section(title: String, items: List<KeyDef>) {
        if (items.isEmpty()) return
        Text(title, color = u.sub, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        if (landscape) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { def ->
                    ClipButton(def, { client.keyTap(def.id) }, Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { def ->
                    ClipButton(def, { client.keyTap(def.id) }, Modifier.weight(1f))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "电脑端常用快捷键（直接在电脑端执行）",
            color = u.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
        Section("剪贴板", clip)
        Section("系统", hot)
        Spacer(Modifier.height(4.dp))
        Text(
            "提示：复制/剪切/粘贴作用于电脑端当前焦点应用。" +
                "Win+L 会立刻锁屏；Alt+Tab 切到下一个窗口。",
            color = u.sub, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ClipButton(def: KeyDef, onTap: () -> Unit, modifier: Modifier) {
    val u = ui()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (pressed) AgColors.Amber.copy(alpha = 0.25f) else u.panel2)
            .border(1.dp, if (pressed) AgColors.Amber else u.border)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(); pressed = true; onTap(); waitForUpOrCancellation(); pressed = false
                }
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(keyIcon(def) ?: Icons.Filled.ContentPaste, contentDescription = def.label, tint = AgColors.Amber, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(def.label, color = u.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(mappingText(def), color = u.sub, fontSize = 9.sp)
        }
    }
}

// ================= 通用按键 =================
@Composable
private fun keyIcon(def: KeyDef): ImageVector? = when (def.id) {
    "up" -> Icons.Filled.KeyboardArrowUp
    "down" -> Icons.Filled.KeyboardArrowDown
    "left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
    "right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    "enter" -> Icons.AutoMirrored.Filled.KeyboardReturn
    "ctrl_enter" -> Icons.AutoMirrored.Filled.Send
    "shift_enter" -> Icons.AutoMirrored.Filled.WrapText
    "home" -> Icons.Filled.Home
    "tv" -> Icons.Filled.Tv
    "shot" -> Icons.Filled.Crop
    "backspace" -> Icons.AutoMirrored.Filled.Backspace
    "escape" -> Icons.Filled.Close
    "copy" -> Icons.Filled.ContentCopy
    "cut" -> Icons.Filled.ContentCut
    "paste" -> Icons.Filled.ContentPaste
    "win_d" -> Icons.Filled.Home
    "alt_tab" -> Icons.Filled.Tv
    "win_l" -> Icons.Filled.Lock
    else -> null
}

@Composable
fun PressKeyButton(
    def: KeyDef, modifier: Modifier,
    onTap: () -> Unit, onHoldTick: () -> Unit, holdable: Boolean,
) {
    val u = ui()
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val icon = keyIcon(def)
    val isNum = def.id.startsWith("n") && def.id.length == 2 && icon == null
    Box(
        modifier = modifier
            .background(if (pressed) AgColors.Amber.copy(alpha = 0.25f) else u.panel2)
            .border(1.dp, if (pressed) AgColors.Amber else u.border)
            .pointerInput(def.id) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    if (!holdable) {
                        onTap(); waitForUpOrCancellation(); pressed = false; return@awaitEachGesture
                    }
                    var longFired = false
                    val job = scope.launch {
                        delay(260); longFired = true
                        while (true) { onHoldTick(); delay(160) }
                    }
                    waitForUpOrCancellation()
                    job.cancel(); pressed = false
                    if (!longFired) onTap()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isNum) {
                Text(def.label, color = u.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else if (icon != null) {
                Icon(icon, contentDescription = def.label, tint = u.fg, modifier = Modifier.size(18.dp))
                Text(def.label, color = u.sub, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(def.display.ifBlank { def.label }, color = u.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (isCombo(def)) Text(mappingText(def), color = AgColors.Yellow, fontSize = 7.sp, maxLines = 1)
        }
    }
}

private fun isCombo(def: KeyDef) = def.type == "combo" && def.mapping is List<*>

private fun mappingText(def: KeyDef): String {
    val m = def.mapping
    return if (m is List<*>) m.joinToString("+") else m.toString()
}

// ================= 顶栏 / 配对 / 状态卡 =================
@Composable
fun DeviceChip(
    count: Int,
    max: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    Row(
        modifier
            .background(u.panel)
            .border(1.dp, u.border)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Devices,
            contentDescription = "设备",
            tint = if (onClick != null) AgColors.Amber else u.sub,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("设备 $count/$max", color = if (onClick != null) u.fg else u.sub, fontSize = 11.sp)
    }
}

@Composable
private fun DeviceDialog(
    peers: List<PeerInfo>,
    onReconnect: () -> Unit,
    onDelete: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val u = ui()
    val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = u.panel,
        title = { Text("设备管理", color = u.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (peers.isEmpty()) {
                    Text("暂无设备信息", color = u.sub, fontSize = 12.sp)
                } else {
                    peers.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(u.panel2)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (p.online) AgColors.Green else u.sub),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name.ifBlank { p.id }, color = u.fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                val seen = if (p.seenAt > 0) format.format(java.util.Date(p.seenAt * 1000L)) else "—"
                                Text("${p.id} · ${p.ip} · $seen", color = u.sub, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // 删除按钮
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(u.panel)
                                    .border(1.dp, u.border)
                                    .clickable { onDelete(p.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "删除", tint = AgColors.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onDisconnect(); onDismiss() },
                    shape = RectangleShape,
                ) { Text("返回初始界面", color = u.fg, fontSize = 11.sp) }
                OutlinedButton(
                    onClick = onReconnect,
                    shape = RectangleShape,
                ) { Text("重新连接", color = AgColors.Amber, fontSize = 11.sp) }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RectangleShape,
            ) { Text("关闭", color = u.sub, fontSize = 12.sp) }
        },
    )
}

@Composable
fun ConnPill(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    val u = ui()
    val c = when {
        ok -> AgColors.Green
        text.contains("失败") -> AgColors.Red
        else -> AgColors.Amber
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(u.panel2)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(text, color = u.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Deprecated("已替换为 ConnectPage，保留签名用于外部引用")
@Composable
fun PairingCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onScanQr: () -> Unit,
) {
}

// ================= AgentPager AgentGridScreen 接线（net → domain 映射 + 投影 + 控制回调） =================
// 移植自 AgentPager AgentGridViewModel + MainActivity 的驱动逻辑：
//   • net wire 模型 → domain 枚举模型（ModelMapping.toDomain）
//   • TaskDashboardProjector 投影（排序/置顶展开/紧急识别/自动仪表盘切换）
//   • 控制回调 → RemoteClient.taskControl（含 DELETE 双步确认，桌面端已支持）
//   • 屏幕亮度策略：焦点任务激活时按设置调亮，离开 Agent 页恢复系统亮度

private fun ControlAction.toWire(): String = when (this) {
    ControlAction.APPROVE -> "approve"
    ControlAction.DENY -> "deny"
    ControlAction.ANSWER -> "answer"
    ControlAction.INTERRUPT -> "interrupt"
    ControlAction.RETRY -> "retry"
    ControlAction.MUTE -> "mute"
    ControlAction.MARK_READ -> "markread"
    ControlAction.PIN -> "pin"
    ControlAction.DELETE -> "delete"
}

@Composable
private fun AgentPage(
    client: RemoteClient,
    onAgentBrightness: (Float?) -> Unit,
) {
    val tasks by client.tasks.collectAsState()
    val focusedTaskID by client.focusedTaskID.collectAsState()
    val pendingRequests by client.pendingRequests.collectAsState()
    val usage by client.usage.collectAsState()
    val paired by client.paired.collectAsState()

    // 进入 Agent 页即向桌面端请求一次用量刷新
    LaunchedEffect(Unit) { client.getUsage() }

    val snapshot = remember(tasks, focusedTaskID, pendingRequests, usage) {
        StateSnapshotPayload(
            tasks = tasks.map { it.toDomain() },
            usage = usage?.toDomain(),
            focusedTaskID = focusedTaskID,
            pendingRequests = pendingRequests.map { it.toDomain() },
        )
    }

    var projection by remember { mutableStateOf(TaskDashboardProjection.empty()) }
    var activeTaskBrightness by remember {
        mutableStateOf(ScreenBrightnessPolicy.DEFAULT_ACTIVE_BRIGHTNESS)
    }

    // 快照变化 → 重新投影（对齐 AgentPager AgentGridViewModel 的状态收集逻辑）
    LaunchedEffect(snapshot) {
        projection = TaskDashboardProjector.project(
            snapshot = snapshot,
            previous = projection,
            now = System.currentTimeMillis(),
        )
    }

    // 全部任务终态闲置 90 秒 → 自动切回用量仪表盘（对齐 scheduleDashboardTransition）
    LaunchedEffect(projection.nextDashboardAt) {
        val transitionAt = projection.nextDashboardAt ?: return@LaunchedEffect
        if (projection.manualDashboardOverride != null) return@LaunchedEffect
        delay((transitionAt - System.currentTimeMillis()).coerceAtLeast(0L))
        projection = TaskDashboardProjector.project(
            snapshot = snapshot,
            previous = projection,
            now = System.currentTimeMillis(),
        )
    }

    val state = AgentGridUiState(
        linkState = if (paired) LinkState.CONNECTED else LinkState.DISCONNECTED,
        taskProjection = projection,
        usage = snapshot.usage,
        pendingRequests = snapshot.pendingRequests,
        activeTaskBrightness = activeTaskBrightness,
    )

    // AgentPager 屏幕亮度策略：跟随焦点任务生命周期（离开本页时恢复系统亮度）
    LaunchedEffect(state.focusedTask?.lifecycle, state.activeTaskBrightness) {
        onAgentBrightness(
            ScreenBrightnessPolicy.brightnessFor(
                lifecycle = state.focusedTask?.lifecycle,
                activeBrightness = state.activeTaskBrightness,
            ),
        )
    }
    DisposableEffect(Unit) {
        onDispose { onAgentBrightness(null) }
    }

    AgentGridScreen(
        state = state,
        onControl = { id, action, value ->
            client.taskControl(id, action.toWire(), value)
        },
        onFocus = { id ->
            projection = projection.focusing(id)
        },
        onToggleDashboard = {
            projection = projection.togglingDashboard()
        },
        onActiveTaskBrightnessChange = { value ->
            activeTaskBrightness = ScreenBrightnessPolicy.sanitizeActiveBrightness(value)
        },
    )
}

/** Agent 状态卡（AgentPager 风格，用于首页其他页面的紧凑版） */
@Composable
fun StatusCard(status: AgentStatus, modifier: Modifier = Modifier) {
    val u = ui()
    val stateColor = stateColorOf(status.state)
    val stateCn = stateCnOf(status.state)
    val (mark, markColor) = sourceMark(status.source)

    Column(
        modifier
            .background(u.panel)
            .border(1.dp, u.border)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(markColor.copy(alpha = 0.16f))
                    .border(1.dp, markColor.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mark, color = markColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (status.source.isBlank()) "Agent" else status.source,
                        color = u.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(stateColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(stateColor))
                        Spacer(Modifier.width(3.dp))
                        Text(stateCn, color = stateColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(status.state.uppercase(), color = u.sub, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (status.message.isBlank()) "（空闲，等待指令）" else status.message,
            color = u.fg, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("项目", color = u.sub, fontSize = 10.sp)
            Spacer(Modifier.width(5.dp))
            Text(status.project.ifBlank { "—" }, color = u.fg, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ================= 语音栏（点按切换：点一下启动，再点一下结束） =================
@Composable
fun VoiceBar(
    client: RemoteClient,
    phase: String,
    sessionMsg: String,
    onNeedsMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    var pttActive by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }

    Box(
        modifier
            .background(u.panel)
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(if (pttActive) AgColors.Amber.copy(alpha = 0.30f) else u.panel2)
                .border(1.dp, if (pttActive) AgColors.Amber else u.border)
                .clickable {
                    if (!pttActive) {
                        // 点击启动：先请求麦克风权限，再发 hold_start + 开始录音
                        onNeedsMic()
                        pttActive = true
                        client.holdStart()
                        MicHolder.start(client)
                    } else {
                        // 再次点击：停止录音 + 发 hold_end
                        MicHolder.stop()
                        client.holdEnd()
                        pttActive = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (pttActive) "点击结束" else "点击说话",
                color = if (pttActive) Color.White else u.fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(tip, color = AgColors.Amber, fontSize = 10.sp)
        }
    }
}

// 全局持有：麦克风（按住说话→电脑）
object MicHolder {
    @Volatile var recorder: com.viberemote.mobile.audio.MicPcmRecorder? = null
    fun start(client: RemoteClient) {
        stop()
        val r = com.viberemote.mobile.audio.MicPcmRecorder { chunk -> client.feedPcm(chunk) }
        if (r.start()) recorder = r
    }
    fun stop() {
        recorder?.stop()
        recorder = null
    }
}

// 保留接口避免编译错误（已停用本地方向语音）
object MrVoice {
    fun init(ctx: android.content.Context, onCmd: (String, String) -> Unit) {}
    fun recognize(): Boolean? = null
}
