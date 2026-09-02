"""AgentPager 风格调色板与字体加载。

调色板严格对齐 AgentPager Windows WPF（见 vibe_conding/AgentPager-main/windows/src/AgentPager.Bridge/MainWindow.xaml）：
  Bg         = #090D12   主背景
  Surface    = #111923   次背景
  Surface2   = #182432   凸起背景
  Border     = #263747   分隔线/边框
  Cyan       = #35E6E0   主强调色（CODEX）
  Violet     = #A78BFA   CLAUDE 强调色
  Amber      = #FDBA4A   高亮
  Muted      = #B7C7D8   次要文字
  Fg         = #E8F4FF   主文字

字体：Fusion Pixel 12px zh_hans（点阵像素体），路径与 AgentPager 一致。
"""
from __future__ import annotations

from pathlib import Path
from PySide6.QtGui import QFontDatabase, QFont, QColor
from PySide6.QtWidgets import QApplication
from PySide6.QtCore import Qt, QObject


# ====== 颜色常量 ======
class C:
    BG = "#090D12"
    SURFACE = "#111923"
    SURFACE2 = "#182432"
    BORDER = "#263747"
    CYAN = "#35E6E0"
    VIOLET = "#A78BFA"
    AMBER = "#FDBA4A"
    GREEN = "#4ADE80"
    RED = "#FB7185"
    MUTED = "#B7C7D8"
    FG = "#E8F4FF"


# 字体路径：vibe-remote/desktop_v2/assets/fonts/ + 回落到 AgentPager 源仓库
_HERE = Path(__file__).resolve().parent
_FONT_CANDIDATES = [
    _HERE / "assets" / "fonts" / "fusion_pixel_12px_zh_hans.ttf",
    # 回落到 AgentPager 源仓库（开发期便于复用，发布前需拷贝到本目录）
    _HERE.parent.parent / "vibe_conding" / "AgentPager-main" / "assets" / "fonts" / "fusion_pixel_12px_zh_hans.ttf",
    _HERE.parent.parent / "vibe_conding" / "AgentPager-main" / "android" / "app" / "src" / "main" / "res" / "font" / "fusion_pixel_12px_zh_hans.ttf",
]

PIXEL_FONT_FAMILY = "Fusion Pixel 12px Prop zh_hans"
_pixel_font_id: int | None = None


def load_pixel_font() -> str:
    """加载 Fusion Pixel 字体，返回 QFontDatabase 中的 family。失败则回落到系统等宽字体。"""
    global _pixel_font_id
    if _pixel_font_id is not None:
        return PIXEL_FONT_FAMILY
    for p in _FONT_CANDIDATES:
        if p.exists():
            _pixel_font_id = QFontDatabase.addApplicationFont(str(p))
            if _pixel_font_id != -1:
                fams = QFontDatabase.applicationFontFamilies(_pixel_font_id)
                if fams:
                    PIXEL_FONT_FAMILY = fams[0]  # noqa: PLW0603 - 实际加载后用系统返回的 family 名
                    return PIXEL_FONT_FAMILY
            break
    # 字体加载失败，回落
    PIXEL_FONT_FAMILY = "Consolas"  # noqa: PLW0603
    return PIXEL_FONT_FAMILY


QSS_TEMPLATE = """
* {{
    font-family: "{font}";
    font-size: 13px;
    color: {fg};
}}
QMainWindow, QWidget {{
    background-color: {bg};
    color: {fg};
}}
QLabel {{
    background: transparent;
    color: {fg};
    border: none;
}}
QLabel#section_title {{
    color: {cyan};
    font-size: 18px;
    font-weight: bold;
}}
QLabel#brand_title {{
    color: {cyan};
    font-size: 20px;
    font-weight: bold;
    letter-spacing: 2px;
}}
QLabel#muted {{
    color: {muted};
}}
QLabel#violet_title {{
    color: {violet};
    font-size: 18px;
    font-weight: bold;
}}
/* 主标签页 */
QTabWidget::pane {{
    background: {surface};
    border: 1px solid {border};
    border-top: none;  /* pane 顶部无边框，让选中标签的青色下边框直接对接内容区 */
}}
QTabBar {{
    background: {bg};
}}
QTabBar::tab {{
    background: {surface};
    color: {fg};
    border: 1px solid {border};
    padding: 10px 22px;
    margin: 0;
    border-bottom: 1px solid {border};  /* 未选中时下边框可见，与上方形成"框选"视觉 */
}}
QTabBar::tab:selected {{
    background: {surface2};
    color: {cyan};
    border-color: {cyan};
    border-bottom: 1px solid {cyan};  /* 选中标签的下边框为青色"框选线" */
}}
QTabBar::tab:hover:!selected {{
    background: #203041;
}}
/* 次级标签页（小号） */
QTabBar#secondary::tab {{
    padding: 6px 14px;
    font-size: 12px;
}}
/* 按钮 */
QPushButton {{
    background: {surface2};
    color: {fg};
    border: 1px solid {cyan};
    padding: 8px 13px;
}}
QPushButton:hover {{
    background: #203041;
    border-color: {cyan};
}}
QPushButton:pressed {{
    background: #1A3440;
}}
QPushButton#danger {{
    border-color: {red};
    color: {fg};
}}
QPushButton#danger:hover {{
    background: #2A1A22;
}}
QPushButton#primary {{
    background: {cyan};
    color: #0A1219;
    border: 1px solid {cyan};
    font-weight: 600;
}}
QPushButton#primary:hover {{
    background: #5FB8D4;
    border-color: #5FB8D4;
}}
QPushButton#primary:pressed {{
    background: #2D8FA8;
}}
QPushButton#primary:disabled {{
    background: #2A3A45;
    color: #6A7A85;
    border-color: #2A3A45;
}}
/* 输入控件 */
QLineEdit, QPlainTextEdit, QTextEdit {{
    background: {bg};
    color: #C6D6E5;
    border: 1px solid {border};
    padding: 6px 8px;
}}
QComboBox {{
    background: {surface2};
    color: {fg};
    border: 1px solid {border};
    padding: 6px;
}}
QComboBox QAbstractItemView {{
    background: {surface};
    color: {fg};
    border: 1px solid {cyan};
    selection-background-color: #1A3440;
    selection-color: {cyan};
    outline: 0;
}}
QCheckBox {{
    color: {fg};
    background: transparent;
    spacing: 8px;
}}
QCheckBox::indicator {{
    width: 14px;
    height: 14px;
    border: 1px solid {border};
    background: {bg};
}}
QCheckBox::indicator:checked {{
    background: {cyan};
    border-color: {cyan};
}}
/* 状态栏 */
QStatusBar {{
    background: {surface};
    color: {muted};
    border-top: 1px solid {border};
}}
/* 滚动条 */
QScrollBar:vertical {{
    background: {surface};
    width: 10px;
    border: none;
}}
QScrollBar::handle:vertical {{
    background: {border};
    min-height: 24px;
    margin: 2px;
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0;
    border: none;
    background: none;
}}
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{
    background: none;
}}
"""


def apply_theme(app: QApplication) -> str:
    """加载字体并应用 QSS 到 QApplication。返回加载到的字体 family。"""
    fam = load_pixel_font()
    qss = QSS_TEMPLATE.format(
        font=fam,
        bg=C.BG,
        surface=C.SURFACE,
        surface2=C.SURFACE2,
        border=C.BORDER,
        cyan=C.CYAN,
        violet=C.VIOLET,
        amber=C.AMBER,
        green=C.GREEN,
        red=C.RED,
        muted=C.MUTED,
        fg=C.FG,
    )
    app.setStyleSheet(qss)
    # 默认字体也设为像素体（控件会继承）
    f = QFont(fam, 13)
    app.setFont(f)
    return fam


def qcolor(name: str) -> QColor:
    """快速构造 QColor。"""
    return QColor(name)
