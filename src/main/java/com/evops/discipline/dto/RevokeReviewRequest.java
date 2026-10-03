package com.evops.discipline.dto;

import javax.validation.constraints.NotBlank;

/**
 * 撤销立案复核请求：申请人申请 + 第二人复核结论。
 */
public class RevokeReviewRequest {
    @NotBlank(message = "申请人编码不能为空")
    private String applicantCode;
    private String applicantName;
    @NotBlank(message = "撤销理由不能为空")
    private String applyReason;

    public String getApplicantCode() { return applicantCode; }
    public void setApplicantCode(String applicantCode) { this.applicantCode = applicantCode; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplyReason() { return applyReason; }
    public void setApplyReason(String applyReason) { this.applyReason = applyReason; }
}
