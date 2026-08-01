@echo off
rem ============================================================
rem Yawei ERP 开机自启注册器（2026-08-01）—— 只需运行一次
rem 注册计划任务 YaweiERP_AutoStart：开机时（SYSTEM 账户）运行 startup-all.bat
rem 效果：断电来电 → Windows 开机 → 自动拉起 MySQL+后端+备份守护，无需登录
rem 用法：双击本文件 → 弹出 UAC 点「是」→ 看到"注册成功"即可
rem 卸载：以管理员运行  schtasks /delete /tn YaweiERP_AutoStart /f
rem ============================================================

rem ---- 自动提权：非管理员时以管理员身份重启自己 ----
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo 正在请求管理员权限（请在弹出的 UAC 窗口点「是」）...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo ============================================
echo  Yawei ERP 开机自启注册
echo ============================================

rem ---- 注册开机计划任务（SYSTEM 账户，无需登录） ----
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
