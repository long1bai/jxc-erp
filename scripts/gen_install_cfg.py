# -*- coding: utf-8 -*-
"""
进销存系统 install.bat 辅助脚本（2026-08-04）
====================================================
用途：install.bat 调用，生成外部 application.yml + 修正 my.ini 路径。
独立成 .py 文件，避免 bat 内嵌 python 三引号/转义地狱。

用法（由 install.bat 调用）：
    python gen_install_cfg.py <ERP_ROOT> <PORT> <MYSQL_PORT> <DB_PASS>
    ERP_ROOT   : 01-ERP 绝对路径（install.bat 传 %~dp0）
    PORT       : Web 端口（默认 8080）
    MYSQL_PORT : MySQL 端口（默认 3306）
    DB_PASS    : 数据库密码（可为空）
"""
import os
import re
import sys


def fix_my_ini(mysql_dir: str, mysql_port: str) -> bool:
    """修正 my.ini 的 basedir/datadir/log-error 为实际路径，端口替换。"""
    ini = os.path.join(mysql_dir, "my.ini")
    if not os.path.exists(ini):
        return False
    with open(ini, "r", encoding="gbk", errors="replace") as f:
        s = f.read()
    root = os.path.abspath(mysql_dir).replace(os.sep, "/")
    s = s.replace("basedir=C:/mysql/8.0.28", "basedir=" + root + "/8.0.28")
    s = s.replace("datadir=C:/mysql/data", "datadir=" + root + "/data")
    s = s.replace("log-error=C:/mysql/logs/error.log",
                  "log-error=" + root + "/logs/error.log")
    s = re.sub(r"port=\d+", "port=" + str(mysql_port), s)
    with open(ini, "w", encoding="gbk", newline="") as f:
        f.write(s)
    return True


def gen_application_yml(erp_root: str, port: str, mysql_port: str, db_pass: str) -> bool:
    """生成 backend/release/config/application.yml，路径指向当前部署目录。"""
    root = os.path.abspath(erp_root).replace(os.sep, "/")
    cfg = (
        "server:\n"
        "  port: ${ERP_PORT:%s}\n"
        "  servlet:\n"
        "    encoding:\n"
        "      charset: UTF-8\n"
        "      force: true\n"
        "\n"
        "app:\n"
        "  upload-dir: ${ERP_UPLOAD_DIR:%s/uploads}\n"
        "  backup-dir: ${ERP_BACKUP_DIR:%s/erp-server/backup}\n"
        "  mysqldump-path: ${ERP_MYSQLDUMP:%s/mysql/8.0.28/bin/mysqldump.exe}\n"
        "  ai-config: ${ERP_AI_CONFIG:%s/config/ai_config.json}\n"
        "  photo-config: ${ERP_PHOTO_CONFIG:%s/config/photo_config.json}\n"
        "  jwt-secret-file: ${ERP_JWT_SECRET:%s/config/jwt_secret.txt}\n"
        "  jwt-expire-days: ${ERP_JWT_EXPIRE_DAYS:7}\n"
        "  legacy-web-public: ${ERP_LEGACY_WEB_PUBLIC:%s/erp-server/web/public}\n"
        "\n"
        "spring:\n"
        "  datasource:\n"
        "    url: ${ERP_DB_URL:jdbc:mysql://127.0.0.1:%s/jxc_erp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}\n"
        "    username: ${ERP_DB_USER:root}\n"
        "    password: ${ERP_DB_PASSWORD:%s}\n"
        "    driver-class-name: com.mysql.cj.jdbc.Driver\n"
        "  servlet:\n"
        "    multipart:\n"
        "      max-file-size: 15MB\n"
        "      max-request-size: 30MB\n"
        "\n"
        "mybatis-plus:\n"
        "  configuration:\n"
        "    map-underscore-to-camel-case: true\n"
        "    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n"
        "  global-config:\n"
        "    banner: false\n"
        "    db-config:\n"
        "      logic-delete-field: deleted\n"
        "      logic-delete-value: 1\n"
        "      logic-not-delete-value: 0\n"
    ) % (port, root, root, root, root, root, root, root, mysql_port, db_pass)

    out_dir = os.path.join(erp_root, "erp-server", "backend", "release", "config")
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "application.yml"), "w", encoding="utf-8", newline="") as f:
        f.write(cfg)
    return True


def main():
    if len(sys.argv) < 2:
        print("用法: python gen_install_cfg.py <ERP_ROOT> [PORT] [MYSQL_PORT] [DB_PASS]")
        return 2
    erp_root = sys.argv[1].rstrip("\\/")
    port = sys.argv[2] if len(sys.argv) > 2 else "8080"
    mysql_port = sys.argv[3] if len(sys.argv) > 3 else "3306"
    db_pass = sys.argv[4] if len(sys.argv) > 4 else ""

    ok_ini = fix_my_ini(os.path.join(erp_root, "mysql"), mysql_port)
    ok_yml = gen_application_yml(erp_root, port, mysql_port, db_pass)
    print("my.ini 修正: %s" % ("OK" if ok_ini else "SKIP(不存在)"))
    print("application.yml 生成: %s" % ("OK" if ok_yml else "FAIL"))
    return 0 if (ok_ini or ok_yml) else 1


if __name__ == "__main__":
    sys.exit(main())
