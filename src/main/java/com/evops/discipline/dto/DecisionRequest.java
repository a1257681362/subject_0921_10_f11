package com.evops.discipline.dto;

import javax.validation.constraints.NotBlank;

/**
 * 处理决定请求：决定类型决定后果（成绩作废 / 禁考）；禁考须带月数。
 */
public class DecisionRequest {
    /** WARNING 警告 / SCORE_VOID 成绩作废 / BAN 禁考 / SCORE_VOID_BAN 作废并禁考。 */
    @NotBlank(message = "决定类型不能为空")
    private String decisionType;

    @NotBlank(message = "决定理由与依据不能为空")
    private String reason;

    /** 禁考月数：BAN / SCORE_VOID_BAN 必填且为正整数。 */
    private Integer banMonths;

    @NotBlank(message = "决定作出人编码不能为空")
    private String makerCode;

    private String makerName;

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getBanMonths() { return banMonths; }
    public void setBanMonths(Integer banMonths) { this.banMonths = banMonths; }
    public String getMakerCode() { return makerCode; }
    public void setMakerCode(String makerCode) { this.makerCode = makerCode; }
    public String getMakerName() { return makerName; }
    public void setMakerName(String makerName) { this.makerName = makerName; }
}
