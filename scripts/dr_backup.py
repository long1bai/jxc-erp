# -*- coding: utf-8 -*-
"""
进销存 ERP v2 容灾备份脚本（2026-08-01 新增，2026-08-02 新电脑适配 + 数据库密码 + 异地备份）
- 数据库全量备份（mysqldump，双库：jxc_erp 空壳版 + yawei_erp 真实业务数据）→ 本机备份 + 异地备份（如配置）
- 上传图片增量拷贝（Desktop\\jxc\\01-ERP\\uploads → 同目录 uploads\）
- 配置文件 + JWT 密钥（ai_config / photo_config / jwt_secret）→ 同目录 config\
- 保留策略：超过 30 天的日期目录自动清理
- 异地备份：OFF_SITE_BACKUP_DIR 环境变量指定目标（U盘/网络盘/共享文件夹），
  未设置时尝试读 db_secret.env 的 OFF_SITE_BACKUP_DIR，再没有则跳过（只做本机备份）
- 数据库密码：从 db_secret.env 读 DB_PASSWORD

退出码：0=成功 1=失败。输出一行摘要供计划任务日志。
"""
import os
import shutil
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

# ========== 路径（新电脑 2026-08-02 适配） ==========
ROOT = Path(r"C:\Users\17815\Desktop\jxc\01-ERP")
MYSQLDUMP = str(ROOT / "mysql" / "8.0.28" / "bin" / "mysqldump.exe")
MYSQL = str(ROOT / "mysql" / "8.0.28" / "bin" / "mysql.exe")
# 备份范围（2026-09-12 起为双库）：
#   jxc_erp   = 脱敏空壳（种子数据，交付/演示用）
#   yawei_erp = 真实业务数据（3.6 万行，2026-08-01 快照恢复而来）
DB_NAMES = ["jxc_erp", "yawei_erp"]
UPLOADS_DIR = ROOT / "uploads"
BACKUP_ROOT = ROOT / "erp-server" / "backup" / "daily"   # 本机备份（项目下）
CONFIG_FILES = [
    ROOT / "config" / "ai_config.json",      # AI 报价配置（含密钥）
    ROOT / "config" / "photo_config.json",   # 拍照识别配置（含密钥）
    ROOT / "config" / "jwt_secret.txt",      # JWT 签名密钥（丢了全员重登）
    ROOT / "config" / "db_secret.env",       # 数据库密码（备份恢复必需）
    ROOT / "erp-server" / "backend" / "src" / "main" / "resources" / "application.yml",
]
RETENTION_DAYS = 30

# ========== 数据库密码（从 db_secret.env 读） ==========
DB_PASSWORD = ""
try:
    env_file = ROOT / "config" / "db_secret.env"
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("DB_PASSWORD=") and not line.startswith("#"):
            DB_PASSWORD = line.split("=", 1)[1].strip()
except Exception:
    pass
DB_AUTH = ["-p" + DB_PASSWORD] if DB_PASSWORD else []

# ========== 异地备份目标 ==========
OFF_SITE_DIR = os.environ.get("OFF_SITE_BACKUP_DIR", "").strip()
if not OFF_SITE_DIR:
    try:
        env_file = ROOT / "config" / "db_secret.env"
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("OFF_SITE_BACKUP_DIR=") and not line.startswith("#"):
                OFF_SITE_DIR = line.split("=", 1)[1].strip()
                break
    except Exception:
        pass

DATE = datetime.now().strftime("%Y%m%d")
TARGET = BACKUP_ROOT / DATE
TARGET_UPLOADS = TARGET / "uploads"
TARGET_CONFIG = TARGET / "config"


def run(cmd, timeout=600):
    """Windows 控制台 GBK 输出兜底"""
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")


def backup_to(dest_root: Path, label: str, steps: list) -> bool:
    """执行一次完整备份到 dest_root。返回是否成功。"""
    ok = True
    target = dest_root / DATE
    t_uploads = target / "uploads"
    t_config = target / "config"
    try:
        target.mkdir(parents=True, exist_ok=True)
        t_uploads.mkdir(parents=True, exist_ok=True)
        t_config.mkdir(parents=True, exist_ok=True)

        # 1. 数据库全量备份（多库逐个导出，任一失败即整体 FAIL）
        for db in DB_NAMES:
            sql_file = target / ("%s_%s.sql" % (db, DATE))
            p = run([MYSQLDUMP, "-uroot"] + DB_AUTH + ["--single-transaction", "--routines",
                     "--default-character-set=utf8mb4", db], timeout=900)
            if p.returncode != 0:
                steps.append(f"{label}DB[{db}]:FAIL(%s)" % (p.stderr or p.stdout)[:120])
                ok = False
            else:
                sql_file.write_text(p.stdout, encoding="utf-8", errors="replace")
                steps.append(f"{label}DB[{db}]:%s" % human(sql_file.stat().st_size))

        # 2. 上传图片增量拷贝
        if UPLOADS_DIR.exists():
            p = run(["xcopy", str(UPLOADS_DIR), str(t_uploads), "/e", "/d", "/y", "/q"], timeout=1800)
            steps.append(f"{label}IMG:%s" % ("OK" if p.returncode in (0, 1) else "FAIL(%s)" % (p.stderr or "")[:100]))
            if p.returncode not in (0, 1):
                ok = False
        else:
            steps.append(f"{label}IMG:SKIP")

        # 3. 配置文件 + 密钥
        cfg_ok = True
        for f in CONFIG_FILES:
            if f.exists():
                try:
                    shutil.copy2(f, t_config / f.name)
                except Exception as e:
                    cfg_ok = False
                    steps.append(f"{label}CFG:%s=FAIL(%s)" % (f.name, str(e)[:60]))
        steps.append(f"{label}CFG:%s" % ("OK" if cfg_ok else "FAIL"))
        if not cfg_ok:
            ok = False

        # 4. 保留策略：清理 30 天前的日期目录
        cutoff = datetime.now() - timedelta(days=RETENTION_DAYS)
        removed = 0
        if dest_root.exists():
            for d in dest_root.iterdir():
                if d.is_dir() and len(d.name) == 8 and d.name.isdigit():
                    try:
                        d_date = datetime.strptime(d.name, "%Y%m%d")
                        if d_date < cutoff:
                            shutil.rmtree(d, ignore_errors=True)
                            removed += 1
                    except ValueError:
                        pass
        steps.append(f"{label}RET:清%d" % removed)
        return ok
    except Exception as e:
        steps.append(f"{label}ERROR:%s" % str(e)[:200])
        return False


def main():
    steps = []
    ok = True

    # 本机备份
    ok = backup_to(BACKUP_ROOT, "", steps) and ok

    # 异地备份（如果配置了目标）
    if OFF_SITE_DIR:
        off = Path(OFF_SITE_DIR)
        try:
            off.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            steps.append(f"OFF-SITE:目标不可用(%s)" % str(e)[:80])
            ok = False
        if off.exists():
            ok = backup_to(off, "OFF:", steps) and ok
            steps.append("OFF-SITE:到 %s" % OFF_SITE_DIR)
    else:
        steps.append("OFF-SITE:未配置(仅本机备份)")

    print("JXC-DR-BACKUP %s %s exit=%s" % (DATE, " ".join(steps), "OK" if ok else "FAIL"))
    return 0 if ok else 1


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return "%.1f%s" % (n, unit)
        n /= 1024
    return "%.1fTB" % n


if __name__ == "__main__":
    sys.exit(main())
