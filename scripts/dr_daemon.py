# -*- coding: utf-8 -*-
"""
jxc ERP 备份守护进程（2026-08-01 新增，容灾配套）
- 常驻后台（启动文件夹 vbs 拉起），每天 17:30 触发 dr_backup.py
- PID 锁防重复实例：锁文件 I:\yawei-erp-java\scripts\.dr_daemon.lock
- 电脑休眠/关机期间错过的备份：开机后 10 分钟内补跑（startup catch-up）
- 日志：I:\yawei-erp-java\scripts\dr_daemon.log（追加）
"""
import os
import subprocess
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

SCRIPTS = Path(__file__).parent
LOCK = SCRIPTS / ".dr_daemon.lock"
LOG = SCRIPTS / "dr_daemon.log"
BACKUP_SCRIPT = SCRIPTS / "dr_backup.py"
BACKUP_HOUR, BACKUP_MINUTE = 17, 30
CHECK_INTERVAL = 60  # 秒
CATCHUP_WINDOW_MIN = 10  # 开机后补跑窗口（分钟）


def log(msg):
    line = "%s %s" % (datetime.now().strftime("%Y-%m-%d %H:%M:%S"), msg)
    print(line, flush=True)
    try:
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass


def acquire_lock():
    """PID 锁：锁存在且进程存活 → 已运行；否则抢占"""
    if LOCK.exists():
        try:
            pid = int(LOCK.read_text().strip())
            # Windows 上 os.kill(pid, 0) 不可靠，用 tasklist 查
            p = subprocess.run(["tasklist", "/FI", "PID eq %d" % pid],
                               capture_output=True, text=True, timeout=15,
                               encoding="utf-8", errors="replace")
            if "python" in p.stdout.lower() or "pythonw" in p.stdout.lower():
                log("已有一个备份守护在运行 (PID %d)，退出" % pid)
                return False
        except (ValueError, OSError, subprocess.TimeoutExpired):
            pass
        # 锁过期（进程死了），删除重抢
        try:
            LOCK.unlink()
        except OSError:
            pass
    LOCK.write_text(str(os.getpid()))
    return True


def run_backup():
    log("触发备份...")
    try:
        p = subprocess.run([sys.executable, str(BACKUP_SCRIPT)],
                           capture_output=True, text=True, timeout=1800,
                           encoding="utf-8", errors="replace")
        out = (p.stdout or "").strip().splitlines()
        log("备份结果: exit=%s %s" % (p.returncode, out[-1] if out else ""))
    except Exception as e:
        log("备份异常: %s" % str(e)[:200])


def main():
    if not acquire_lock():
        return 0
    log("备份守护启动 (PID %d)，每天 %02d:%02d 自动备份" % (os.getpid(), BACKUP_HOUR, BACKUP_MINUTE))

    last_backup_date = None  # 当天已备份过的日期（防重复）
    started_at = datetime.now()

    try:
        while True:
            now = datetime.now()

            # 补跑逻辑：开机 10 分钟内且当前已过备份时间但今天还没备 → 补跑
            boot_catchup = (
                (now - started_at).total_seconds() < CATCHUP_WINDOW_MIN * 60
                and (now.hour, now.minute) > (BACKUP_HOUR, BACKUP_MINUTE)
                and now.date() != last_backup_date
            )
            # 定时逻辑：到点且今天还没备
            scheduled = (
                now.hour == BACKUP_HOUR and now.minute >= BACKUP_MINUTE
                and now.date() != last_backup_date
            )

            if (scheduled or boot_catchup) and now.date() != last_backup_date:
                run_backup()
                last_backup_date = now.date()

            time.sleep(CHECK_INTERVAL)
    except KeyboardInterrupt:
        log("守护退出")
    finally:
        try:
            LOCK.unlink()
        except OSError:
            pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
