@echo off
rem ============================================================
rem 进销存系统 v2 - 一键安装部署脚本（买家使用）
rem 功能: 1) 修正 my.ini 路径 2) 初始化绿色 MySQL(如未初始化)
rem       3) 启动 MySQL 4) 导入数据库(已存在跳过) 5) 生成外部配置
rem       6) 启动后端(8080)
rem 可选环境变量(买家自定义, 不设用默认):
rem   ERP_PORT        - Web 端口(默认 8080)
rem   ERP_DB_PASSWORD - 数据库 root 密码(默认空, 生产建议设置)
rem   ERP_MYSQL_PORT  - MySQL 端口(默认 3306)
rem 用法: 双击本脚本, 或命令行执行 install.bat
rem ============================================================
setlocal enabledelayedexpansion
chcp 65001 >nul

rem ---- 定位根目录: 本脚本位于 <包>/01-ERP/install.bat, 自身目录即 ERP_ROOT ----
set ERP_ROOT=%~dp0
set MYSQL_DIR=%ERP_ROOT%mysql
set MYSQL_BIN=%MYSQL_DIR%\8.0.28\bin
set MYSQL_INI=%MYSQL_DIR%\my.ini
set DB_FILE=%ERP_ROOT%database\jxc_erp_clean.sql
set CFG_PY=%ERP_ROOT%erp-server\scripts\gen_install_cfg.py

rem ---- 环境变量(带默认) ----
if "%ERP_PORT%"=="" set ERP_PORT=8080
if "%ERP_MYSQL_PORT%"=="" set ERP_MYSQL_PORT=3306
set DB_PASS=%ERP_DB_PASSWORD%

echo ============================================
echo  进销存系统 v2 - 安装部署
echo  ERP目录: %ERP_ROOT%
echo  Web端口: %ERP_PORT%   MySQL端口: %ERP_MYSQL_PORT%
echo ============================================

rem ---- [1/6] 修正 my.ini 路径 + 端口 ----
echo [1/6] 修正 MySQL 配置...
if exist "%MYSQL_INI%" (
  rem 用 bat 原生字符串替换（%var:old=new%），不依赖 python
  set INI_BAK=%MYSQL_INI%.bak
  copy /y "%MYSQL_INI%" "%INI_BAK%" >nul
  rem 替换 basedir/datadir/log-error 为实际路径（正斜杠）
  powershell -NoProfile -Command "$p='%MYSQL_INI%';$s=Get-Content $p -Raw -Encoding Default;$r='%MYSQL_DIR%'.Replace('\','/');$s=$s.Replace('basedir=C:/mysql/8.0.28','basedir='+$r+'/8.0.28').Replace('datadir=C:/mysql/data','datadir='+$r+'/data').Replace('log-error=C:/mysql/logs/error.log','log-error='+$r+'/logs/error.log');$s=$s -replace 'port=\d+','port=%ERP_MYSQL_PORT%';[IO.File]::WriteAllText($p,$s,[Text.Encoding]::GetEncoding(936))" >nul 2>&1
  if errorlevel 1 (
    echo    my.ini 路径修正失败, 请手动编辑 %MYSQL_INI%
  ) else (
    echo    my.ini 已修正
  )
)

rem ---- [2/6] 初始化 MySQL 数据目录(首次) ----
echo [2/6] 初始化 MySQL 数据目录...
if not exist "%MYSQL_DIR%\data" (
  "%MYSQL_BIN%\mysqld.exe" --initialize-insecure --basedir="%MYSQL_DIR%\8.0.28" --datadir="%MYSQL_DIR%\data" --defaults-file="%MYSQL_INI%" >nul 2>&1
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
set DB_PASS_ARG=
if not "%DB_PASS%"=="" (
  echo [3.5] 设置数据库密码...
  "%MYSQL_BIN%\mysql.exe" -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '%DB_PASS%'; FLUSH PRIVILEGES;" >nul 2>&1 && echo    密码已设置
  set DB_PASS_ARG=-p"%DB_PASS%"
)

rem ---- [4/6] 导入数据库(已存在跳过) ----
echo [4/6] 导入数据库...
"%MYSQL_BIN%\mysql.exe" -uroot %DB_PASS_ARG% -e "SHOW DATABASES LIKE 'jxc_erp'" | findstr jxc_erp >nul 2>&1
if errorlevel 1 (
  "%MYSQL_BIN%\mysql.exe" -uroot %DB_PASS_ARG% < "%DB_FILE%"
  echo    数据库导入完成
) else (
  echo    数据库已存在,跳过
)

rem ---- [5/6] 生成外部配置(路径指向当前目录) ----
echo [5/6] 生成外部配置...
python "%CFG_PY%" "%ERP_ROOT%" %ERP_PORT% %ERP_MYSQL_PORT% %DB_PASS% >nul 2>&1
if errorlevel 1 (
  echo    python 不可用, 请手动复制 erp-server\backend\release\config\application.example.yml
) else (
  echo    配置已生成
)

rem ---- [6/6] 启动后端 ----
echo [6/6] 启动后端 (http://localhost:%ERP_PORT%)...
start "" /b java -jar "%ERP_ROOT%erp-server\backend\release\erp-server-1.0.0.jar" --server.port=%ERP_PORT%

echo.
echo ============================================
echo  部署完成!
echo  访问: http://localhost:%ERP_PORT%
echo  默认账号: admin / admin123
echo  局域网访问: http://<本机IP>:%ERP_PORT%
echo  修改公司名/系统名: 登录后 系统→系统配置
echo  修改 AI 配置: config\ai_config.json
echo ============================================
pause
