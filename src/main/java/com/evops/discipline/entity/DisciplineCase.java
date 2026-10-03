package com.evops.discipline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 违纪案件主表（模块）：监考上报附证据立案，限期出处理决定，超时自动催办；
 * 决定类型对应成绩作废、禁考等后果，执行结果回写成绩档案；撤销立案走复核。
 * 内容写入后不改（仅状态随流转推进），每次流转只追加版本（见 t_discipline_case_version）。
 * 闭环（CLOSED）或撤销（REVOKED）即封存，封存记录仅授权岗（dossier）可见。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_case")
public class DisciplineCase extends BaseEntity {
    /** 案件编号 WJ + 立案日期 8 位 + 4 位流水。 */
    private String caseNo;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    /** 学员证件号快照：封存敏感字段，仅授权岗可见，学员门户脱敏。 */
    private String idNo;
    /** CHEAT 作弊 / IMPERSONATE 替考 / PROHIBITED 携带违禁品 / DISORDER 扰乱秩序 / OTHER 其他。 */
    private String violationType;
    private String title;
    private String description;
    private String reporterCode;
    private String reporterName;
    /** 承办人（决定责任人）：缺省为上报人；催办发给承办人。 */
    private String handlerCode;
    private String handlerName;
    private LocalDateTime reportedAt;
    private Integer evidenceCount;
    /** 当前版本状态：FILED / DECIDED / CLOSED / REVIEWING / REVOKED。 */
    private String status;
    private Integer currentVersion;
    /** 处理决定期限（立案即定时钟点）。 */
    private LocalDateTime decisionDueAt;
    private Integer decisionDueDays;
    /** WARNING / SCORE_VOID / BAN / SCORE_VOID_BAN。 */
    private String decisionType;
    private String decisionReason;
    private String decisionMakerCode;
    private String decisionMakerName;
    private LocalDateTime decidedAt;
    /** 决定对应后果（决定时落定，执行按此逐项落地）。 */
    private Boolean invalidateScore;
    private Boolean banFlag;
    private Integer banMonths;
    private LocalDate banUntil;
    private LocalDateTime executedAt;
    /** 成绩档案回写状态：档案未生成时挂待回写，成绩导入自动补回写。 */
    private Boolean scoreWriteback;
    private Long activeReviewId;
    /** 封存：CLOSED / REVOKED 即封存，仅授权岗可见。 */
    private Boolean sealed;
    private LocalDateTime sealedAt;
    private String operator;
    private String remark;
}
