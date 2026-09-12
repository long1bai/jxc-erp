@echo off
rem ============================================================
rem Jxc ERP boot-time autostart (2026-08-01)
rem Invoked by scheduled task JxcERP_AutoStart at system startup
rem (SYSTEM account, no logon needed). Also callable manually.
rem Idempotent: services already listening are not restarted.
rem Order: MySQL -> backend jar -> backup daemon (PID-lock inside).
rem ASCII-only on purpose: cmd.exe parses GBK, UTF-8 comments break.
rem ============================================================
setlocal

rem ---------- absolute paths (SYSTEM PATH differs from user) ----------
set MYSQLD=C:\Users\17815\Desktop\jxc\01-ERP\mysql\8.0.28\bin\mysqld.exe
set MYSQL_INI=C:/Users/17815/Desktop/jxc/01-ERP/mysql/my.ini
set JAVA_EXE=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
if not exist "%JAVA_EXE%" set JAVA_EXE=java
set JAR=C:\Users\17815\Desktop\jxc\01-ERP\erp-server\backend\release\erp-server-1.0.0.jar
set CONFIG_LOC=file:C:/Users/17815/Desktop/jxc/01-ERP/erp-server/backend/release/config/application.yml
set PY=C:\Users\17815\AppData\Local\Programs\Python\Python314\python.exe
set DAEMON=C:\Users\17815\Desktop\jxc\01-ERP\erp-server\scripts\dr_daemon.py
set LOG=C:\Users\17815\Desktop\jxc\01-ERP\erp-server\scripts\autostart.log

echo [%date% %time%] autostart begin >> "%LOG%"

rem ---------- 1. MySQL ----------
netstat -ano | findstr /c:":3306 " | findstr LISTENING >nul 2>&1
if errorlevel 1 (
    start "" /b "%MYSQLD%" --defaults-file="%MYSQL_INI%"
    echo   MySQL: starting >> "%LOG%"
    ping -n 4 127.0.0.1 >nul
) else (
    echo   MySQL: already running >> "%LOG%"
)

rem ---------- 2. backend jar ----------
netstat -ano | findstr /c:":8080" | findstr LISTENING >nul 2>&1
if errorlevel 1 (
    start "" /b "%JAVA_EXE%" -jar "%JAR%" --spring.config.additional-location=%CONFIG_LOC%
    echo   Backend: starting >> "%LOG%"
    ping -n 4 127.0.0.1 >nul
) else (
    echo   Backend: already running >> "%LOG%"
)

rem ---------- 3. backup daemon (PID-lock prevents duplicates) ----------
start "" /b "%PY%" "%DAEMON%"

echo [%date% %time%] autostart done >> "%LOG%"
endlocal
