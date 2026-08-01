package com.yawei.erp;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 单据编号生成器：前缀 + YYYYMMDD + 4位序号（基于 sequences 表，MyBatis 原子自增） */
@Component
public class SequenceUtil {

    private final SysMapper mapper;

    public SequenceUtil(SysMapper mapper) {
        this.mapper = mapper;
    }

    /** 按天自增：前缀-YYYYMMDD-0001（如 CGDD-20260731-0001） */
    public String nextDaily(String prefix) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String year = dateStr.substring(0, 4);
        String month = dateStr.substring(4, 6);
        String key = prefix + ":" + dateStr;
        int updated = mapper.seqIncrement(key, year, month);
        if (updated == 0) {
            mapper.seqInsert(key, year, month);
            return prefix + "-" + dateStr + "-0001";
        }
        Integer seq = mapper.seqSelect(key, year, month);
        return prefix + "-" + dateStr + "-" + String.format("%04d", seq == null ? 1 : seq);
    }
}
