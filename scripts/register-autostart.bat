@echo off
rem ============================================================
rem Jxc ERP autostart registrar (2026-08-01) - run once
rem Registers scheduled task JxcERP_AutoStart: at system startup
rem (SYSTEM account, no logon needed) runs startup-all.bat.
rem Effect: power loss -> power on -> Windows boots -> MySQL +
rem backend + backup daemon start automatically, no logon needed.
rem Usage: double-click this file -> click "Yes" on UAC prompt.
rem Uninstall (as admin): schtasks /delete /tn JxcERP_AutoStart /f
rem NOTE: saved as GBK so Chinese echo renders correctly in cmd.
rem ============================================================

rem ---- auto-elevate: restart self as admin when not admin ----
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo 需要管理员权限，正在请求 UAC 提升...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo ============================================
echo  Jxc ERP 开机自启注册
echo ============================================

rem ---- register boot-time task (SYSTEM account, no logon) ----
schtasks /create /tn "JxcERP_AutoStart" ^
    /tr "cmd /c C:\Users\17815\Desktop\jxc\01-ERP\erp-server\scripts\startup-all.bat" ^
    /sc onstart /ru SYSTEM /rl HIGHEST /f

if %errorlevel% equ 0 (
    echo.
    echo [OK] 注册成功：开机自动启动 MySQL + 后端 + 备份守护
    echo      JxcERP_AutoStart 以 SYSTEM 身份运行，无需登录
    echo.
    echo 验证: schtasks /query /tn JxcERP_AutoStart
    echo 移除: schtasks /delete /tn JxcERP_AutoStart /f
) else (
    echo.
    echo [失败] 注册未成功，是否未点 UAC 确认？
)

pause
