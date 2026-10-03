package com.evops.discipline.common;

/**
 * 违纪立案业务常量：案件状态、版本变更类型、决定类型及其后果、证据/催办/复核/执行/禁考状态。
 */
public final class DisciplineConstants {

    private DisciplineConstants() {
    }

    /** 处理决定期限默认天数（立案即定时钟点，不依赖人工盯期限）。 */
    public static final int DEFAULT_DECISION_DUE_DAYS = 7;
    /** 临期提醒窗口：到期前 24 小时内补一轮提醒。 */
    public static final int DUE_SOON_HOURS = 24;
    /** 超时催办节奏：超时后每 24 小时一轮。 */
    public static final int OVERDUE_REMIND_EVERY_HOURS = 24;

    // 案件状态（当前版本状态）
    /** 已立案待决定：监考上报附证据立案，限期出处理决定。 */
    public static final String CASE_FILED = "FILED";
    /** 已决定待执行：处理决定已作出。 */
    public static final String CASE_DECIDED = "DECIDED";
    /** 已执行闭环：决定后果全部落地并回写成绩档案，案件封存。 */
    public static final String CASE_CLOSED = "CLOSED";
    /** 复核中：撤销立案申请已提交，待第二人复核。 */
    public static final String CASE_REVIEWING = "REVIEWING";
    /** 已撤销：复核通过撤销立案，已落地的后果回滚，案件封存。 */
    public static final String CASE_REVOKED = "REVOKED";

    // 版本变更类型（轨迹只追加）
    public static final String CHANGE_FILE = "FILE";
    public static final String CHANGE_DECIDE = "DECIDE";
    public static final String CHANGE_EXECUTE = "EXECUTE";
    public static final String CHANGE_REVOKE_APPLY = "REVOKE_APPLY";
    public static final String CHANGE_REVOKE_APPROVE = "REVOKE_APPROVE";
    public static final String CHANGE_REVOKE_REJECT = "REVOKE_REJECT";

    // 处理决定类型
    /** 警告：不改成绩、不设禁考。 */
    public static final String DECISION_WARNING = "WARNING";
    /** 成绩作废：当批次成绩回写为无效。 */
    public static final String DECISION_SCORE_VOID = "SCORE_VOID";
    /** 禁考：入禁考名册，期限内报名拦截。 */
    public static final String DECISION_BAN = "BAN";
    /** 作废并禁考：两项后果同时落地。 */
    public static final String DECISION_SCORE_VOID_BAN = "SCORE_VOID_BAN";

    // 证据类型
    public static final String EVIDENCE_PHOTO = "PHOTO";
    public static final String EVIDENCE_VIDEO = "VIDEO";
    public static final String EVIDENCE_DOC = "DOC";
    public static final String EVIDENCE_WITNESS = "WITNESS";
    public static final String EVIDENCE_OTHER = "OTHER";

    // 催办
    public static final String REMIND_DUE_SOON = "DUE_SOON";
    public static final String REMIND_OVERDUE = "OVERDUE";
    public static final String CHANNEL_IN_SITE = "IN_SITE";
    public static final String CHANNEL_EMAIL = "EMAIL";

    // 撤销复核状态
    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_APPROVED = "APPROVED";
    public static final String REVIEW_REJECTED = "REJECTED";

    // 后果执行记录
    public static final String EXEC_SCORE_INVALIDATE = "SCORE_INVALIDATE";
    public static final String EXEC_SCORE_RESTORE = "SCORE_RESTORE";
    public static final String EXEC_BAN_APPLY = "BAN_APPLY";
    public static final String EXEC_BAN_LIFT = "BAN_LIFT";
    /** 已回写成绩档案。 */
    public static final String APPLY_DONE = "DONE";
    /** 成绩档案尚未生成，挂待回写；成绩导入时自动补回写。 */
    public static final String APPLY_PENDING = "PENDING";
    /** 成绩生成前决定已被复核撤销，挂起回写取消。 */
    public static final String APPLY_CANCELED = "CANCELED";

    // 违纪类型
    public static final String VIOLATION_CHEAT = "CHEAT";
    public static final String VIOLATION_IMPERSONATE = "IMPERSONATE";
    public static final String VIOLATION_PROHIBITED = "PROHIBITED";
    public static final String VIOLATION_DISORDER = "DISORDER";
    public static final String VIOLATION_OTHER = "OTHER";

    // 禁考名册状态
    public static final String BAN_ACTIVE = "ACTIVE";
    public static final String BAN_LIFTED = "LIFTED";

    // 封存授权角色（封存记录只有授权岗能看；学员门户另行走脱敏视图）
    public static final String ROLE_DOSSIER = "dossier";
}
