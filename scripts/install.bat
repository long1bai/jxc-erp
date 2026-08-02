@echo off
rem ============================================================
rem jxc进销存 v2 - 一键安装部署脚本（买家使用）
rem 功能: 1) 初始化绿色 MySQL(如未初始化) 2) 修正 my.ini 路径
rem       3) 启动 MySQL 4) 导入数据库(已存在跳过) 5) 生成/更新外部配置
rem       6) 启动后端(8080)
rem 可选环境变量(买家自定义, 不设用默认):
rem   ERP_PORT        - Web 端口(默认 8080)
rem   ERP_DB_PASSWORD - 数据库 root 密码(默认空, 生产建议设置)
rem   ERP_MYSQL_PORT  - MySQL 端口(默认 3306)
rem 用法: 双击本脚本, 或命令行执行 install.bat
rem ============================================================
setlocal enabledelayedexpansion
chcp 65001 >nul

rem ---- 定位根目录(本脚本在 <包>/03-部署/ 或 <包>/scripts/) ----
set ROOT=%~dp0..
if exist "%ROOT%\01-ERP" (set ERP_ROOT=%ROOT%\01-ERP) else (set ERP_ROOT=%ROOT%)
if exist "%ROOT%\yawei-erp-java" (set ERP_ROOT=%ROOT%)

set MYSQL_DIR=%ERP_ROOT%\mysql
set MYSQL_BIN=%MYSQL_DIR%\8.0.28\bin
set MYSQL_INI=%MYSQL_DIR%\my.ini
set DB_FILE=%ERP_ROOT%\database\yawei_erp_full.sql

rem ---- 环境变量(带默认) ----
if "%ERP_PORT%"=="" set ERP_PORT=8080
if "%ERP_MYSQL_PORT%"=="" set ERP_MYSQL_PORT=3306
set DB_PASS=%ERP_DB_PASSWORD%

echo ============================================
echo  jxc进销存 v2 - 安装部署
echo  ERP目录: %ERP_ROOT%
echo  Web端口: %ERP_PORT%   MySQL端口: %ERP_MYSQL_PORT%
echo ============================================

rem ---- [1/6] 修正 my.ini 路径 + 端口 ----
echo [1/6] 修正 MySQL 配置...
if exist "%MYSQL_INI%" (
  python -c "import io,os;p=r'%MYSQL_INI%';s=open(p,encoding='gbk',errors='replace').read();root=os.path.abspath(r'%MYSQL_DIR%').replace('/','\\\\');s=s.replace('basedir=C:/mysql/8.0.28','basedir='+root+'\\\\8.0.28').replace('datadir=C:/mysql/data','datadir='+root+'\\\\data').replace('log-error=C:/mysql/logs/error.log','log-error='+root+'\\\\logs\\\\error.log');import re;s=re.sub(r'port=\d+','port=%ERP_MYSQL_PORT%',s);open(p,'w',encoding='gbk').write(s)" 2>nul && echo    my.ini 已修正 || echo    python 不可用,请手动改 my.ini
)

rem ---- [2/6] 初始化 MySQL 数据目录(首次) ----
echo [2/6] 初始化 MySQL 数据目录...
if not exist "%MYSQL_DIR%\data" (
  "%MYSQL_BIN%\mysqld.exe" --initialize-insecure --basedir="%MYSQL_BIN%" --datadir="%MYSQL_DIR%\data" --defaults-file="%MYSQL_INI%" 2>nul
  if errorlevel 1 (echo    初始化失败,请检查 my.ini) else (echo    初始化完成)
) else (
  echo    已存在,跳过
)

rem ---- [3/6] 启动 MySQL ----
echo [3/6] 启动 MySQL...
"%MYSQL_BIN%\mysqld.exe" --defaults-file="%MYSQL_INI%" >nul 2>&1
echo    等待启动...
ping -n 8 127.0.0.1 >nul

rem ---- [3.5] 设置 root 密码(如配置了) ----
if not "%DB_PASS%"=="" (
  echo [3.5] 设置数据库密码...
  "%MYSQL_BIN%\mysql.exe" -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '%DB_PASS%'; FLUSH PRIVILEGES;" 2>nul && echo    密码已设置
)

rem ---- [4/6] 导入数据库(已存在跳过) ----
echo [4/6] 导入数据库...
"%MYSQL_BIN%\mysql.exe" -uroot %DB_PASS_ARG% -e "SHOW DATABASES LIKE 'yawei_erp'" | findstr yawei_erp >nul 2>&1
if errorlevel 1 (
  "%MYSQL_BIN%\mysql.exe" -uroot %DB_PASS_ARG% < "%DB_FILE%"
  echo    数据库导入完成
) else (
  echo    数据库已存在,跳过
)

rem ---- [5/6] 生成外部配置(路径指向当前目录, 端口/密码用环境变量) ----
echo [5/6] 生成外部配置...
python -c "import os;root=os.path.abspath(r'%ERP_ROOT%').replace(chr(92),'/');cfg='server:\n  port: ${ERP_PORT:'+str(%ERP_PORT%)+r'''}
app:
  upload-dir: %s/yawei-uploads
  backup-dir: %s/yawei-erp-java/backup
  mysqldump-path: %s/mysql/8.0.28/bin/mysqldump.exe
  ai-config: %s/yawei-erp-config/ai_config.json
  photo-config: %s/yawei-erp-config/photo_config.json
  jwt-secret-file: %s/yawei-erp-config/jwt_secret.txt
  jwt-expire-days: 7
  legacy-web-public: %s/yawei-erp-java/web/public
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:%s/yawei_erp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: "%s"
    driver-class-name: com.mysql.cj.jdbc.Driver
  servlet:
    multipart:
      max-file-size: 15MB
      max-request-size: 30MB
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: false
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
''' % (root,root,root,root,root,root,root,str(%ERP_MYSQL_PORT%),'%DB_PASS%');os.makedirs(r'%ERP_ROOT%\yawei-erp-java\backend\release\config',exist_ok=True);open(r'%ERP_ROOT%\yawei-erp-java\backend\release\config\application.yml','w',encoding='utf-8').write(cfg)" 2>nul && echo    配置已生成 || echo    python 不可用,请手动复制 application.example.yml

rem ---- [6/6] 启动后端 ----
echo [6/6] 启动后端 (http://localhost:%ERP_PORT%)...
start "" /b java -jar "%ERP_ROOT%\yawei-erp-java\backend\release\yawei-erp-0.0.1-SNAPSHOT.jar" --server.port=%ERP_PORT%

echo.
echo ============================================
echo  部署完成!
echo  访问: http://localhost:%ERP_PORT%
echo  默认账号: admin / admin123
echo  局域网访问: http://<本机IP>:%ERP_PORT%
echo  修改公司名/系统名: 数据库 sys_config 表
echo  修改 AI 配置: yawei-erp-config\ai_config.json
echo ============================================
pause
