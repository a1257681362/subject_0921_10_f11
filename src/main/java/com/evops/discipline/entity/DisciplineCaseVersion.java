package com.evops.discipline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 违纪案件版本：立案 / 决定 / 执行 / 撤销申请 / 复核结论各追加一版，永不修改；主表只切当前版本号与状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_case_version")
public class DisciplineCaseVersion extends BaseEntity {
    private Long caseId;
    private Integer versionNo;
    /** 本版状态：FILED / DECIDED / CLOSED / REVIEWING / REVOKED。 */
    private String status;
    /** FILE / DECIDE / EXECUTE / REVOKE_APPLY / REVOKE_APPROVE / REVOKE_REJECT。 */
    private String changeType;
    private String decisionType;
    private String changeReason;
    private String operator;
    private LocalDateTime changedAt;
}
