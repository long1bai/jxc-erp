' Yawei ERP v2 auto-start (2026-08-01, replaces v1 启动ERP.vbs)
' On logon: start MySQL -> backend jar -> backup daemon (idempotent)
' Location: C:\Users\Administrator\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\
Dim WshShell
Set WshShell = CreateObject("WScript.Shell")

' Absolute paths only: explorer env PATH differs from bash/cmd PATH.
Dim PY, JAVA
PY = "C:\Users\Administrator\AppData\Local\Programs\Python\Python311-32\python.exe"
JAVA = "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"
If Not CreateObject("Scripting.FileSystemObject").FileExists(JAVA) Then
    JAVA = "java"
End If

' 1. MySQL (start only if 3306 not listening; use Windows-style path)
If Not PortListening("3306") Then
    WshShell.Run "C:\mysql\8.0.28\bin\mysqld.exe --defaults-file=C:/mysql/my.ini", 0, False
    WScript.Sleep 3000
End If

' 2. Backend jar (start only if 8080 not listening)
If Not PortListening("8080") Then
    WshShell.Run """" & JAVA & """ -jar I:\yawei-erp-java\backend\target\yawei-erp-0.0.1-SNAPSHOT.jar", 0, False
    WScript.Sleep 3000
End If

' 3. Backup daemon (daily 17:30 backup to H:; PID-lock prevents duplicates)
WshShell.Run """" & PY & """ I:\yawei-erp-java\scripts\dr_daemon.py", 0, False

Function PortListening(port)
    PortListening = False
    Dim sh, exec, line
    Set sh = CreateObject("WScript.Shell")
    Set exec = sh.Exec("netstat -ano")
    Do While Not exec.StdOut.AtEndOfStream
        line = exec.StdOut.ReadLine()
        If InStr(line, ":" & port & " ") > 0 And InStr(line, "LISTEN") > 0 Then
            PortListening = True
            Exit Do
        End If
    Loop
    Set exec = Nothing
    Set sh = Nothing
End Function
