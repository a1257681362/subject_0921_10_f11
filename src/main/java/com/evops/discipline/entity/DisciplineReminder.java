package com.evops.discipline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 违纪催办：临期（到期前 24 小时内）与超时（每 24 小时一轮）自动催办；
 * 站内 + 邮件双通道各一条，本版先落库留痕，外发渠道下版接。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_reminder")
public class DisciplineReminder extends BaseEntity {
    private Long caseId;
    /** 同一案件每轮递增；临期一轮，超时每 24 小时一轮。 */
    private Integer remindRound;
    /** DUE_SOON 临期提醒 / OVERDUE 超时催办。 */
    private String remindType;
    /** IN_SITE 站内 / EMAIL 邮件。 */
    private String channel;
    private String receiverCode;
    private String receiverName;
    private String title;
    private String content;
    private LocalDateTime dueAt;
    private String operator;
    private LocalDateTime remindedAt;
}
