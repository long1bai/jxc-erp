package com.yawei.erp.util;

import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.yawei.erp.mapper.SysMapper;

/** 单据编号生成器：前缀 + YYYYMMDD + 4位序号（基于 sequences 表）
 *  并发安全策略（2026-08-01 压测收敛，历经四轮踩坑）：
 *  1. SELECT+INSERT 预检：TOCTOU，并发首插重复
 *  2. INSERT...ON DUPLICATE KEY UPDATE：MySQL 已知死锁（10 并发死锁 3 次）
 *  3. INSERT IGNORE + UPDATE：并发首插 gap 锁与插入意向锁互等，死锁（10 并发死锁 6 次）
 *  4. GET_LOCK + FOR UPDATE：无死锁但连接级锁语义在池化连接下不可靠，重复单号
 *  最终方案：回到 UPDATE seq=seq+1（行存在时单行锁串行化，无 gap 锁无死锁）+ INSERT 建行，
 *  首插并发时 gap 锁与插入意向锁可能死锁——这是 MySQL 对"并发创建同一唯一键"的标准行为，
 *  官方处理就是捕获 DeadlockLoser/DuplicateKey 重试（8 次足够收敛）。 */
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
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                int updated = mapper.seqIncrement(key, year, month);
                if (updated == 0) {
                    try {
                        mapper.seqInsert(key, year, month);
                    } catch (DuplicateKeyException e) {
                        // 并发首插竞争：对方已建行，重试走 UPDATE 路径
                        org.slf4j.LoggerFactory.getLogger(SequenceUtil.class)
                                .warn("seqInsert 撞键重试 attempt={} prefix={} msg={}", attempt, prefix,
                                        String.valueOf(e.getMessage()).substring(0, Math.min(80, String.valueOf(e.getMessage()).length())));
                        sleep(attempt);
                        continue;
                    }
                    return prefix + "-" + dateStr + "-0001";
                }
                Integer seq = mapper.seqSelect(key, year, month);
                return prefix + "-" + dateStr + "-" + String.format("%04d", seq == null ? 1 : seq);
            } catch (DeadlockLoserDataAccessException e) {
                // 并发首插 gap 锁/插入意向锁死锁（MySQL 已知），受害者重试
                org.slf4j.LoggerFactory.getLogger(SequenceUtil.class)
                        .warn("seq 死锁重试 attempt={} prefix={}", attempt, prefix);
                sleep(attempt);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(SequenceUtil.class)
                        .warn("seq 未知异常 attempt={} prefix={} type={} msg={}", attempt, prefix,
                                e.getClass().getSimpleName(),
                                String.valueOf(e.getMessage()).substring(0, Math.min(100, String.valueOf(e.getMessage()).length())));
                throw e;
            }
        }
        throw new RuntimeException("生成单号失败（并发冲突超8次）: " + prefix);
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(15L * (attempt + 1));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
