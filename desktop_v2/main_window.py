"""Vibe Remote desktop_v2 主窗口。

布局对齐 AgentPager Windows WPF MainWindow（vibe_conding/AgentPager-main/windows/src/AgentPager.Bridge/MainWindow.xaml）：
  顶部品牌区 + 服务状态徽章
  第一层主标签：连接配置 / CODEX / CLAUDE
  第二层次级标签：连接状态 / KEYMAP
  底部状态栏：诊断信息 + 打开日志目录按钮

5 个页面：
  - PairingPage：QR + 局域网地址 + 配对文本 + 启动开机
  - CodexHookPage：Codex Hook 状态 + 安装/卸载
  - ClaudeHookPage：Claude Hook 状态 + 安装/卸载
  - StatusPage：手机连接数 + 服务状态 + 最近 Hook 事件
  - KeymapPage：Keymap 表格 + 推送
"""
from __future__ import annotations

from pathlib import Path
from typing import Optional

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QAction, QPixmap
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QPushButton,
    QSizePolicy,
    QSplitter,
    QTabWidget,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from theme import C


HERE = Path(__file__).resolve().parent


class _SectionCard(QWidget):
    """AgentPager 风格的内容卡片：深色背景 + 1px 边框 + 内边距。"""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setStyleSheet(
            f"background:{C.BG}; border:1px solid {C.BORDER};"
        )
        self._outer = QVBoxLayout(self)
        self._outer.setContentsMargins(18, 18, 18, 18)
        self._outer.setSpacing(12)

    def addWidget(self, w: QWidget) -> None:  # noqa: N802 - Qt API
        self._outer.addWidget(w)

    def addLayout(self, l) -> None:  # noqa: N802 - Qt API
        self._outer.addLayout(l)

    def addStretch(self) -> None:  # noqa: N802 - Qt API
        self._outer.addStretch()


class PairingPage(QWidget):
    """连接配置页：3 种连接方式按钮 + QR + 局域网地址 + 配对文本 + 启动开机。

    连接方式（对齐手机端 Android 三选项）：
      1) 连接到同一网络（WiFi）
      2) USB 直连（adb reverse，手机访问 127.0.0.1:8765）
      3) 手机对电脑 USB 共享网络（RNDIS）
    """

    refresh_requested = Signal()
    network_changed = Signal(str)
    copy_pairing_requested = Signal()
    startup_toggled = Signal(bool)
    mode_changed = Signal(int)  # 1=WiFi, 2=USB 直连, 3=USB 共享

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        root = QVBoxLayout(self)
        root.setContentsMargins(24, 24, 24, 24)
        root.setSpacing(12)

        # ===== 连接方式三选项 =====
        mode_row = QHBoxLayout()
        mode_row.setSpacing(8)
        self.btn_wifi = QPushButton("1. 连接到同一网络")
        self.btn_usb_direct = QPushButton("2. USB 直连")
        self.btn_usb_tether = QPushButton("3. 手机对电脑 USB 共享")
        for b in (self.btn_wifi, self.btn_usb_direct, self.btn_usb_tether):
            b.setCheckable(True)
            b.setMinimumHeight(36)
            mode_row.addWidget(b)
        mode_row.addStretch()
        root.addLayout(mode_row)

        self.btn_wifi.setChecked(True)
        self.btn_wifi.clicked.connect(lambda: self._select_mode(1))
        self.btn_usb_direct.clicked.connect(lambda: self._select_mode(2))
        self.btn_usb_tether.clicked.connect(lambda: self._select_mode(3))

        # ===== 下方：QR + 信息 =====
        lower = QHBoxLayout()
        lower.setSpacing(24)

        # 左：QR
        qr_box = QLabel()
        qr_box.setFixedSize(200, 200)
        qr_box.setStyleSheet(f"background:white; border:1px solid {C.BORDER};")
        qr_box.setAlignment(Qt.AlignCenter)
        qr_box.setText("QR")
        self.qr_label = qr_box
        lower.addWidget(qr_box, alignment=Qt.AlignTop)

        # 右：信息
        info = QVBoxLayout()
        info.setSpacing(8)

        title = QLabel("扫描配对")
        title.setObjectName("section_title")
        info.addWidget(title)

        self.hint = QLabel("用 Vibe Remote Android 扫描左侧二维码。首次启动时，请在 Windows 防火墙提示中只允许\"专用网络\"访问。")
        self.hint.setObjectName("muted")
        self.hint.setWordWrap(True)
        info.addWidget(self.hint)

        info.addSpacing(4)

        info.addWidget(QLabel("局域网地址"))
        net_row = QHBoxLayout()
        self.net_combo = QComboBox()
        self.net_combo.setMinimumWidth(285)
        self.net_combo.currentTextChanged.connect(self._on_net_changed)
        net_row.addWidget(self.net_combo, 1)
        self.refresh_btn = QPushButton("刷新")
        self.refresh_btn.clicked.connect(self.refresh_requested.emit)
        net_row.addWidget(self.refresh_btn)
        info.addLayout(net_row)

        info.addSpacing(4)
        info.addWidget(QLabel("配对文本"))
        self.pair_text = QTextEdit()
        self.pair_text.setReadOnly(True)
        self.pair_text.setFixedHeight(60)
        info.addWidget(self.pair_text)

        self.copy_btn = QPushButton("复制配对文本")
        self.copy_btn.clicked.connect(self.copy_pairing_requested.emit)
        info.addWidget(self.copy_btn)

        self.startup_chk = QCheckBox("登录 Windows 时启动")
        self.startup_chk.toggled.connect(self.startup_toggled.emit)
        info.addWidget(self.startup_chk)

        info.addStretch()
        lower.addLayout(info, 1)
        root.addLayout(lower, 1)

    def _select_mode(self, mode: int) -> None:
        """切换连接方式按钮状态。"""
        for b, m in ((self.btn_wifi, 1), (self.btn_usb_direct, 2), (self.btn_usb_tether, 3)):
            b.setChecked(m == mode)
        # 提示文本切换
        if mode == 1:
            self.hint.setText("用 Vibe Remote Android 扫描左侧二维码。确保手机和电脑连接同一 WiFi。")
        elif mode == 2:
            self.hint.setText("USB 直连：手机用 USB 线连电脑，开启 USB 调试。点击\"刷新\"触发 adb reverse，手机访问 http://127.0.0.1:8765/")
        else:
            self.hint.setText("USB 共享：手机开启\"USB 网络共享\"后，电脑会出现 RNDIS 适配器，选择对应 IP 即可。")
        self.mode_changed.emit(mode)

    def _on_net_changed(self, text: str) -> None:
        self.network_changed.emit(text)

    def set_networks(self, items: list[str], selected: str = "") -> None:
        self.net_combo.blockSignals(True)
        self.net_combo.clear()
        self.net_combo.addItems(items)
        if selected and selected in items:
            self.net_combo.setCurrentIndex(items.index(selected))
        self.net_combo.blockSignals(False)

    def set_qr_pixmap(self, pm: QPixmap) -> None:
        self.qr_label.setPixmap(pm.scaled(190, 190, Qt.KeepAspectRatio, Qt.SmoothTransformation))

    def set_pairing_text(self, text: str) -> None:
        self.pair_text.setPlainText(text)

    def set_startup_checked(self, on: bool) -> None:
        self.startup_chk.blockSignals(True)
        self.startup_chk.setChecked(on)
        self.startup_chk.blockSignals(False)


class _HookPage(QWidget):
    """Hook 配置页通用骨架（Codex / Claude 共用）。"""

    install_requested = Signal()
    uninstall_requested = Signal()

    def __init__(self, accent: str, title: str, hint: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        accent_color = {"cyan": C.CYAN, "violet": C.VIOLET}[accent]
        root = QVBoxLayout(self)
        root.setContentsMargins(28, 28, 28, 28)
        root.setSpacing(14)

        t = QLabel(title)
        if accent == "violet":
            t.setObjectName("violet_title")
        else:
            t.setObjectName("section_title")
        root.addWidget(t)

        card = _SectionCard(self)
        # 配置状态行
        status_row = QHBoxLayout()
        status_row.addWidget(QLabel("配置状态"))
        self.status_label = QLabel("—")
        self.status_label.setStyleSheet(f"color:{accent_color}; font-weight:bold;")
        self.status_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        status_row.addStretch()
        status_row.addWidget(self.status_label)
        card.addLayout(status_row)

        # 最近事件行
        last_row = QHBoxLayout()
        last_row.addWidget(QLabel("最近事件"))
        self.last_label = QLabel("—")
        self.last_label.setStyleSheet(f"color:{C.MUTED};")
        self.last_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        last_row.addStretch()
        last_row.addWidget(self.last_label)
        card.addLayout(last_row)

        # 提示
        h = QLabel(hint)
        h.setObjectName("muted")
        h.setWordWrap(True)
        card.addWidget(h)

        # 按钮
        btn_row = QHBoxLayout()
        self.install_btn = QPushButton("安装或修复")
        self.install_btn.clicked.connect(self.install_requested.emit)
        self.uninstall_btn = QPushButton("卸载")
        self.uninstall_btn.setObjectName("danger")
        self.uninstall_btn.clicked.connect(self.uninstall_requested.emit)
        btn_row.addWidget(self.install_btn)
        btn_row.addWidget(self.uninstall_btn)
        btn_row.addStretch()
        card.addLayout(btn_row)

        root.addWidget(card)

        footer = QLabel("Bridge 不在线时 Hook 自动放行，不会阻塞 Agent。")
        footer.setObjectName("muted")
        footer.setWordWrap(True)
        root.addWidget(footer)
        root.addStretch()

    def set_status(self, text: str) -> None:
        self.status_label.setText(text)

    def set_last_event(self, text: str) -> None:
        self.last_label.setText(text)


class CodexHookPage(_HookPage):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(
            accent="cyan",
            title="Codex 生命周期 Hook",
            hint="安装会保留已有 Hook 并创建备份。写入配置后，请在 Codex 中运行 /hooks，"
                 "审核并信任 Vibe Remote Hook；未信任前 Codex 会跳过它。",
            parent=parent,
        )


class ClaudeHookPage(_HookPage):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(
            accent="violet",
            title="Claude Code 生命周期 Hook",
            hint="向 ~/.claude/settings.json 写入生命周期 Hook，覆盖开始、工具、权限、完成等事件；"
                 "保留现有 Hook 并自动备份。安装后 Claude Code 首次触发 Hook 时会询问是否信任。",
            parent=parent,
        )


class StatusPage(QWidget):
    """连接状态页：手机连接数 + 服务状态 + 最近 Hook 事件列表。"""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        root = QVBoxLayout(self)
        root.setContentsMargins(28, 28, 28, 28)
        root.setSpacing(14)

        title = QLabel("连接状态")
        title.setObjectName("section_title")
        root.addWidget(title)

        card = _SectionCard(self)
        # 服务状态
        svc_row = QHBoxLayout()
        svc_row.addWidget(QLabel("服务状态"))
        self.svc_label = QLabel("正在启动")
        self.svc_label.setStyleSheet(f"color:{C.CYAN}; font-weight:bold;")
        self.svc_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        svc_row.addStretch()
        svc_row.addWidget(self.svc_label)
        card.addLayout(svc_row)

        # 手机连接数
        ph_row = QHBoxLayout()
        ph_row.addWidget(QLabel("已连接手机"))
        self.phone_label = QLabel("0")
        self.phone_label.setStyleSheet(f"color:{C.CYAN}; font-weight:bold;")
        self.phone_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        ph_row.addStretch()
        ph_row.addWidget(self.phone_label)
        card.addLayout(ph_row)

        # 最近错误
        err_row = QHBoxLayout()
        err_row.addWidget(QLabel("最近错误"))
        self.err_label = QLabel("—")
        self.err_label.setStyleSheet(f"color:{C.MUTED};")
        self.err_label.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        err_row.addStretch()
        err_row.addWidget(self.err_label)
        card.addLayout(err_row)

        root.addWidget(card)

        # 最近 Hook 事件流
        root.addWidget(QLabel("最近 Hook 事件"))
        self.event_log = QTextEdit()
        self.event_log.setReadOnly(True)
        self.event_log.setMinimumHeight(180)
        root.addWidget(self.event_log, 1)

        root.addStretch()

    def set_service_status(self, text: str) -> None:
        self.svc_label.setText(text)

    def set_phone_count(self, n: int) -> None:
        self.phone_label.setText(str(n))

    def set_last_error(self, text: str) -> None:
        self.err_label.setText(text or "—")

    def append_event(self, line: str) -> None:
        self.event_log.append(line)


class KeymapPage(QWidget):
    """Keymap 编辑页：可编辑表格 + 增删/重置/保存/推送。

    支持操作：
      - 新增行：弹输入框填 id/label/display/group/type/mapping
      - 删除行：删除当前选中行
      - 重置默认：恢复出厂 keymap（需二次确认）
      - 保存：把当前表格写回 keymap.json
      - 推送到手机：调用 runtime.push_keymap() 广播给已连接手机
    """

    push_requested = Signal()
    save_requested = Signal(list)  # list[dict] 当前表格内容
    add_requested = Signal(dict)   # dict 新键定义
    remove_requested = Signal(str)  # str key_id
    reset_requested = Signal()

    COL_ID, COL_LABEL, COL_DISPLAY, COL_GROUP, COL_TYPE, COL_MAPPING = range(6)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        root = QVBoxLayout(self)
        root.setContentsMargins(28, 28, 28, 28)
        root.setSpacing(14)

        title = QLabel("按键映射管理")
        title.setObjectName("section_title")
        root.addWidget(title)

        hint = QLabel("手机端按键 ↔ 电脑端快捷键。编辑后点击\"保存\"写入 keymap.json，"
                      "再点\"推送到手机\"立即同步到已连接手机。")
        hint.setObjectName("muted")
        hint.setWordWrap(True)
        root.addWidget(hint)

        from PySide6.QtWidgets import (
            QTableWidget, QTableWidgetItem, QHeaderView, QAbstractItemView,
            QInputDialog, QMessageBox,
        )
        self._QInputDialog = QInputDialog
        self._QMessageBox = QMessageBox

        # ===== 表格（可编辑） =====
        self.table = QTableWidget(0, 6, self)
        self.table.setHorizontalHeaderLabels(
            ["ID", "标签", "显示", "分组", "类型", "映射"]
        )
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(QHeaderView.Stretch)
        # ID 列稍窄，映射列稍宽
        header.setSectionResizeMode(self.COL_ID, QHeaderView.ResizeToContents)
        header.setSectionResizeMode(self.COL_TYPE, QHeaderView.ResizeToContents)
        self.table.verticalHeader().setVisible(False)
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table.setEditTriggers(
            QAbstractItemView.DoubleClicked | QAbstractItemView.EditKeyPressed
        )
        self.table.itemChanged.connect(self._on_item_changed)
        root.addWidget(self.table, 1)

        # ===== 工具栏：新增 / 删除 / 重置 / 保存 / 推送 =====
        bar = QHBoxLayout()
        bar.setSpacing(8)

        self.add_btn = QPushButton("＋ 新增")
        self.add_btn.clicked.connect(self._on_add_clicked)
        bar.addWidget(self.add_btn)

        self.del_btn = QPushButton("－ 删除")
        self.del_btn.setObjectName("danger")
        self.del_btn.clicked.connect(self._on_del_clicked)
        bar.addWidget(self.del_btn)

        self.reset_btn = QPushButton("恢复默认")
        self.reset_btn.clicked.connect(self._on_reset_clicked)
        bar.addWidget(self.reset_btn)

        bar.addStretch()

        self.dirty_label = QLabel("")  # 未保存提示
        self.dirty_label.setStyleSheet(f"color:{C.AMBER};")
        bar.addWidget(self.dirty_label)

        self.save_btn = QPushButton("保存到 keymap.json")
        self.save_btn.setObjectName("primary")
        self.save_btn.clicked.connect(self._on_save_clicked)
        bar.addWidget(self.save_btn)

        self.push_btn = QPushButton("▶ 推送到手机")
        self.push_btn.setObjectName("primary")
        self.push_btn.clicked.connect(self.push_requested.emit)
        bar.addWidget(self.push_btn)

        root.addLayout(bar)

        self._dirty = False
        self._loading = False  # set_keymap 期间屏蔽 itemChanged

    # ===== 内部辅助 =====
    def _mark_dirty(self) -> None:
        if not self._dirty:
            self._dirty = True
            self.dirty_label.setText("● 未保存的修改")

    def _clear_dirty(self) -> None:
        if self._dirty:
            self._dirty = False
            self.dirty_label.setText("")

    def _on_item_changed(self, _item) -> None:
        if not self._loading:
            self._mark_dirty()

    def _on_add_clicked(self) -> None:
        """新增行：弹一个多字段输入对话框。"""
        from PySide6.QtWidgets import QDialog, QFormLayout, QLineEdit, QComboBox, QDialogButtonBox
        dlg = QDialog(self)
        dlg.setWindowTitle("新增按键")
        form = QFormLayout(dlg)
        le_id = QLineEdit()
        le_id.setPlaceholderText("如：n5（唯一）")
        le_label = QLineEdit()
        le_label.setPlaceholderText("如：5")
        le_display = QLineEdit()
        le_display.setPlaceholderText("如：5 / Ctrl+S")
        le_group = QLineEdit()
        le_group.setPlaceholderText("如：num / fn / clip / dpad")
        cb_type = QComboBox()
        cb_type.addItems(["key", "combo", "action"])
        le_mapping = QLineEdit()
        le_mapping.setPlaceholderText("key: 单键名 / combo: ctrl+c / action: home")

        form.addRow("ID", le_id)
        form.addRow("标签", le_label)
        form.addRow("显示", le_display)
        form.addRow("分组", le_group)
        form.addRow("类型", cb_type)
        form.addRow("映射", le_mapping)

        btns = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        btns.accepted.connect(dlg.accept)
        btns.rejected.connect(dlg.reject)
        form.addRow(btns)

        if dlg.exec() != QDialog.Accepted:
            return
        kid = le_id.text().strip()
        if not kid:
            self._QMessageBox.warning(self, "新增失败", "ID 不能为空")
            return
        kind = cb_type.currentText()
        mapping_raw = le_mapping.text().strip()
        # 把 combo 字符串解析为 list
        if kind == "combo":
            if "+" in mapping_raw:
                mapping = [p.strip() for p in mapping_raw.split("+") if p.strip()]
            else:
                mapping = [p.strip() for p in mapping_raw.split(",") if p.strip()]
            if not mapping:
                self._QMessageBox.warning(self, "新增失败", "combo 类型映射需为 ctrl+c 或 ctrl,c 形式")
                return
        else:
            mapping = mapping_raw
        item = {
            "id": kid,
            "label": le_label.text().strip() or kid,
            "display": le_display.text().strip() or le_label.text().strip() or kid,
            "group": le_group.text().strip() or "fn",
            "type": kind,
            "mapping": mapping,
        }
        # 直接在表格底部插入一行（避免 round-trip）
        self._append_row(item)
        self._mark_dirty()
        # 同时 emit 给 runtime 立即写入（保持同步）
        self.add_requested.emit(item)

    def _on_del_clicked(self) -> None:
        r = self.table.currentRow()
        if r < 0:
            self._QMessageBox.information(self, "删除", "请先选中一行")
            return
        kid = self.table.item(r, self.COL_ID).text()
        # 删除表格行
        self.table.removeRow(r)
        self._mark_dirty()
        self.remove_requested.emit(kid)

    def _on_reset_clicked(self) -> None:
        if self._QMessageBox.question(
            self, "恢复默认", "将覆盖当前 keymap.json 为出厂布局，确定吗？",
            self._QMessageBox.Yes | self._QMessageBox.No,
            self._QMessageBox.No,
        ) != self._QMessageBox.Yes:
            return
        self.reset_requested.emit()
        # runtime 会通过 keymap_changed 重新推回 UI

    def _on_save_clicked(self) -> None:
        """收集当前表格所有行，emit save_requested。"""
        keys = self.collect_keys()
        self.save_requested.emit(keys)
        self._clear_dirty()

    def _append_row(self, k: dict) -> None:
        from PySide6.QtWidgets import QTableWidgetItem
        r = self.table.rowCount()
        self._loading = True
        self.table.insertRow(r)
        self.table.setItem(r, self.COL_ID, QTableWidgetItem(str(k.get("id", ""))))
        self.table.setItem(r, self.COL_LABEL, QTableWidgetItem(str(k.get("label", ""))))
        self.table.setItem(r, self.COL_DISPLAY, QTableWidgetItem(str(k.get("display", ""))))
        self.table.setItem(r, self.COL_GROUP, QTableWidgetItem(str(k.get("group", ""))))
        self.table.setItem(r, self.COL_TYPE, QTableWidgetItem(str(k.get("type", "key"))))
        mapping = k.get("mapping", "")
        if isinstance(mapping, list):
            mapping = "+".join(mapping)
        self.table.setItem(r, self.COL_MAPPING, QTableWidgetItem(str(mapping)))
        self._loading = False

    def collect_keys(self) -> list[dict]:
        """把表格内容收集为 keymap.keys 数组。"""
        out: list[dict] = []
        for r in range(self.table.rowCount()):
            kid = self.table.item(r, self.COL_ID).text().strip()
            if not kid:
                continue
            label = self.table.item(r, self.COL_LABEL).text().strip()
            display = self.table.item(r, self.COL_DISPLAY).text().strip()
            group = self.table.item(r, self.COL_GROUP).text().strip()
            kind = self.table.item(r, self.COL_TYPE).text().strip() or "key"
            mapping_raw = self.table.item(r, self.COL_MAPPING).text().strip()
            if kind == "combo":
                if "+" in mapping_raw:
                    mapping = [p.strip() for p in mapping_raw.split("+") if p.strip()]
                else:
                    mapping = [p.strip() for p in mapping_raw.split(",") if p.strip()]
                if not mapping:
                    mapping = [mapping_raw]
            else:
                mapping = mapping_raw
            out.append({
                "id": kid, "label": label, "display": display or label,
                "group": group or "fn", "type": kind, "mapping": mapping,
            })
        return out

    # ===== 对外接口 =====
    def set_keymap(self, keys: list[dict]) -> None:
        """runtime 推来的 keymap 数据。"""
        from PySide6.QtWidgets import QTableWidgetItem
        self._loading = True
        self.table.setRowCount(0)
        for k in keys:
            r = self.table.rowCount()
            self.table.insertRow(r)
            self.table.setItem(r, self.COL_ID, QTableWidgetItem(str(k.get("id", ""))))
            self.table.setItem(r, self.COL_LABEL, QTableWidgetItem(str(k.get("label", ""))))
            self.table.setItem(r, self.COL_DISPLAY, QTableWidgetItem(str(k.get("display", ""))))
            self.table.setItem(r, self.COL_GROUP, QTableWidgetItem(str(k.get("group", ""))))
            self.table.setItem(r, self.COL_TYPE, QTableWidgetItem(str(k.get("type", "key"))))
            mapping = k.get("mapping", "")
            if isinstance(mapping, list):
                mapping = "+".join(mapping)
            self.table.setItem(r, self.COL_MAPPING, QTableWidgetItem(str(mapping)))
        self._loading = False
        self._clear_dirty()


class MainWindow(QMainWindow):
    """主窗口：4 主标签（连接/KEYMAP/CODEX/CLAUDE） + 右上角状态徽章组 + 底部状态栏。"""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setWindowTitle("Vibe Remote Bridge")
        self.setMinimumSize(720, 540)
        self.resize(820, 620)

        central = QWidget(self)
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(20, 20, 20, 20)
        root.setSpacing(10)

        # ===== 顶部品牌区 + 右上角状态徽章组 =====
        brand = QHBoxLayout()
        brand.setSpacing(14)
        # Logo 占位（黑底黄字方块，对齐 AgentPager logo）
        logo = QLabel("V")
        logo.setFixedSize(40, 40)
        logo.setAlignment(Qt.AlignCenter)
        logo.setStyleSheet(
            f"background:{C.AMBER}; color:{C.BG}; font-weight:900; font-size:18px;"
        )
        brand.addWidget(logo)

        title_box = QVBoxLayout()
        title_box.setSpacing(2)
        brand_title = QLabel("VIBE REMOTE BRIDGE")
        brand_title.setObjectName("brand_title")
        title_box.addWidget(brand_title)
        self.service_status = QLabel("正在启动")
        self.service_status.setStyleSheet(f"color:{C.CYAN};")
        title_box.addWidget(self.service_status)
        brand.addLayout(title_box)
        brand.addStretch()

        # 右上角徽章组：仅保留 PHONE 数（纯展示，不扫描后台设备）
        badge_box = QHBoxLayout()
        badge_box.setSpacing(8)
        # PHONE 数徽章（纯展示连接数，不响应点击，避免后台扫描）
        self.phone_badge = QLabel("PHONE 0")
        self.phone_badge.setStyleSheet(
            f"background:{C.SURFACE}; color:{C.CYAN}; border:1px solid {C.BORDER};"
            f"padding:4px 10px; font-weight:bold;"
        )
        badge_box.addWidget(self.phone_badge)
        brand.addLayout(badge_box)

        root.addLayout(brand)

        # ===== 主标签：连接 / KEYMAP / CODEX / CLAUDE =====
        self.primary_tabs = QTabWidget(self)
        self.primary_tabs.setDocumentMode(True)
        self.pairing_page = PairingPage(self)
        self.keymap_page = KeymapPage(self)
        self.codex_hook_page = CodexHookPage(self)
        self.claude_hook_page = ClaudeHookPage(self)
        self.primary_tabs.addTab(self.pairing_page, "连接")
        self.primary_tabs.addTab(self.keymap_page, "KEYMAP")
        self.primary_tabs.addTab(self.codex_hook_page, "CODEX")
        self.primary_tabs.addTab(self.claude_hook_page, "CLAUDE")
        root.addWidget(self.primary_tabs, 1)

        # ===== 标签切换淡入动画 =====
        from PySide6.QtWidgets import QGraphicsOpacityEffect
        from PySide6.QtCore import QPropertyAnimation, QEasingCurve
        self._QPropertyAnimation = QPropertyAnimation
        self._QEasingCurve = QEasingCurve
        self._tab_effects: dict[int, QGraphicsOpacityEffect] = {}
        for i in range(self.primary_tabs.count()):
            page = self.primary_tabs.widget(i)
            eff = QGraphicsOpacityEffect(page)
            eff.setOpacity(1.0)
            page.setGraphicsEffect(eff)
            self._tab_effects[i] = eff
        self._tab_anim: QPropertyAnimation | None = None
        # 切换时触发淡入
        self.primary_tabs.currentChanged.connect(self._on_tab_changed)

        # ===== 底部状态栏 =====
        bar = self.statusBar()
        self.diag_label = QLabel("正在初始化")
        self.diag_label.setStyleSheet(f"color:{C.MUTED};")
        bar.addWidget(self.diag_label, 1)
        self.log_btn = QPushButton("打开日志目录")
        bar.addPermanentWidget(self.log_btn)

        # ===== 标签切换淡入动画 =====
        from PySide6.QtWidgets import QGraphicsOpacityEffect
        from PySide6.QtCore import QPropertyAnimation, QEasingCurve
        self._QPropertyAnimation = QPropertyAnimation
        self._QEasingCurve = QEasingCurve
        self._tab_effects: dict[int, QGraphicsOpacityEffect] = {}
        for i in range(self.primary_tabs.count()):
            page = self.primary_tabs.widget(i)
            eff = QGraphicsOpacityEffect(page)
            eff.setOpacity(1.0)
            page.setGraphicsEffect(eff)
            self._tab_effects[i] = eff
        self._tab_anim: QPropertyAnimation | None = None
        # 切换时触发淡入
        self.primary_tabs.currentChanged.connect(self._on_tab_changed)

    def _on_tab_changed(self, idx: int) -> None:
        """切换主标签时，新页面 opacity 0→1 淡入（180ms，OutCubic 缓动）。"""
        eff = self._tab_effects.get(idx)
        if eff is None:
            return
        # 取消上一个动画（避免快速切换时动画堆叠）
        if self._tab_anim is not None:
            self._tab_anim.stop()
            self._tab_anim = None
        anim = self._QPropertyAnimation(eff, b"opacity", self)
        anim.setDuration(180)
        anim.setStartValue(0.0)
        anim.setEndValue(1.0)
        anim.setEasingCurve(self._QEasingCurve.OutCubic)
        anim.finished.connect(lambda: setattr(self, "_tab_anim", None))
        anim.start()
        self._tab_anim = anim

    # ===== 对外槽（runtime 调用） =====
    def set_service_status(self, text: str) -> None:
        self.service_status.setText(text)

    def set_phone_count(self, n: int) -> None:
        """右上角 PHONE 徽章文字更新。"""
        self.phone_badge.setText(f"PHONE {n}")

    def set_diag(self, text: str) -> None:
        self.diag_label.setText(text)

    def append_event(self, line: str) -> None:
        # 兼容旧 API：事件塞到底部状态栏
        self.set_diag(line)
