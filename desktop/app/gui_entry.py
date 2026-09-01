"""GUI 入口：强制以 --gui 模式启动（pywebview 窗口，关闭即退出 server）。"""
import sys

# 强制注入 --gui 参数，让 main.py 走 GUI 分支
if "--gui" not in sys.argv:
    sys.argv = [sys.argv[0], "--gui"]

from main import main

if __name__ == "__main__":
    main()
