package com.evops.discipline.dto;

import javax.validation.constraints.NotBlank;

/**
 * 复核结论请求：复核人不能是申请人；通过撤销立案并回滚后果，驳回必须写意见并回到原状态。
 */
public class DisciplineReviewConclusionRequest {
    /** true 复核通过（撤销立案）/ false 驳回（维持原决定）。 */
    private Boolean approved;

    @NotBlank(message = "复核人编码不能为空")
    private String reviewerCode;

    private String reviewerName;

    /** 驳回时必填；通过时可选。 */
    private String comment;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getReviewerCode() { return reviewerCode; }
    public void setReviewerCode(String reviewerCode) { this.reviewerCode = reviewerCode; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
