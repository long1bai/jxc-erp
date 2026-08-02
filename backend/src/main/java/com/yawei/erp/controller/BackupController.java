package com.yawei.erp.controller;

import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;

/** 数据备份（mysqldump → backup 目录，按时间命名） */
@RestController
@RequestMapping("/api/backup")
public class BackupController {


    @org.springframework.beans.factory.annotation.Value("${app.backup-dir}")
    private String backupDir;

    @org.springframework.beans.factory.annotation.Value("${app.mysqldump-path}")
    private String mysqldumpPath;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:}")
    private String dbPassword;

    @PostMapping("/create")
    public Map<String, Object> create() {
        try {
            Files.createDirectories(Path.of(backupDir));
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String file = backupDir + "/yawei_erp_" + stamp + ".sql";
            // 2026-08-02: 数据库已加固密码，mysqldump 需带 -p（从配置读，空则不带）
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(mysqldumpPath);
            cmd.add("-u");
            cmd.add("root");
            String dbPass = dbPassword;
            if (dbPass != null && !dbPass.isBlank()) {
                cmd.add("-p" + dbPass);
            }
            cmd.add("--default-character-set=utf8mb4");
            cmd.add("--single-transaction");
            cmd.add("--routines");
            cmd.add("--triggers");
            cmd.add("yawei_erp");
            cmd.add("-r");
            cmd.add(file);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return ApiResponse.fail("备份超时");
            }
            if (p.exitValue() != 0) {
                return ApiResponse.fail("备份失败（mysqldump 退出码 " + p.exitValue() + "）");
            }
            long mb = Files.size(Path.of(file)) / 1024 / 1024;
            return ApiResponse.ok(Map.of("file", file, "sizeMB", mb));
        } catch (Exception e) {
            return ApiResponse.fail("备份失败：" + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        File dir = new File(backupDir);
        File[] files = dir.exists() ? dir.listFiles((d, n) -> n.endsWith(".sql")) : new File[0];
        var items = Arrays.stream(files == null ? new File[0] : files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(f -> Map.of(
                        "name", f.getName(),
                        "sizeMB", f.length() / 1024.0 / 1024.0,
                        "time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .format(java.time.Instant.ofEpochMilli(f.lastModified())
                                        .atZone(java.time.ZoneId.systemDefault()))))
                .toList();
        return ApiResponse.ok(Map.of("items", items));
    }

    /** 下载备份文件 */
    @GetMapping("/download/{name}")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable String name) {
        try {
            // 防目录穿越：只允许 backup 目录下的 .sql 文件名
            if (name.contains("..") || name.contains("/") || name.contains("\\\\") || !name.endsWith(".sql")) {
                return org.springframework.http.ResponseEntity.badRequest().build();
            }
            Path file = Path.of(backupDir, name);
            if (!Files.exists(file)) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            byte[] data = Files.readAllBytes(file);
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    /** 恢复备份（导入 SQL——覆盖当前数据，危险操作，前端需二次确认） */
    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.contains("..") || name.contains("/") || name.contains("\\\\") || !name.endsWith(".sql")) {
            return ApiResponse.fail("备份文件名无效");
        }
        Path file = Path.of(backupDir, name);
        if (!Files.exists(file)) {
            return ApiResponse.fail("备份文件不存在");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "C:/mysql/8.0.28/bin/mysql.exe", "-u", "root", "--default-character-set=utf8mb4",
                    "yawei_erp", "-e", "source " + file.toString().replace('\\', '/'));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(300, java.util.concurrent.TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!done) {
                p.destroyForcibly();
                return ApiResponse.fail("恢复超时（300 秒）");
            }
            if (p.exitValue() != 0) {
                return ApiResponse.fail("恢复失败：\n" + out.substring(0, Math.min(out.length(), 500)));
            }
            return ApiResponse.ok(Map.of("restored", name));
        } catch (Exception e) {
            return ApiResponse.fail("恢复失败：" + e.getMessage());
        }
    }
}
