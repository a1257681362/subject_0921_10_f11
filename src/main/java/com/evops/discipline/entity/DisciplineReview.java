package com.evops.discipline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 违纪撤销复核：撤销立案必走复核，复核人不能是申请人；通过即撤销并回滚后果，驳回回到原状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_review")
public class DisciplineReview extends BaseEntity {
    private Long caseId;
    /** 复核申请号 FH + 日期 8 位 + 4 位流水。 */
    private String reviewNo;
    /** PENDING 待复核 / APPROVED 复核通过（撤销立案）/ REJECTED 复核驳回（维持原决定）。 */
    private String status;
    private String applicantCode;
    private String applicantName;
    private String applyReason;
    private LocalDateTime appliedAt;
    /** 申请前案件状态：复核驳回时回到该状态（DECIDED / CLOSED）。 */
    private String prevStatus;
    private String reviewerCode;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
}
