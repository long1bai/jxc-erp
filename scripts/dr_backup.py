# -*- coding: utf-8 -*-
"""
jxc ERP v2 容灾备份脚本（2026-08-01 新增）
- 数据库全量备份（mysqldump）→ H:\jxc系统备份\ERP\YYYYMMDD\
- 上传图片增量拷贝（I:\yawei-uploads → 同目录 uploads\）
- 配置文件 + JWT 密钥（ai_config / photo_config / jwt_secret）→ 同目录 config\
- 保留策略：ERP 目录下超过 30 天的日期目录自动清理
- 由 Windows 计划任务每天 17:30 触发（也可手动运行）

退出码：0=成功 1=失败。输出一行摘要供计划任务日志。
"""
import shutil
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

# ========== 路径 ==========
MYSQLDUMP = r"C:\mysql\8.0.28\bin\mysqldump.exe"
MYSQL = r"C:\mysql\8.0.28\bin\mysql.exe"
DB_NAME = "yawei_erp"
UPLOADS_DIR = Path(r"I:\yawei-uploads")
BACKUP_ROOT = Path(r"H:\jxc系统备份\ERP")
CONFIG_FILES = [
    Path(r"I:\yawei-erp\ai_config.json"),      # AI 报价配置（含密钥）
    Path(r"I:\yawei-erp\photo_config.json"),   # 拍照识别配置（含密钥）
    Path(r"I:\yawei-erp\jwt_secret.txt"),      # JWT 签名密钥（丢了全员重登）
    Path(r"I:\yawei-erp-java\backend\src\main\resources\application.yml"),
]
RETENTION_DAYS = 30

DATE = datetime.now().strftime("%Y%m%d")
TARGET = BACKUP_ROOT / DATE
TARGET_UPLOADS = TARGET / "uploads"
TARGET_CONFIG = TARGET / "config"


def run(cmd, timeout=600):
    """Windows 控制台 GBK 输出兜底"""
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")


def main():
    steps = []
    ok = True

    try:
        TARGET.mkdir(parents=True, exist_ok=True)
        TARGET_UPLOADS.mkdir(parents=True, exist_ok=True)
        TARGET_CONFIG.mkdir(parents=True, exist_ok=True)

        # 1. 数据库全量备份
        sql_file = TARGET / ("yawei_erp_%s.sql" % DATE)
        p = run([MYSQLDUMP, "-uroot", "--single-transaction", "--routines",
                 "--default-character-set=utf8mb4", DB_NAME], timeout=900)
        if p.returncode != 0:
            steps.append("DB:FAIL(%s)" % (p.stderr or p.stdout)[:120])
            ok = False
        else:
            sql_file.write_text(p.stdout, encoding="utf-8", errors="replace")
            steps.append("DB:%s" % human(sql_file.stat().st_size))

        # 2. 上传图片增量拷贝（/d 只拷比目标新的文件，保留目录结构）
        if UPLOADS_DIR.exists():
            p = run(["xcopy", str(UPLOADS_DIR), str(TARGET_UPLOADS), "/e", "/d", "/y", "/q"], timeout=1800)
            # xcopy 退出码 1 = 有文件复制（正常），0 = 无变化
            steps.append("IMG:%s" % ("OK" if p.returncode in (0, 1) else "FAIL(%s)" % (p.stderr or "")[:100]))
            if p.returncode not in (0, 1):
                ok = False
        else:
            steps.append("IMG:SKIP(无上传目录)")

        # 3. 配置文件 + 密钥
        cfg_ok = True
        for f in CONFIG_FILES:
            if f.exists():
                try:
                    shutil.copy2(f, TARGET_CONFIG / f.name)
                except Exception as e:
                    cfg_ok = False
                    steps.append("CFG:%s=FAIL(%s)" % (f.name, str(e)[:60]))
        if cfg_ok:
            steps.append("CFG:OK(%d个)" % len([f for f in CONFIG_FILES if f.exists()]))
        else:
            ok = False

        # 4. 保留策略：清理 30 天前的日期目录
        cutoff = datetime.now() - timedelta(days=RETENTION_DAYS)
        removed = 0
        if BACKUP_ROOT.exists():
            for d in BACKUP_ROOT.iterdir():
                if d.is_dir() and len(d.name) == 8 and d.name.isdigit():
                    try:
                        d_date = datetime.strptime(d.name, "%Y%m%d")
                        if d_date < cutoff:
                            shutil.rmtree(d, ignore_errors=True)
                            removed += 1
                    except ValueError:
                        pass
        steps.append("RET:清%d" % removed)

        print("YAWEI-DR-BACKUP %s %s exit=%s" % (DATE, " ".join(steps), "OK" if ok else "FAIL"))
        return 0 if ok else 1

    except Exception as e:
        print("YAWEI-DR-BACKUP %s ERROR %s" % (DATE, str(e)[:200]))
        return 1


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return "%.1f%s" % (n, unit)
        n /= 1024
    return "%.1fTB" % n


if __name__ == "__main__":
    sys.exit(main())
