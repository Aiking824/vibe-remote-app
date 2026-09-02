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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viberemote.mobile.R
import com.viberemote.mobile.net.AgentStatus
import com.viberemote.mobile.net.KeyDef
import com.viberemote.mobile.net.PeerInfo
import com.viberemote.mobile.net.RemoteClient
import com.viberemote.mobile.net.TaskSnapshot as NetTaskSnapshot
import com.viberemote.mobile.net.TokenUsage
import com.viberemote.mobile.AgentGridUiState
import com.viberemote.mobile.ScreenBrightnessPolicy
import com.viberemote.mobile.domain.AgentActivity
import com.viberemote.mobile.domain.AgentLifecycle
import com.viberemote.mobile.domain.AgentSource
import com.viberemote.mobile.domain.ControlAction
import com.viberemote.mobile.domain.LinkState
import com.viberemote.mobile.domain.StateSnapshotPayload
import com.viberemote.mobile.domain.TaskDashboardProjection
import com.viberemote.mobile.domain.TaskDashboardProjector
import com.viberemote.mobile.domain.TaskSnapshot
import com.viberemote.mobile.domain.UsageSnapshot
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
    else -> "?" to AgColors.Muted
}

// -- 状态 → 中文/颜色（AgentPager 状态色） --
// 注意：服务端可能传 "running"/"starting"（AgentPager 对齐的小写）或 "RUNNING"/"STARTING"，两种都要命中
private fun stateCnOf(s: String): String = when (s.uppercase()) {
    "READY", "IDLE" -> "空闲"
    "WORKING", "RUNNING", "STARTING" -> "工作中"
    "THINKING" -> "思考中"
    "CONFIRM", "WAITING", "WAITINGAPPROVAL", "WAITINGANSWER",
    "WAITING_APPROVAL", "WAITING_ANSWER" -> "待确认"
    "ERROR", "FAILED" -> "出错"
    "SUCCEEDED", "SUCCESS" -> "已完成"
    "INTERRUPTED" -> "已中断"
    "OFFLINE" -> "离线"
    else -> s
}

private fun stateColorOf(s: String) = when (s.uppercase()) {
    "READY", "IDLE" -> AgColors.Green
    "WORKING", "RUNNING", "STARTING", "THINKING" -> AgColors.Amber
    "CONFIRM", "WAITING", "WAITINGAPPROVAL", "WAITINGANSWER",
    "WAITING_APPROVAL", "WAITING_ANSWER" -> AgColors.Blue
    "ERROR", "FAILED", "INTERRUPTED" -> AgColors.Red
    "SUCCEEDED", "SUCCESS" -> AgColors.Green
    "OFFLINE" -> AgColors.Muted
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
    val tasks by client.tasks.collectAsState()
    val u = ui()
    var page by remember { mutableStateOf(Page.PAD) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val agentLandscape = page == Page.AGENT && landscape
    val hotLandscape = page == Page.HOT && landscape

    // 首界面 Agent 状态卡的"选中项目"（跨 PAD/MOUSE/HOT 共享同一个选择）
    //   null = 自动（展示全量 tasks 里最活跃的那条任务）
    //   非空 = 仅在该项目下选最活跃任务；未命中该项目任务，显示"该项目暂无运行任务"兜底
    var selectedProject by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = u.bg,
        bottomBar = {
            // 快捷键页面横屏时隐藏底栏，竖屏常驻
            if (paired && !agentLandscape && !hotLandscape) {
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
                    .padding(horizontal = if (agentLandscape || hotLandscape) 0.dp else 12.dp, vertical = if (agentLandscape || hotLandscape) 0.dp else 8.dp),
            ) {
                // 快捷键页面横屏时隐藏顶栏，竖屏常驻
                if (!agentLandscape && !(paired && hotLandscape)) {
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
                }

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
                            tasks = tasks,
                            selectedProject = selectedProject,
                            onSelectProject = { selectedProject = it },
                            phase = phase,
                            sessionMsg = sessionMsg,
                            onNeedsMic = onNeedsMic,
                        )
                        Page.MOUSE -> MousePage(
                            client,
                            status = c,
                            tasks = tasks,
                            selectedProject = selectedProject,
                            onSelectProject = { selectedProject = it },
                        )
                        Page.HOT -> HotPage(
                            client,
                            keys = keymap,
                            status = c,
                            tasks = tasks,
                            selectedProject = selectedProject,
                            onSelectProject = { selectedProject = it },
                        )
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
    tasks: List<NetTaskSnapshot>,
    selectedProject: String?,
    onSelectProject: (String?) -> Unit,
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
                StatusCard(status, tasks, selectedProject, onSelectProject, Modifier.fillMaxWidth())
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
            StatusCard(status, tasks, selectedProject, onSelectProject, Modifier.fillMaxWidth())
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
private fun MousePage(
    client: RemoteClient,
    status: AgentStatus,
    tasks: List<NetTaskSnapshot>,
    selectedProject: String?,
    onSelectProject: (String?) -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard(status, tasks, selectedProject, onSelectProject, Modifier.fillMaxWidth())
                TouchArea(client, Modifier.weight(1f).fillMaxWidth())
            }
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
            StatusCard(status, tasks, selectedProject, onSelectProject, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
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
private fun HotPage(
    client: RemoteClient,
    keys: List<KeyDef>,
    status: AgentStatus,
    tasks: List<NetTaskSnapshot>,
    selectedProject: String?,
    onSelectProject: (String?) -> Unit,
) {
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
        if (landscape) {
            // 横屏：6 个按键平铺两行三列，铺满整屏且按键竖直居中
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                clip.forEach { def ->
                    ClipButton(
                        def, { client.keyTap(def.id) }, Modifier.weight(1f).fillMaxHeight(),
                        iconSize = 20.dp, labelSize = 11.sp, mappingSize = 8.sp, vPadding = 6.dp,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                hot.forEach { def ->
                    ClipButton(
                        def, { client.keyTap(def.id) }, Modifier.weight(1f).fillMaxHeight(),
                        iconSize = 20.dp, labelSize = 11.sp, mappingSize = 8.sp, vPadding = 6.dp,
                    )
                }
            }
        } else {
            // 竖屏：完整布局
            StatusCard(status, tasks, selectedProject, onSelectProject, Modifier.fillMaxWidth())
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
}

@Composable
private fun ClipButton(
    def: KeyDef,
    onTap: () -> Unit,
    modifier: Modifier,
    iconSize: Dp = 26.dp,
    labelSize: TextUnit = 13.sp,
    mappingSize: TextUnit = 9.sp,
    vPadding: Dp = 14.dp,
) {
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
            .padding(vertical = vPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(keyIcon(def) ?: Icons.Filled.ContentPaste, contentDescription = def.label, tint = AgColors.Amber, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height(3.dp))
            Text(def.label, color = u.fg, fontSize = labelSize, fontWeight = FontWeight.Bold)
            Text(mappingText(def), color = u.sub, fontSize = mappingSize)
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
    val orientation = LocalConfiguration.current.orientation
    val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE

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

    // 横屏：全量 AgentGridScreen（任务列表 / 详情 / 用量切换 / 设置 全保留）
    // 竖屏：只保留「近期用量仪表盘 + 活跃项目摘要卡」，不做任务详情/设置
    if (landscape) {
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
    } else {
        AgentPortraitOverview(
            tasks = snapshot.tasks,
            usage = snapshot.usage,
            connected = state.linkState == LinkState.CONNECTED,
        )
    }
}

/**
 * 竖屏 Agent 主界面（仅两部分，全部可在一屏内看完无需滚动）：
 *   1. 近期用量仪表盘（UsageDashboard，复用 AgentPager 移植版）
 *   2. 现在活跃的项目（按项目名分组，每个项目取最活跃任务 → 摘要卡）
 *
 *   不显示设置页 / 任务详情 / 删除按钮 —— 那些操作统一走横屏或其它 Tab。
 */
@Composable
private fun AgentPortraitOverview(
    tasks: List<TaskSnapshot>,
    usage: UsageSnapshot?,
    connected: Boolean,
) {
    val u = ui()

    // 活跃 = 非终态（STARTING / RUNNING / WAITING_*；isTerminal 为 TaskSnapshot 自定义属性）
    val activeTasks = remember(tasks) { tasks.filter { !it.isTerminal } }

    // 按项目名分组，每组按 attentionPriority / updatedAt 取代表
    val activeProjectRows = remember(activeTasks) {
        activeTasks
            .groupBy { it.projectName.ifBlank { "(未命名项目)" } }
            .mapValues { (_, list) ->
                list.maxWithOrNull(
                    compareBy<TaskSnapshot> { it.attentionPriority }
                        .thenByDescending { it.updatedAt }
                )!!
            }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, TaskSnapshot>> {
                    when (it.value.lifecycle) {
                        AgentLifecycle.WAITING_APPROVAL,
                        AgentLifecycle.WAITING_ANSWER,
                        -> 4
                        AgentLifecycle.RUNNING -> 3
                        AgentLifecycle.STARTING -> 2
                        AgentLifecycle.OFFLINE -> 1
                        else -> 0
                    }
                }
                    .thenByDescending { it.value.updatedAt }
            )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(u.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // —— 标题行 ——
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Agent · 纵览",
                color = u.fg, fontFamily = PixelFont,
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            val dot = if (connected) AgColors.Green else AgColors.Amber
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (connected) "已连接电脑" else "未连接（显示缓存）",
                    color = dot, fontSize = 10.sp,
                )
            }
        }

        // —— 1. 近期用量（只保留右半柱状图：左部额度面板已被精简） ——
        Box(
            Modifier
                .fillMaxWidth()
                .background(AgentGridColors.Background)
                .border(1.dp, u.border)
        ) {
            UsageBarsOnlyPanel(usage = usage)
        }

        // —— 2. 现在活跃的项目 ——
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "活跃项目",
                color = u.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "共 ${activeProjectRows.size} 个项目 · ${activeTasks.size} 个任务",
                color = u.sub, fontSize = 10.sp,
            )
        }

        if (activeProjectRows.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(u.panel)
                    .border(1.dp, u.border)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("（当前没有活跃项目）", color = u.sub, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hook 未启动 / 所有任务已完成，横屏可查看历史",
                    color = u.sub, fontSize = 10.sp,
                )
            }
        } else {
            activeProjectRows.forEach { (project, leadTask) ->
                ActiveProjectCard(
                    project = project,
                    lead = leadTask,
                    sameProjectTaskCount = activeTasks.count {
                        (it.projectName.ifBlank { "(未命名项目)" }) == project
                    },
                )
            }
        }
    }
}

/** 单条「活跃项目」摘要卡（竖屏 Agent Overview 专用）
 *
 *  本次完善：明确显示运行状态
 *    • 顶部彩色状态条（lifecycle → 颜色）— 整个卡片顶部 2dp 指示条
 *    • lifecycle 中文 + 秒级运行耗时（"运行中 · 1分 23秒"）
 *    • 当前 activity 中文动作标签（"正在编辑"、"正在执行命令"…）
 *    • 底部「进度条」：按 attentionPriority 归一化的 0~1 视觉进度（活跃程度指示）
 *    • 最终状态条颜色：WAITING_APPROVAL 红闪底 / RUNNING 绿流 / STARTING 蓝 / WAITING_ANSWER 紫 / SUCCEEDED 琥珀底
 * */
@Composable
private fun ActiveProjectCard(
    project: String,
    lead: TaskSnapshot,
    sameProjectTaskCount: Int,
) {
    val u = ui()
    val stateColor = stateColorOf(lead.lifecycle.wireName())
    val stateCn = stateCnOf(lead.lifecycle.wireName())
    val (mark, markColor) = sourceMark(
        when (lead.source) {
            AgentSource.CLAUDE_CODE -> "claude"
            AgentSource.CODEX_DESKTOP, AgentSource.CODEX_CLI -> "codex"
        }
    )
    val waiting = lead.lifecycle == AgentLifecycle.WAITING_APPROVAL ||
        lead.lifecycle == AgentLifecycle.WAITING_ANSWER
    val urgent = waiting || lead.attentionPriority >= 250

    // 运行耗时（秒级实时刷新 —— 每 1s 重新计算 elapsedAt）
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            tick = System.currentTimeMillis()
        }
    }
    val elapsed = remember(tick, lead) {
        val ms = if (lead.isTerminal && lead.completedAt != null) {
            lead.completedAt - lead.startedAt
        } else {
            tick - lead.startedAt
        }.coerceAtLeast(0L)
        formatElapsedMs(ms)
    }

    // 当前 Activity 文字（非运行态为空）
    val activityText = activityCnOf(lead.activity)

    // 进度条归一化（attentionPriority 0..600+ → 0..1，阈值 250 封顶，避免 WAITING 顶满时 RUNNING 也不显
    val progress = remember(lead.attentionPriority) {
        (lead.attentionPriority.coerceAtMost(600) / 600f).coerceIn(0f, 1f)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(
                if (urgent) AgColors.Amber.copy(alpha = 0.05f) else u.panel,
            )
            .border(1.dp, if (urgent) AgColors.Amber.copy(alpha = 0.6f) else u.border),
    ) {
        // —— 顶部 2dp 彩色状态条（用户"项目运行状态条"要求） ——
        Box(Modifier.fillMaxWidth().height(2.dp).background(stateColor))

        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(markColor.copy(alpha = 0.16f))
                        .border(1.dp, markColor.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mark, color = markColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    // 第一行：Harness名 / +N徽章 / 状态胶囊
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = harnessDisplayName(
                            when (lead.source) {
                                AgentSource.CLAUDE_CODE -> "claude"
                                AgentSource.CODEX_DESKTOP, AgentSource.CODEX_CLI -> "codex"
                            }
                        ),
                            color = u.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (sameProjectTaskCount > 1) {
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.background(u.panel2).padding(horizontal = 3.dp, vertical = 0.dp)) {
                                Text(
                                    "+${sameProjectTaskCount - 1}",
                                    color = u.sub, fontSize = 9.sp,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(stateColor.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Box(Modifier.size(4.dp).clip(CircleShape).background(stateColor))
                            Spacer(Modifier.width(3.dp))
                            Text(stateCn, color = stateColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    // 第二行：项目名（琥珀粗体高亮）
                    Text(
                        project,
                        color = AgColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // —— 「运行状态行」：中文状态 + 累计耗时 + （可选）activity 动作 ——
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(stateColor.copy(alpha = 0.10f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text("$stateCn · $elapsed", color = stateColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
                if (activityText.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(activityText, color = u.fg, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                lead.title.ifBlank { lead.latestStep ?: "(无标题)" },
                color = u.fg, fontSize = 11.sp, lineHeight = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            // 底部进度条（活跃程度指示）+ 摘要行
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(u.border, RectangleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(1.5.dp)
                        .background(stateColor, RectangleShape),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lead.subagents.isNotEmpty()) {
                    Box(Modifier.background(u.panel2).padding(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(
                            "子代理 ×${lead.subagents.size}",
                            color = AgColors.Violet, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                val usage = lead.tokenUsage
                if (usage != null) {
                    Text("Tokens", color = u.sub, fontSize = 9.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        formatTokensCompact(usage.total),
                        color = AgColors.Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                if (waiting) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.background(AgColors.Red.copy(alpha = 0.12f)).padding(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(
                            "需处理 → 横屏",
                            color = AgColors.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** AgentLifecycle domain 枚举 → wire 字符串（复用 RemoteScreen 的 stateColorOf/stateCnOf） */
private fun AgentLifecycle.wireName(): String = name.lowercase()

/** 运行耗时格式化：34s / 1m23s / 2h05m / 1d3h */
private fun formatElapsedMs(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec < 60) return "${totalSec}s"
    val m = totalSec / 60
    val s = totalSec % 60
    if (m < 60) return String.format("%dm%02ds", m, s)
    val h = m / 60
    val mm = m % 60
    if (h < 24) return String.format("%dh%02dm", h, mm)
    val d = h / 24
    val hh = h % 24
    return String.format("%dd%dh", d, hh)
}

/** activity → 中文文字（运行中任务的"正在做什么"标签） */
private fun activityCnOf(activity: AgentActivity?): String = when (activity) {
    null -> ""
    AgentActivity.THINKING -> "正在思考…"
    AgentActivity.READING -> "正在阅读代码/文档"
    AgentActivity.SEARCHING -> "正在搜索"
    AgentActivity.EDITING -> "正在编辑文件"
    AgentActivity.EXECUTING -> "正在执行命令"
    AgentActivity.TESTING -> "正在运行测试"
    AgentActivity.BROWSING -> "正在浏览网页"
    AgentActivity.DELEGATING -> "正在调用子代理"
}

/** Agent 状态卡（布局 3.0）
 *
 *  ┌─────────────────────────────────────────────────────────┐
 *  │ [C] Harness名 ●状态    右上：【缓存命中率 ▶ 87.3%】   │ ← Row 1
 *  │     【已选】项目：vibe-remote（琥珀高亮）               │ ← Row 2
 *  ├─────────────────────────────────────────────────────────┤
 *  │  正在编辑文件：RemoteScreen.kt 增加命中率显示…         │ ← 中间摘要
 *  ├─────────────────────────────────────────────────────────┤
 *  │  左下：【Tokens 12.3k】（点击 → 明细对话框）           │
 *  │                            右下：【切换项目 ›】（点击→Picker）│
 *  └─────────────────────────────────────────────────────────┘
 *
 *  行为拆分（不再整卡 clickable）：
 *    • 右上角缓存命中率胶囊 → 仅显示；
 *    • 左下角 Tokens 数字（整条 clickable） → 弹 TokenDetailDialog，明细含命中率；
 *    • 右下角「切换项目 ›」（整条 clickable） → 弹 ProjectPickerDialog。
 * */
@Composable
fun StatusCard(
    status: AgentStatus,
    tasks: List<NetTaskSnapshot>,
    selectedProject: String?,
    onSelectProject: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = ui()

    // 项目内的"最活跃任务"选择策略：attentionScore + updatedAt 倒序（同一个项目可能有多个 session）
    fun pickTop(list: List<NetTaskSnapshot>): NetTaskSnapshot? = list.maxWithOrNull(
        compareBy<NetTaskSnapshot> { it.attentionScore }
            .thenByDescending { it.updatedAt }
    )

    val allProjects = remember(tasks) {
        tasks.map { it.projectName.ifBlank { status.project.ifBlank { "(未命名项目)" } } }
            .distinct().sorted()
    }
    val working: NetTaskSnapshot? = remember(tasks, selectedProject) {
        when (val p = selectedProject) {
            null -> pickTop(tasks)
            else -> pickTop(tasks.filter {
                (it.projectName.ifBlank { status.project.ifBlank { "(未命名项目)" } }) == p
            })
        }
    }

    val hasSelection = selectedProject != null
    val displaySource = working?.source?.takeIf { it.isNotBlank() } ?: status.source
    val displayProject = selectedProject ?: working?.projectName?.takeIf { it.isNotBlank() } ?: status.project
    val displayLifecycle = working?.lifecycle ?: status.state
    val displayTitle = working?.title?.takeIf { it.isNotBlank() } ?: working?.userPrompt
        ?: working?.latestStep?.takeIf { it.isNotBlank() } ?: status.message

    val stateColor = stateColorOf(displayLifecycle)
    val stateCn = stateCnOf(displayLifecycle)
    val (mark, markColor) = sourceMark(displaySource)
    val hitRate = working?.tokenUsage?.let { cacheHitRateOf(it) }

    var showPicker by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }

    Column(
        modifier
            .background(
                if (hasSelection) AgColors.Amber.copy(alpha = 0.06f) else u.panel,
            )
            .border(1.dp, if (hasSelection) AgColors.Amber else u.border)
            .padding(10.dp),
    ) {
        // ============== 顶行：Harness / 状态胶囊 / 右上 缓存命中率 ==============
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
                        when {
                            displaySource.isBlank() -> "Agent"
                            else -> harnessDisplayName(displaySource)
                        },
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
                // 选中项目：项目名高亮为琥珀色 + "已选择"徽章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSelection) {
                        Box(
                            Modifier
                                .background(AgColors.Amber.copy(alpha = 0.22f))
                                .padding(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("已选", color = AgColors.Amber, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = "项目：${displayProject.ifBlank { "—" }}",
                        color = if (hasSelection) AgColors.Amber else u.sub,
                        fontSize = if (hasSelection) 11.sp else 10.sp,
                        fontWeight = if (hasSelection) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // ============== 右上角：缓存命中率 ==============
            Spacer(Modifier.width(8.dp))
            if (hitRate == null) {
                Box(
                    Modifier
                        .background(u.panel2)
                        .border(1.dp, u.border)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("缓存命中率 · N/A", color = u.sub, fontSize = 9.sp)
                }
            } else {
                val hrColor = when {
                    hitRate >= 0.7f -> AgColors.Green
                    hitRate >= 0.4f -> AgColors.Amber
                    else -> AgColors.Orange
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(hrColor.copy(alpha = 0.10f))
                        .border(1.dp, hrColor.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(hrColor))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "缓存 ${hitRatePercent(hitRate)}",
                        color = hrColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // ============== 中间：任务标题/状态 ==============
        Text(
            when {
                hasSelection && working == null -> "（该项目暂无运行任务）"
                else -> displayTitle.takeIf { it.isNotBlank() } ?: "（空闲，等待指令）"
            },
            color = if (hasSelection && working == null) u.sub else u.fg,
            fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))
        // ============== 底行：左下 Tokens 点击看明细 ｜ 右下 切换项目 点击弹 Picker ==============
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tu = working?.tokenUsage
            if (tu == null) {
                // 无 Token 数据：左下角灰字占位
                Text(
                    text = "Tokens · —",
                    color = u.sub, fontSize = 10.sp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showTokenDialog = true }
                        .background(AgColors.Amber.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text("Tokens", color = u.sub, fontSize = 10.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatTokensCompact(tu.total),
                        color = AgColors.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("↗", color = AgColors.Cyan, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            // 右下角「切换项目 ›」（替代原"整卡点击打开选择器"）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { showPicker = true }
                    .background(AgColors.Cyan.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (selectedProject == null) "切换项目" else "切换项目",
                    color = AgColors.Cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(2.dp))
                Text("›", color = AgColors.Cyan, fontSize = 12.sp)
            }
        }
    }

    if (showPicker) {
        ProjectPickerDialog(
            allProjects = allProjects,
            selectedProject = selectedProject,
            hasTasks = tasks.isNotEmpty(),
            onPick = { p ->
                onSelectProject(p)
                showPicker = false
                // 选中具体项目后不再自动开明细（让用户再点"左下 Tokens"才开，贴合新按钮语义）
            },
            onViewTokens = { showPicker = false; showTokenDialog = true },
            onClear = { onSelectProject(null) },
            onDismiss = { showPicker = false },
        )
    }

    if (showTokenDialog) {
        TokenDetailDialog(
            task = working,
            fallbackProject = displayProject,
            fallbackStatus = status,
            onDismiss = { showTokenDialog = false },
        )
    }
}

/** TokenUsage → 缓存命中率（cachedInput / (input + cachedInput)，null 表示无可计算分母） */
private fun cacheHitRateOf(usage: TokenUsage): Float? {
    val denom = usage.input.toLong() + usage.cachedInput.toLong()
    return if (denom <= 0L) null else usage.cachedInput.toFloat() / denom.toFloat()
}

/** 命中率 % 字符串，保留 1 位小数（66% / 87.3%） */
private fun hitRatePercent(rate: Float): String {
    val percent = (rate * 1000).toInt()
    val intPart = percent / 10
    val fracPart = percent % 10
    return if (fracPart == 0) "${intPart}%" else "${intPart}.${fracPart}%"
}

/** 项目选择器对话框（分组列表 + 当前选中高亮 + 明细入口 + 清空） */
@Composable
private fun ProjectPickerDialog(
    allProjects: List<String>,
    selectedProject: String?,
    hasTasks: Boolean,
    onPick: (String?) -> Unit,
    onViewTokens: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val u = ui()
    AlertDialog(
        containerColor = u.panel,
        titleContentColor = u.fg,
        textContentColor = u.fg,
        onDismissRequest = onDismiss,
        title = {
            Text("选择要查看的项目", fontFamily = PixelFont, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // 自动项
                Text(
                    "（自动模式 = 全量 tasks 中最活跃的任务）",
                    color = u.sub, fontSize = 10.sp,
                )
                Spacer(Modifier.height(6.dp))
                ProjectPickerRow(
                    name = "自动（最活跃）",
                    hint = "关注热度最高的任务",
                    selected = selectedProject == null,
                    onClick = { onPick(null) },
                )
                Spacer(Modifier.height(8.dp))
                // 项目列表
                if (allProjects.isEmpty() || !hasTasks) {
                    Text(
                        "（当前还没有可选择的项目，Hook 未启动或暂无任务）",
                        color = u.sub, fontSize = 11.sp,
                    )
                } else {
                    Text(
                        "我的项目（共 ${allProjects.size} 个）",
                        color = u.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 220.dp)) {
                        allProjects.forEach { name ->
                            ProjectPickerRow(
                                name = name,
                                hint = "点击查看该项目的当前状态",
                                selected = name == selectedProject,
                                onClick = { onPick(name) },
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (selectedProject != null) {
                    TextButton(onClick = {
                        onClear()
                    }) {
                        Text("清空选择", color = u.sub, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onViewTokens) {
                    Text("查看 Tokens 明细", color = AgColors.Cyan, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("完成", color = AgColors.Amber, fontSize = 12.sp)
                }
            }
        },
    )
}

@Composable
private fun ProjectPickerRow(
    name: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val u = ui()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) AgColors.Amber.copy(alpha = 0.16f) else u.panel2,
            )
            .border(1.dp, if (selected) AgColors.Amber else u.border)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(1.2.dp, if (selected) AgColors.Amber else u.sub, CircleShape)
                .then(
                    if (selected) Modifier.background(AgColors.Amber.copy(alpha = 0.4f), CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AgColors.Amber, CircleShape))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = if (selected) AgColors.Amber else u.fg,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(hint, color = u.sub, fontSize = 9.sp)
        }
    }
}

/** Harness 名称映射（首字母徽标 → 用户可读名） */
private fun harnessDisplayName(source: String): String = when (source.lowercase()) {
    "claude", "claudecode", "claude_code", "cc" -> "Claude Code"
    "codex", "openai_codex", "cli" -> "Codex CLI"
    "solo", "soloagent" -> "Solo Agent"
    else -> source.ifBlank { "Agent" }
}

/** 数字紧凑化：1234 → 1.2k / 1,234,567 → 1.2M */
private fun formatTokensCompact(value: Int): String = when {
    value <= 0 -> "0"
    value < 1000 -> value.toString()
    value < 1_000_000 -> String.format("%.1fk", value / 1000.0)
    else -> String.format("%.1fM", value / 1_000_000.0)
}
private fun formatTokensCompact(value: Long): String = when {
    value <= 0 -> "0"
    value < 1000 -> value.toString()
    value < 1_000_000 -> String.format("%.1fk", value / 1000.0)
    else -> String.format("%.1fM", value / 1_000_000.0)
}

/** Token 用量明细对话框：当前任务 input / cached / output / reasoning / total + 子代理汇总 */
@Composable
private fun TokenDetailDialog(
    task: NetTaskSnapshot?,
    fallbackProject: String?,
    fallbackStatus: AgentStatus,
    onDismiss: () -> Unit,
) {
    val u = ui()

    @Composable
    fun UsageRow(label: String, value: String, accent: Color = u.fg) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = u.sub, fontSize = 11.sp, modifier = Modifier.width(110.dp))
            Spacer(Modifier.weight(1f))
            Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    AlertDialog(
        containerColor = u.panel,
        titleContentColor = u.fg,
        textContentColor = u.fg,
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Token 用量明细", fontFamily = PixelFont, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val t = task
            Column(Modifier.fillMaxWidth()) {
                if (t == null) {
                    if (!fallbackProject.isNullOrBlank()) {
                        Text(
                            "项目：$fallbackProject",
                            color = AgColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = buildString {
                            if (t == null && !fallbackProject.isNullOrBlank()) append("（该项目当前无任务运行，暂无 Token 数据）")
                            else if (fallbackStatus.message.isBlank()) append("（当前无工作中的 Agent 任务）")
                            else append(fallbackStatus.message)
                        },
                        color = u.sub, fontSize = 12.sp,
                    )
                } else {
                    // Task 头
                    Text(
                        "任务：${t.title.ifBlank { t.userPrompt ?: "(无标题)" }}",
                        color = u.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "项目：${t.projectName.ifBlank { "—" }}",
                        color = u.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 用量条
                    val usage = t.tokenUsage
                    if (usage == null) {
                        Text("（尚未上报 token 用量）", color = u.sub, fontSize = 11.sp)
                    } else {
                        UsageRow("输入 (Input)", formatTokensCompact(usage.input), AgColors.Blue)
                        Spacer(Modifier.height(4.dp))
                        UsageRow("缓存 (Cached)", formatTokensCompact(usage.cachedInput), AgColors.Violet)
                        Spacer(Modifier.height(4.dp))
                        UsageRow("输出 (Output)", formatTokensCompact(usage.output), AgColors.Cyan)
                        Spacer(Modifier.height(4.dp))
                        UsageRow("推理 (Reasoning)", formatTokensCompact(usage.reasoningOutput), AgColors.Amber)
                        Spacer(Modifier.height(4.dp))
                        cacheHitRateOf(usage).also { hr ->
                            if (hr == null) {
                                UsageRow(
                                    "缓存命中率",
                                    "（无缓存/命中数据）",
                                    u.sub,
                                )
                            } else {
                                val hrColor = when {
                                    hr >= 0.7f -> AgColors.Green
                                    hr >= 0.4f -> AgColors.Amber
                                    else -> AgColors.Orange
                                }
                                UsageRow("缓存命中率", hitRatePercent(hr), hrColor)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(u.border))
                        Spacer(Modifier.height(6.dp))
                        UsageRow("总计 (Total)", formatTokensCompact(usage.total), AgColors.Green)
                    }
                    // 子代理
                    if (t.subagents.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(u.border))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "子代理（共 ${t.subagents.size} 个）",
                            color = u.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        t.subagents.forEach { s ->
                            Spacer(Modifier.height(4.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(u.panel2)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    s.displayName.ifBlank { s.path },
                                    color = u.fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                val st = s.lifecycle
                                UsageRow(
                                    "状态 · ${stateCnOf(st)}",
                                    formatTokensCompact(s.tokenUsage?.total ?: 0),
                                    stateColorOf(st),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = AgColors.Amber, fontSize = 12.sp)
            }
        },
    )
}

// ================= 语音栏（麦克风图标，点击触发 keymap "voice" 条目） =================
@Composable
fun VoiceBar(
    client: RemoteClient,
    phase: String,
    sessionMsg: String,
    onNeedsMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    Box(
        modifier
            .background(u.panel)
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(u.panel2)
                .border(1.dp, u.border)
                .clickable {
                    client.keyTap("voice")
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "语音",
                tint = u.fg,
                modifier = Modifier.size(28.dp),
            )
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
