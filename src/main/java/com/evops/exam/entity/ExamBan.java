package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 禁考名册：禁考决定执行即入册，报名导入按证件号拦截；复核撤销通过即解除（留痕不删行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_exam_ban")
public class ExamBan extends BaseEntity {
    private Long registrationId;
    private String idNo;
    private String name;
    private Long caseId;
    private String caseNo;
    private String reason;
    private LocalDate banFrom;
    private LocalDate banUntil;
    /** ACTIVE 禁考中 / LIFTED 已解除（复核撤销）。 */
    private String status;
    private LocalDateTime liftedAt;
    private String liftReason;
    private String operator;
}
