package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.exam.entity.ExamBan;
import com.evops.exam.mapper.ExamBanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 禁考名册（报名域主数据）：禁考决定执行即入册，报名导入按证件号拦截；复核撤销通过即解除。
 * 违纪模块只负责写入/解除，报名导入在本服务做拦截，避免考试域反向依赖违纪模块。
 */
@Service
public class ExamBanService {

    private final ExamBanMapper banMapper;

    public ExamBanService(ExamBanMapper banMapper) {
        this.banMapper = banMapper;
    }

    /** 报名导入预检：证件号命中生效禁考（按服务器日期）时给出拒收原因；无证件号不拦截（转人工核验）。 */
    @Transactional(readOnly = true)
    public String rejectReason(String idNo) {
        ExamBan ban = activeBan(idNo);
        if (ban == null) {
            return null;
        }
        return String.format("证件号处于禁考期（案件 %s，禁考至 %s），报名拒收",
                ban.getCaseNo(), ban.getBanUntil());
    }

    /** 报名导入执行：命中生效禁考整批拒收，不落半条。 */
    @Transactional(readOnly = true)
    public void assertNotBanned(String idNo, String regNo) {
        ExamBan ban = activeBan(idNo);
        if (ban != null) {
            throw new BusinessException("REG_BANNED",
                    "报名号 " + regNo + " 证件号处于禁考期（案件 " + ban.getCaseNo()
                            + "，禁考至 " + ban.getBanUntil() + "），整批拒收");
        }
    }

    /**
     * 按证件号查生效禁考：ACTIVE 且当日落在禁考区间（按服务器日期，起止含当天）。
     * 无证件号（null/空白）不拦截，转报名人工核验。
     */
    @Transactional(readOnly = true)
    public ExamBan activeBan(String idNo) {
        if (idNo == null || idNo.trim().isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        List<ExamBan> bans = banMapper.selectList(new QueryWrapper<ExamBan>()
                .eq("id_no", idNo.trim())
                .eq("status", "ACTIVE")
                .le("ban_from", today)
                .ge("ban_until", today)
                .orderByDesc("id"));
        return bans.isEmpty() ? null : bans.get(0);
    }
}
