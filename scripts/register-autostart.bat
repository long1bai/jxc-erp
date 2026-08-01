@echo off
rem ============================================================
rem Yawei ERP autostart registrar (2026-08-01) - run once
rem Registers scheduled task YaweiERP_AutoStart: at system startup
rem (SYSTEM account, no logon needed) runs startup-all.bat.
rem Effect: power loss -> power on -> Windows boots -> MySQL +
rem backend + backup daemon start automatically, no logon needed.
rem Usage: double-click this file -> click "Yes" on UAC prompt.
rem Uninstall (as admin): schtasks /delete /tn YaweiERP_AutoStart /f
rem NOTE: saved as GBK so Chinese echo renders correctly in cmd.
rem ============================================================

rem ---- auto-elevate: restart self as admin when not admin ----
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo 正在请求管理员权限（请在弹出的 UAC 窗口点「是」）...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo ============================================
echo  Yawei ERP 开机自启注册
echo ============================================

rem ---- register boot-time task (SYSTEM account, no logon) ----
schtasks /create /tn "YaweiERP_AutoStart" ^
    /tr "cmd /c I:\yawei-erp-java\scripts\startup-all.bat" ^
    /sc onstart /ru SYSTEM /rl HIGHEST /f

if %errorlevel% equ 0 (
    echo.
    echo [OK] 开机自启已注册：开机时自动启动 MySQL + 后端 + 备份守护
    echo      任务名：YaweiERP_AutoStart（SYSTEM 账户，无需登录）
    echo.
    echo 验证：schtasks /query /tn YaweiERP_AutoStart
    echo 卸载：schtasks /delete /tn YaweiERP_AutoStart /f
) else (
    echo.
    echo [失败] 注册未成功，请检查是否点了 UAC 的「是」。
)

pause
