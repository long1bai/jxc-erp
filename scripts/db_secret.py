# -*- coding: utf-8 -*-
"""数据库凭据读取 —— 脚本侧唯一来源（密码不进 git）

优先级：环境变量 DB_PASSWORD > 项目根 config/db_secret.env
口径与 dr_backup.py 一致，避免把密码硬编码进各个脚本。

用法：
    from db_secret import DB_AUTH          # ["-p<密码>"]，密码为空时是 []
    subprocess.run([MYSQL_BIN, "-uroot"] + DB_AUTH + ["jxc_erp", "-e", sql])
"""
import os
from pathlib import Path

# scripts/ 上两级 = 项目根（01-ERP），与 config/ 同级
ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / "config" / "db_secret.env"


def load_password() -> str:
    """返回数据库密码；取不到时返回空串（调用方自行降级）。"""
    pw = os.environ.get("DB_PASSWORD", "").strip()
    if pw:
        return pw
    try:
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("DB_PASSWORD=") and not line.startswith("#"):
                return line.split("=", 1)[1].strip()
    except Exception:
        pass
    return ""


DB_PASSWORD = load_password()
DB_AUTH = ["-p" + DB_PASSWORD] if DB_PASSWORD else []
