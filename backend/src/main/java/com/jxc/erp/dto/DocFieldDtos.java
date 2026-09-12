package com.jxc.erp.dto;

import java.util.List;

/** 单据能力中心 DTO（动态字段定义 / 双通道识别结果 / 导入结果） */
public class DocFieldDtos {

    /** 单个动态字段定义（前端预览可编辑后整体保存） */
    public record FieldDef(String fieldKey, String fieldName, String fieldType,
                           List<String> options, Integer sortOrder) {}

    /** 双通道识别结果（传统解析 / AI 返回同构，前端一份代码渲染） */
    public record RecognizeResult(String origin, List<FieldDef> fields,
                                  List<String> sampleHeaders, List<List<String>> sampleRows) {}

    /** 导入错误明细（行号 + 原因） */
    public record ImportError(int row, String message) {}

    /** 导入结果（total 总行数 / ok 有效行 / fail 失败行 / created 新建单数 / skipped 跳过行） */
    public record ImportResult(int total, int okCount, int failCount,
                               int created, int skipped, List<ImportError> errorRows) {}

    /** 保存字段定义的请求体 */
    public record SaveReq(String docType, List<FieldDef> fields) {}
}
