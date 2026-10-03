package com.evops.grade.service;

/**
 * 成绩有效性的外部口径提供者：其他业务模块（缺考裁定、违纪成绩作废等）实现本接口，
 * 成绩录入时统一汇总，成绩模块不反向依赖这些模块。
 */
public interface ScoreValidityOverride {

    /** 该报名记录是否存在"成绩应记为无效"的生效口径。 */
    boolean invalidates(Long registrationId);

    /** 预检清单上展示给操作员的说明（invalidates 为 true 时使用）。 */
    String describe(Long registrationId);
}
