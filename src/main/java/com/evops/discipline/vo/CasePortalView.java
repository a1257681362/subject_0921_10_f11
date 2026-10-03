package com.evops.discipline.vo;

/**
 * 学员门户案件视图（脱敏）：只回本人案件；隐藏证件号、上报人/承办人/复核人、决定人、证据存储引用等内部信息。
 */
public class CasePortalView {
    private String caseNo;
    /** 案件状态（FILED / DECIDED / CLOSED / REVIEWING / REVOKED）。 */
    private String status;
    private String statusText;
    private String violationType;
    private String violationTypeText;
    private String title;
    private String decisionType;
    private String decisionTypeText;
    private String decidedAt;
    private String decisionDueAt;
    private Boolean overdue;
    private String executedAt;
    /** 复核申请号与状态（学员发起撤销后可查进度）。 */
    private String reviewStatus;
    private String reviewStatusText;
    private String filedAt;
    private String sealedAt;
    /** 证据件数（不回文件名与引用）。 */
    private Integer evidenceCount;

    public String getCaseNo() { return caseNo; }
    public void setCaseNo(String caseNo) { this.caseNo = caseNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getViolationType() { return violationType; }
    public void setViolationType(String violationType) { this.violationType = violationType; }
    public String getViolationTypeText() { return violationTypeText; }
    public void setViolationTypeText(String violationTypeText) { this.violationTypeText = violationTypeText; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }
    public String getDecisionTypeText() { return decisionTypeText; }
    public void setDecisionTypeText(String decisionTypeText) { this.decisionTypeText = decisionTypeText; }
    public String getDecidedAt() { return decidedAt; }
    public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionDueAt() { return decisionDueAt; }
    public void setDecisionDueAt(String decisionDueAt) { this.decisionDueAt = decisionDueAt; }
    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }
    public String getExecutedAt() { return executedAt; }
    public void setExecutedAt(String executedAt) { this.executedAt = executedAt; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewStatusText() { return reviewStatusText; }
    public void setReviewStatusText(String reviewStatusText) { this.reviewStatusText = reviewStatusText; }
    public String getFiledAt() { return filedAt; }
    public void setFiledAt(String filedAt) { this.filedAt = filedAt; }
    public String getSealedAt() { return sealedAt; }
    public void setSealedAt(String sealedAt) { this.sealedAt = sealedAt; }
    public Integer getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(Integer evidenceCount) { this.evidenceCount = evidenceCount; }
}
