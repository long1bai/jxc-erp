# -*- coding: utf-8 -*-
"""
进销存 ERP + Hermes Agent 全量迁移打包脚本（2026-08-01）
- 目标：把整套系统打包到 H:\jxc-迁移包-<日期>\，新电脑解压即可运行
- 内容：
  1. ERP 源码 + jar（剔除 node_modules/target 缓存，保留可重建）
  2. MySQL 绿色版（C:\mysql 原样搬，免安装——新电脑直接用）
  3. 数据库导出（mysqldump 全库含触发器/序列/视图）
  4. 上传图片（I:\uploads）
  5. 配置/密钥（I:\erp-server 下的 jwt_secret/ai_config/photo_config）
  6. Hermes Agent 本体 + 数据（config/skills/memories/sessions/cron）
  7. 一键部署脚本（install-erp.bat / install-hermes.bat / 部署说明.md）
- 用法：python scripts/pack_migration.py
"""
import os
import shutil
import subprocess
import sys
from datetime import datetime

MYSQL_BIN = r"C:\mysql\8.0.28\bin\mysql.exe"
MYSQLD = r"C:\mysql\8.0.28\bin\mysqldump.exe"
ERP_SRC = r"I:\erp-server"
ERP_CFG = r"I:\erp-server"
UPLOADS = r"I:\uploads"
MYSQL_DIR = r"C:\mysql"
HERMES_AGENT = r"C:\Users\Administrator\AppData\Local\hermes\hermes-agent"
HERMES_DATA = r"C:\Users\Administrator\AppData\Local\hermes"

STAMP = datetime.now().strftime("%Y%m%d")
DEST = r"H:\jxc-迁移包-%s" % STAMP
SKIP_DIRS = {"node_modules", "target", ".venv", "__pycache__", ".pytest_cache",
             "venv", "dist", "coverage", ".mypy_cache", ".ruff_cache"}
SKIP_FILES = {"*.pyc", ".DS_Store", "Thumbs.db"}


def csize(path):
    total = 0
    for root, _d, files in os.walk(path):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(root, f))
            except OSError:
                pass
    return total


def human(n):
    return "%.1f MB" % (n / 1024 / 1024)


def copytree_filtered(src, dst, skip=SKIP_DIRS):
    """复制目录树，跳过构建缓存目录"""
    os.makedirs(dst, exist_ok=True)
    n = 0
    for root, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in skip]
        rel = os.path.relpath(root, src)
        target = os.path.join(dst, rel) if rel != "." else dst
        os.makedirs(target, exist_ok=True)
        for f in files:
            if f.endswith((".pyc",)) or f in (".DS_Store", "Thumbs.db"):
                continue
            sf = os.path.join(root, f)
            df = os.path.join(target, f)
            try:
                shutil.copy2(sf, df)
                n += 1
            except OSError:
                pass
    return n


def main():
    print("== 进销存系统全量迁移打包 ==")
    print("目标目录: %s" % DEST)
    if os.path.exists(DEST):
        print("目标已存在，跳过。删除后重跑。")
        return
    os.makedirs(DEST)

    # ---------- 1. ERP 源码 + jar ----------
    print("\n[1/7] 打包 ERP 源码...")
    n = copytree_filtered(ERP_SRC, os.path.join(DEST, "01-ERP", "erp-server"))
    print("  文件 %d 个 (%s)" % (n, human(csize(os.path.join(DEST, "01-ERP", "erp-server")))))

    # jar 单独放（防止 target 被跳过）
    jar_src = os.path.join(ERP_SRC, "backend", "target", "erp-server-0.0.1-SNAPSHOT.jar")
    if os.path.exists(jar_src):
        jar_dst_dir = os.path.join(DEST, "01-ERP", "erp-server", "backend", "release")
        os.makedirs(jar_dst_dir, exist_ok=True)
        shutil.copy2(jar_src, os.path.join(jar_dst_dir, "erp-server-0.0.1-SNAPSHOT.jar"))
        print("  jar 已复制到 backend/release/")

    # ---------- 2. MySQL 绿色版 ----------
    print("[2/7] 打包 MySQL 绿色版...")
    n = copytree_filtered(MYSQL_DIR, os.path.join(DEST, "01-ERP", "mysql"),
                          SKIP_DIRS | {"data"})  # data 不含（全新初始化）
    print("  文件 %d 个 (%s)（data 目录跳过，新电脑初始化）" % (
        n, human(csize(os.path.join(DEST, "01-ERP", "mysql")))))

    # ---------- 3. 数据库导出 ----------
    print("[3/7] 导出数据库...")
    db_dir = os.path.join(DEST, "01-ERP", "database")
    os.makedirs(db_dir, exist_ok=True)
    sql_path = os.path.join(db_dir, "jxc_erp_full.sql")
    p = subprocess.run([MYSQLD, "-uroot", "--routines", "--triggers",
                        "--single-transaction", "--databases", "jxc_erp"],
                       capture_output=True, timeout=300)
    if p.returncode != 0:
        print("  mysqldump 失败: %s" % p.stderr.decode("gbk", errors="replace")[:200])
    else:
        with open(sql_path, "wb") as f:
            f.write(p.stdout)
        print("  已导出 %s" % human(os.path.getsize(sql_path)))

    # ---------- 4. 上传图片 ----------
    print("[4/7] 打包上传图片...")
    n = copytree_filtered(UPLOADS, os.path.join(DEST, "01-ERP", "uploads"),
                          SKIP_DIRS - {"target"})
    print("  文件 %d 个 (%s)" % (n, human(csize(os.path.join(DEST, "01-ERP", "uploads")))))

    # ---------- 5. 配置/密钥 ----------
    print("[5/7] 打包配置/密钥...")
    cfg_dst = os.path.join(DEST, "01-ERP", "config")
    os.makedirs(cfg_dst, exist_ok=True)
    for f in os.listdir(ERP_CFG):
        fp = os.path.join(ERP_CFG, f)
        if os.path.isfile(fp) and f.endswith((".txt", ".json")):
            shutil.copy2(fp, os.path.join(cfg_dst, f))
            print("  %s" % f)

    # ---------- 6. Hermes Agent 本体 + 数据 ----------
    print("[6/7] 打包 Hermes Agent...")
    # 本体（跳过 .git 省 1.1GB——git 历史不是运行必需；**venv 必须保留**——hermes.exe 在
    # venv\Scripts 里，跳过了新电脑就没法直接跑。node_modules 是可再生的构建依赖，跳过）
    hermes_skip = SKIP_DIRS - {"venv", ".venv"} | {".git", ".github", "tests", "tests-js",
                                                   "website", "web", "node_modules"}
    n = copytree_filtered(HERMES_AGENT, os.path.join(DEST, "02-Hermes", "hermes-agent"),
                          hermes_skip)
    print("  本体 %d 个文件 (%s)（跳过 .git/node_modules 可再生部分）" % (
        n, human(csize(os.path.join(DEST, "02-Hermes", "hermes-agent")))))

    # 数据（config/skills/memories/sessions/cron/logs 选带）
    hdata_dst = os.path.join(DEST, "02-Hermes", "hermes-data")
    os.makedirs(hdata_dst, exist_ok=True)
    for name in ["config.yaml", ".env", "skills", "memories", "sessions", "cron",
                 "profiles", "plugins", "kanban.db", "state.db"]:
        sp = os.path.join(HERMES_DATA, name)
        if os.path.isdir(sp):
            n = copytree_filtered(sp, os.path.join(hdata_dst, name), SKIP_DIRS | {"cache", "logs", "lsp"})
            print("  数据 %s: %d 文件 (%s)" % (name, n, human(csize(os.path.join(hdata_dst, name)))))
        elif os.path.isfile(sp):
            shutil.copy2(sp, os.path.join(hdata_dst, name))
            print("  数据 %s: %s" % (name, human(os.path.getsize(sp))))

    # ---------- 7. 一键部署脚本 + 说明 ----------
    print("[7/7] 生成部署脚本...")
    script_dir = os.path.join(DEST, "03-部署")
    os.makedirs(script_dir, exist_ok=True)
    with open(os.path.join(script_dir, "install-erp.bat"), "w", encoding="gbk") as f:
        f.write(INSTALL_ERP_BAT)
    with open(os.path.join(script_dir, "install-hermes.bat"), "w", encoding="gbk") as f:
        f.write(INSTALL_HERMES_BAT)
    with open(os.path.join(script_dir, "部署说明.md"), "w", encoding="utf-8") as f:
        f.write(DEPLOY_README)

    total = csize(DEST)
    print("\n===== 打包完成 =====")
    print("目标: %s (%s)" % (DEST, human(total)))
    print("结构:")
    for root, dirs, files in os.walk(DEST):
        level = root.replace(DEST, "").count(os.sep)
        if level <= 2:
            print("  " + "  " * level + os.path.basename(root) + "/")


INSTALL_ERP_BAT = """@echo off
rem ============ 进销存 ERP 一键部署（新电脑）============
rem 前提：把整个迁移包放到 D:\\jxc（或任意盘根目录）
set ROOT=%~dp0..
set MYSQL=%ROOT%\\01-ERP\\mysql\\8.0.28\\bin\\mysqld.exe
set MYSQL_INI=%ROOT%\\01-ERP\\mysql\\my.ini

echo [1/4] 初始化 MySQL 数据目录...
if not exist "%ROOT%\\01-ERP\\mysql\\data" (
  "%ROOT%\\01-ERP\\mysql\\8.0.28\\bin\\mysqld.exe" --initialize-insecure --basedir="%ROOT%\\01-ERP\\mysql\\8.0.28" --datadir="%ROOT%\\01-ERP\\mysql\\data" --defaults-file="%MYSQL_INI%"
)

echo [2/4] 启动 MySQL...
start "" /b "%MYSQL%" --defaults-file="%MYSQL_INI%"
ping -n 6 127.0.0.1 >nul

echo [3/4] 导入数据库...
"%ROOT%\\01-ERP\\mysql\\8.0.28\\bin\\mysql.exe" -uroot < "%ROOT%\\01-ERP\\database\\jxc_erp_full.sql"

echo [4/4] 启动 ERP 后端（8080）...
start "" /b java -jar "%ROOT%\\01-ERP\\erp-server\\backend\\release\\erp-server-0.0.1-SNAPSHOT.jar"

echo 部署完成！访问 http://localhost:8080 （局域网用本机IP:8080）
pause
"""

INSTALL_HERMES_BAT = """@echo off
rem ============ Hermes Agent 迁移到新电脑 ============
rem 前提：新电脑已装 Python 3.11+ 和 Node 18+
set ROOT=%~dp0..
set HERMES_AGENT_SRC=%ROOT%\\02-Hermes\\hermes-agent
set HERMES_DATA_SRC=%ROOT%\\02-Hermes\\hermes-data
set HERMES_HOME=%USERPROFILE%\\AppData\\Local\\hermes

echo [1/3] 复制 Hermes 本体...
xcopy "%HERMES_AGENT_SRC%" "%HERMES_HOME%\\hermes-agent\\" /E /I /Q /Y

echo [2/3] 复制数据（配置/技能/记忆/会话）...
xcopy "%HERMES_DATA_SRC%" "%HERMES_HOME%\\" /E /I /Q /Y

echo [3/3] 注册 hermes 命令...
set PATH=%HERMES_HOME%\\hermes-agent\\venv\\Scripts;%PATH%
echo PATH 已追加（永久生效需手动加系统环境变量）

echo 完成！新终端运行 hermes 即可。
pause
"""

DEPLOY_README = """# 进销存系统迁移部署说明（2026-08-01 打包）

## 包内容
- 01-ERP/：ERP 系统（源码 + jar + MySQL 绿色版 + 数据库 + 图片 + 配置）
- 02-Hermes/：Hermes Agent（本体 + 数据）
- 03-部署/：一键部署脚本

## 新电脑部署 ERP（10 分钟）
### 前提：装 Java 21
1. 把整个迁移包复制到新电脑（建议 D:\\jxc）
2. 双击 03-部署\\install-erp.bat
3. 自动完成：初始化 MySQL → 启动 → 导入数据 → 启动后端
4. 浏览器访问 http://localhost:8080 （admin/admin123）
5. 局域网其他电脑访问 http://<新电脑IP>:8080

### 手工部署（脚本失败时）
```
# 1. 初始化 MySQL（绿色版，免安装）
mysql\\8.0.28\\bin\\mysqld.exe --initialize-insecure --basedir=mysql\\8.0.28 --datadir=mysql\\data
# 2. 启动（my.ini 需改 basedir/datadir 路径为实际位置）
mysql\\8.0.28\\bin\\mysqld.exe --defaults-file=mysql\\my.ini
# 3. 导入数据
mysql\\8.0.28\\bin\\mysql.exe -uroot < database\\jxc_erp_full.sql
# 4. 启动后端
java -jar erp-server\\backend\\release\\erp-server-0.0.1-SNAPSHOT.jar
```

## 新电脑部署 Hermes
1. 双击 03-部署\\install-hermes.bat
2. 或手工：复制 02-Hermes\\hermes-agent → %USERPROFILE%\\AppData\\Local\\hermes\\hermes-agent
3. 复制 02-Hermes\\hermes-data → %USERPROFILE%\\AppData\\Local\\hermes\\（覆盖）
4. 把 venv\\Scripts 加入 PATH，运行 hermes

## 注意
- my.ini 里 basedir/datadir 是 C:\\mysql 路径——**新电脑路径变了要改**
- jwt_secret.txt 已随迁（JWT 旧 token 仍有效）
- ai_config.json 已随迁（AI 报价/拍照识别继续可用）
- 数据库是 mysqldump 全量（含触发器/序列/视图）
"""


if __name__ == "__main__":
    main()
