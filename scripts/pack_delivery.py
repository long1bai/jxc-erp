# -*- coding: utf-8 -*-
"""
进销存系统 交付包组装脚本（2026-08-04）
====================================================
作用：从开发目录生成一份"可交付给买家"的干净部署包。
核心：剔除一切与开发环境/公司机密相关的内容，只留可部署的绿色包。

用法：
    python scripts/pack_delivery.py [目标目录]
    默认输出到 H:\jxc-delivery-<日期>\ （可用参数指定）

输出结构（买家解压即用）：
    <包>/
      01-ERP/
        erp-server/          # 后端源码 + release jar + 前端源码
        mysql/               # 绿色 MySQL
        database/            # jxc_erp_clean.sql（干净库，无业务数据）
        config/              # 空配置模板（买家自己填 API Key / 密码）
        uploads/             # 空目录（运行后放图片）
        install.bat          # 一键安装
        README-买家版.md      # 部署说明

剔除项（敏感/机密）：
    - uploads/ 真实照片（189MB）
    - erp-server/backup/ 数据库备份（552MB，含历史数据）
    - erp-server/.git/ 版本历史（含旧密钥/路径）
    - web/node_modules/ 依赖（买家 npm install 或直接用 dist）
    - backend/target/ 编译中间产物（保留 release jar）
    - config/ 里的真实 jwt_secret / ai_config(含 key) / cookie / db_secret
    - scripts/ 内部开发脚本（dr_* / e2e / seed / pack_migration / download_mysql）
    - docs/ 内部开发文档 + 测试报告
    - 日志文件

注意事项：
    - install.bat 里 DB_FILE 指向 database/jxc_erp_clean.sql
    - 买家装完：改公司名(sys_config)、填 AI Key(ai_config.json)、改 admin 密码
    - 本脚本只组装，不修改源目录
"""
import os
import shutil
import sys
from datetime import datetime

# ---------------- 路径 ----------------
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # erp-server/
ERP_TOP = os.path.dirname(ROOT)                                       # 01-ERP/
STAMP = datetime.now().strftime("%Y%m%d")
DEST = sys.argv[1] if len(sys.argv) > 1 else os.path.join("H:", os.sep, "jxc-delivery-%s" % STAMP)

# ---------------- 剔除清单 ----------------
SKIP_DIRS = {
    "node_modules", "target", ".git", ".mvn", "__pycache__", ".idea", ".vscode",
    "backup", "dist", "data", "logs", "uploads",  # dist 保留? 不,前端已 build 进 jar
}
SKIP_FILES = {
    "*.log", "*.pyc", ".DS_Store", "Thumbs.db", "*.tmp", "*.original",
    "cookie.txt", "jwt_secret.txt", "db_secret.env",
    "ai_config.json", "photo_config.json", "application.yml",  # 配置文件(含密钥/路径)
}
# 内部脚本不随包
SKIP_SCRIPTS = {
    "dr_backup.py", "dr_daemon.py", "dr_restore_drill.py", "e2e_test.py",
    "seed_real_data.py", "stress_test.py", "test_photo_flow.py",
    "pack_migration.py", "download_mysql.py", "register-autostart.bat",
    "startup-all.bat", "startup-erp.vbs",
}
# 内部文档不随包
SKIP_DOCS = {"docs", "test-reports", "AGENTS.md"}


def should_skip_dir(name, rel):
    if name in SKIP_DIRS:
        return True
    return False


def should_skip_file(name, rel):
    for pat in SKIP_FILES:
        if pat.startswith("*"):
            if name.endswith(pat[1:]):
                return True
        elif name == pat:
            return True
    return False


def main():
    dest = os.path.abspath(DEST)
    if os.path.exists(dest):
        shutil.rmtree(dest)
    os.makedirs(dest)

    # 1. erp-server（剔除敏感目录/文件/脚本/文档）
    src_server = ROOT
    dst_server = os.path.join(dest, "01-ERP", "erp-server")
    os.makedirs(dst_server, exist_ok=True)
    for item in os.listdir(src_server):
        s = os.path.join(src_server, item)
        if os.path.isdir(s):
            if item in SKIP_DIRS or item in SKIP_DOCS:
                print("  跳过目录:", item)
                continue
            shutil.copytree(s, os.path.join(dst_server, item),
                            ignore=shutil.ignore_patterns(*SKIP_DIRS, *[p[1:] for p in SKIP_FILES if p.startswith("*")]))
        else:
            if item in SKIP_FILES or item in SKIP_DOCS or item in SKIP_SCRIPTS:
                print("  跳过文件:", item)
                continue
            shutil.copy2(s, os.path.join(dst_server, item))
    # scripts 只保留 install.bat + gen_install_cfg.py
    sdir = os.path.join(dst_server, "scripts")
    if os.path.isdir(sdir):
        for f in os.listdir(sdir):
            if f not in ("install.bat", "gen_install_cfg.py"):
                os.remove(os.path.join(sdir, f))
    # backend/release 保留 jar + example，删 application.yml（含真实路径）
    rdir = os.path.join(dst_server, "backend", "release")
    if os.path.isdir(rdir):
        for f in os.listdir(rdir):
            if f == "application.yml":
                os.remove(os.path.join(rdir, f))

    # 2. mysql（绿色版，剔除 data/logs——买家初始化）
    src_mysql = os.path.join(ERP_TOP, "mysql")
    dst_mysql = os.path.join(dest, "01-ERP", "mysql")
    if os.path.isdir(src_mysql):
        shutil.copytree(src_mysql, dst_mysql,
                        ignore=shutil.ignore_patterns("data", "logs", "*.log"))
        # 生成中性 my.ini（install.bat 首次运行会 replace 成实际路径）
        neutral_ini = "[mysqld]\r\nbasedir=C:/mysql/8.0.28\r\ndatadir=C:/mysql/data\r\nport=3306\r\ncharacter-set-server=utf8mb4\r\ncollation-server=utf8mb4_unicode_ci\r\ndefault-time-zone=+08:00\r\nmax_connections=200\r\n# 日志\r\nlog-error=C:/mysql/logs/error.log\r\n[client]\r\ndefault-character-set=utf8mb4\r\n"
        with open(os.path.join(dst_mysql, "my.ini"), "w", encoding="gbk", newline="") as f:
            f.write(neutral_ini)
        print("  mysql 已复制（剔除 data/logs，my.ini 已中性化）")

    # 3. database（只带干净库）
    src_db = os.path.join(ERP_TOP, "database", "jxc_erp_clean.sql")
    dst_db = os.path.join(dest, "01-ERP", "database")
    os.makedirs(dst_db, exist_ok=True)
    if os.path.exists(src_db):
        shutil.copy2(src_db, os.path.join(dst_db, "jxc_erp_clean.sql"))
        print("  干净库已复制")

    # 4. config（只带模板）
    src_cfg = os.path.join(ERP_TOP, "config")
    dst_cfg = os.path.join(dest, "01-ERP", "config")
    os.makedirs(dst_cfg, exist_ok=True)
    if os.path.isdir(src_cfg):
        for f in os.listdir(src_cfg):
            if f in ("cookie.txt", "jwt_secret.txt", "db_secret.env", ".last_backup.json"):
                continue
            shutil.copy2(os.path.join(src_cfg, f), os.path.join(dst_cfg, f))
        print("  配置模板已复制（剔除密钥）")

    # 5. uploads 空目录
    os.makedirs(os.path.join(dest, "01-ERP", "uploads"), exist_ok=True)

    # 6. install.bat 复制到包根
    shutil.copy2(os.path.join(src_server, "scripts", "install.bat"),
                 os.path.join(dest, "01-ERP", "install.bat"))
    print("  install.bat 已复制")

    # 7. 买家版 README
    readme = os.path.join(dest, "01-ERP", "README-买家版.md")
    with open(readme, "w", encoding="utf-8") as f:
        f.write(README)
    print("  README-买家版.md 已生成")

    # 汇总
    total = 0
    for dirpath, _d, files in os.walk(dest):
        for fn in files:
            total += os.path.getsize(os.path.join(dirpath, fn))
    print("\n交付包完成: %s" % dest)
    print("大小: %.1f MB" % (total / 1024 / 1024))


README = """# 进销存系统 - 部署说明

## 一、环境要求
- Windows 7/10/11（64位）
- 已安装 JDK 17+（推荐 JDK 21）

## 二、一键安装
1. 双击 `install.bat`
2. 等待脚本完成（首次会初始化 MySQL、导入数据库、启动服务）
3. 浏览器访问 `http://localhost:8080`
4. 默认账号：`admin` / `admin123`（**首次登录后请立即修改**）

## 三、常用配置
### 1. 公司名/系统名
登录后：系统 → 系统配置 → 公司信息
（打印抬头、登录页标题都从这里改）

### 2. AI 报价助手
编辑 `config/ai_config.json`：
```json
{
  "api_url": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
  "api_key": "你的阿里云DashScope API Key",
  "chat_model": "qwen-plus",
  "vision_model": "qwen3-vl-plus",
  "system_prompt": "你是{company_name}的报价助手。..."
}
```
保存后重启服务生效。

### 3. 数据库
- 库名：`jxc_erp`（MySQL 8.0.28，绿色版）
- root 密码：见 `config/db_secret.env`（安装后生成，可改；不入版本库）

### 4. 端口
默认 8080。修改方式：命令行运行 `install.bat` 前设环境变量
`set ERP_PORT=9090` 再执行。

## 四、备份与恢复
- 登录系统 → 数据备份 → 立即备份（生成 .sql 文件）
- 备份文件在服务器 `erp-server/backup/` 目录
- 恢复：数据备份页选文件 → 恢复

## 五、升级
覆盖安装：停止服务 → 用新包替换 erp-server → 运行 install.bat（数据库已存在会跳过导入）

## 六、常见问题
| 问题 | 解决 |
|---|---|
| 端口被占用 | 改 ERP_PORT 环境变量 |
| 忘记 admin 密码 | 停止服务，mysql 执行 `UPDATE jxc_erp.users SET password_hash=...` 或用旧备份恢复 |
| 中文乱码 | 确认浏览器编码 UTF-8 |

---
*本系统为工厂进销存一体化管理软件。*
"""

if __name__ == "__main__":
    main()
