package com.evops.discipline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 违纪后果执行记录：成绩作废/恢复、禁考设置/解除逐项落账，只追加；复核撤销回滚也各记一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_execution")
public class DisciplineExecution extends BaseEntity {
    private Long caseId;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    /** SCORE_INVALIDATE 成绩作废 / SCORE_RESTORE 撤销后恢复 / BAN_APPLY 禁考生效 / BAN_LIFT 撤销后解除。 */
    private String actionType;
    /** DONE 已回写 / PENDING 档案未生成待回写（成绩导入时补回写）。 */
    private String applyStatus;
    private Long scoreId;
    private Boolean validBefore;
    private Boolean validAfter;
    private LocalDate banFrom;
    private LocalDate banUntil;
    private String resultNote;
    private String operator;
    private LocalDateTime executedAt;
}
