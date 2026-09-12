<#
.SYNOPSIS
    jxc ERP 双库模式一键启停（2026-09-12 新增）

.DESCRIPTION
    本机有两个数据库，两个实例可同时运行、互不干扰：

      clean = jxc_erp    脱敏空壳（只剩种子数据，交付/演示用）  → 端口 8080
      real  = yawei_erp  真实业务数据（3.6 万行，8/1 快照）      → 端口 8081

    两库共用同一份 uploads 图片目录和 config 配置。
    所有进度提示走 Write-Host（不进输出流），返回值仅用于内部判断。

.EXAMPLE
    pwsh -File erp_mode.ps1 -Mode status        # 看两个实例状态
    pwsh -File erp_mode.ps1 -Mode real          # 拉起真实数据实例（8081）
    pwsh -File erp_mode.ps1 -Mode clean         # 拉起空壳实例（8080）
    pwsh -File erp_mode.ps1 -Mode stop -Target real
    pwsh -File erp_mode.ps1 -Mode stop -Target all
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('real', 'clean', 'status', 'stop')]
    [string]$Mode,

    [ValidateSet('real', 'clean', 'all')]
    [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'

# ========== 路径常量 ==========
$ROOT    = 'C:\Users\17815\Desktop\jxc\01-ERP'
$MYSQLD  = "$ROOT\mysql\8.0.28\bin\mysqld.exe"
$MYINI   = 'C:/Users/17815/Desktop/jxc/01-ERP/mysql/my.ini'
$JAR     = "$ROOT\erp-server\backend\release\erp-server-1.0.0.jar"
$JARCONF = 'C:/Users/17815/Desktop/jxc/01-ERP/erp-server/backend/release/config/application.yml'
$MARK    = "$ROOT\erp-server\scripts\.erp_mode.json"
$JDBC    = 'jdbc:mysql://127.0.0.1:3306/{0}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'

# ========== 模式定义 ==========
$MODES = @{
    clean = @{ Port = 8080; Db = 'jxc_erp';   Label = '脱敏空壳（交付/演示）' }
    real  = @{ Port = 8081; Db = 'yawei_erp'; Label = '真实业务数据' }
}

function Get-JavaPath {
    # 优先 PATH，回退到最后一次实测可用的 JDK 绝对路径（vbs/计划任务里 PATH 可能不全）
    $c = Get-Command java -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    $fallback = 'C:\Program Files\Java\jdk-25.0.2\bin\java.exe'
    if (Test-Path $fallback) { return $fallback }
    return $null
}

function Get-PortPid([int]$Port) {
    $c = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
         Select-Object -First 1
    if ($c) { return [int]$c.OwningProcess }
    return 0
}

function Get-Mark {
    if (Test-Path $MARK) {
        try { return Get-Content $MARK -Raw | ConvertFrom-Json } catch { return $null }
    }
    return $null
}

function Save-Mark($Key, $ProcId, $Port, $Db) {
    $obj = @{}
    $old = Get-Mark
    if ($old) { foreach ($p in $old.PSObject.Properties) { $obj[$p.Name] = $p.Value } }
    $obj[$Key] = @{ pid = $ProcId; port = $Port; db = $Db; since = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss') }
    $obj | ConvertTo-Json -Depth 5 | Set-Content $MARK -Encoding UTF8
}

function Clear-Mark($Key) {
    $old = Get-Mark
    if (-not $old) { return }
    $obj = @{}
    foreach ($p in $old.PSObject.Properties) { if ($p.Name -ne $Key) { $obj[$p.Name] = $p.Value } }
    $obj | ConvertTo-Json -Depth 5 | Set-Content $MARK -Encoding UTF8
}

function Test-Mysql {
    # 返回 $true / $false，提示走 Write-Host 不污染返回值
    if (Get-PortPid 3306) { return $true }
    Write-Host '  MySQL 未运行，先拉起 ...'
    Start-Process -FilePath $MYSQLD -ArgumentList "--defaults-file=$MYINI" -WindowStyle Hidden
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 500
        if (Get-PortPid 3306) { Write-Host '  MySQL 就绪（3306）'; return $true }
    }
    Write-Host '  [FAIL] MySQL 启动失败，3306 始终未监听'
    return $false
}

function Start-Mode([string]$Key) {
    $m = $MODES[$Key]
    Write-Host "==> 启动 $Key（$($m.Label)）端口 $($m.Port) / 库 $($m.Db)"

    if ((Test-Mysql) -ne $true) { return $false }

    $exist = Get-PortPid $m.Port
    if ($exist) {
        Write-Host "  端口 $($m.Port) 已被 PID $exist 占用，跳过启动（幂等）"
        $rec = Get-Mark
        if (-not $rec -or -not $rec.$Key) { Save-Mark $Key $exist $m.Port $m.Db }
        return $true
    }

    if (-not (Test-Path $JAR)) { Write-Host "  [FAIL] jar 不存在：$JAR"; return $false }

    $java = Get-JavaPath
    if (-not $java) { Write-Host '  [FAIL] 找不到 java，请确认 PATH 或 JDK 安装'; return $false }

    $env:ERP_PORT   = "$($m.Port)"
    $env:ERP_DB_URL = ($JDBC -f $m.Db)
    Start-Process -FilePath $java `
        -ArgumentList '-jar', $JAR, "--spring.config.additional-location=file:$JARCONF" `
        -WindowStyle Hidden

    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        if (Get-PortPid $m.Port) { break }
    }
    $procId = Get-PortPid $m.Port
    if (-not $procId) { Write-Host "  [FAIL] $($m.Port) 未监听（启动失败）"; return $false }

    # HTTP 就绪校验（端口通了 ≠ 服务可用）
    $httpOk = $false
    for ($i = 0; $i -lt 20; $i++) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$($m.Port)/" -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { $httpOk = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }

    Save-Mark $Key $procId $m.Port $m.Db
    if ($httpOk) {
        Write-Host "  [OK] 就绪 → http://127.0.0.1:$($m.Port)  (PID $procId)"
    } else {
        Write-Host "  [WARN] 端口已监听(PID $procId)但 HTTP 未返回 200，稍等再试"
    }
    return $true
}

function Stop-Mode([string]$Key) {
    $m = $MODES[$Key]
    $p = Get-PortPid $m.Port
    if (-not $p) { Write-Host "==> $Key（端口 $($m.Port)）未运行，无需停止"; Clear-Mark $Key; return }
    Write-Host "==> 停止 $Key（端口 $($m.Port) / PID $p）"
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 300
        if (-not (Get-PortPid $m.Port)) { break }
    }
    if (Get-PortPid $m.Port) { Write-Host "  [WARN] 端口 $($m.Port) 仍被占用" }
    else { Write-Host '  [OK] 已停止'; Clear-Mark $Key }
}

function Show-Status {
    Write-Host '========== jxc ERP 双库模式状态 =========='
    $mysqlPid = Get-PortPid 3306
    $mysqlTxt = if ($mysqlPid) { "UP (PID $mysqlPid)" } else { 'DOWN' }
    Write-Host "MySQL 3306 : $mysqlTxt"
    Write-Host ''
    $rec = Get-Mark
    foreach ($key in @('clean', 'real')) {
        $m = $MODES[$key]
        $p = Get-PortPid $m.Port
        if ($p) {
            $db = $m.Db
            if ($rec -and $rec.$key -and $rec.$key.pid -eq $p) { $db = $rec.$key.db }
            Write-Host ("  [{0,-5}] 端口 {1}  UP   PID {2,-7} 库 {3,-10} {4}" -f $key, $m.Port, $p, $db, $m.Label)
            Write-Host ("           → http://127.0.0.1:{0}" -f $m.Port)
        } else {
            Write-Host ("  [{0,-5}] 端口 {1}  DOWN           {2}" -f $key, $m.Port, $m.Label)
        }
    }
    Write-Host ''
    if (Test-Path $MARK) { Write-Host "模式记录：$MARK" }
}

# ========== 主流程 ==========
switch ($Mode) {
    'status' { Show-Status }
    'stop' {
        if ($Target -eq 'all') { foreach ($k in @('real', 'clean')) { Stop-Mode $k } }
        else { Stop-Mode $Target }
    }
    default { Start-Mode $Mode | Out-Null }
}
