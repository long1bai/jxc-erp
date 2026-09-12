package com.jxc.erp.entity;

/** 操作日志记录（审计用） */
public record OperationLog(String userName, String role, String method, String path,
                           String module, String detail, String ip, int status, int costMs) {
}
