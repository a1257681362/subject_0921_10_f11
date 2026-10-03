package com.evops.discipline.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 监考上报立案请求：附证据立案；期限按 dueDays 走，缺省 7 天。
 */
public class CaseFileRequest {
    @NotNull(message = "批次 id 不能为空")
    private Long batchId;

    @NotBlank(message = "报名号不能为空")
    private String regNo;

    @NotBlank(message = "违纪类型不能为空")
    private String violationType;

    @NotBlank(message = "案件标题不能为空")
    private String title;

    private String description;

    @NotBlank(message = "上报人编码不能为空")
    private String reporterCode;

    private String reporterName;

    /** 承办人编码：缺省由上报人承办；催办发给承办人。 */
    private String handlerCode;
    private String handlerName;

    /** 违纪发生时间（yyyy-MM-dd HH:mm(:ss) / yyyy-MM-dd），缺省为当前时间。 */
    private String occurredAt;

    /** 处理决定期限天数，缺省 7。 */
    private Integer dueDays;

    @NotEmpty(message = "立案必须附证据")
    @Valid
    private List<EvidenceItem> evidences;

    private String remark;

    public static class EvidenceItem {
        @NotBlank(message = "证据类型不能为空")
        private String evidenceType;
        @NotBlank(message = "证据文件名不能为空")
        private String fileName;
        private String fileRef;
        private String description;

        public String getEvidenceType() { return evidenceType; }
        public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileRef() { return fileRef; }
        public void setFileRef(String fileRef) { this.fileRef = fileRef; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getRegNo() { return regNo; }
    public void setRegNo(String regNo) { this.regNo = regNo; }
    public String getViolationType() { return violationType; }
    public void setViolationType(String violationType) { this.violationType = violationType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReporterCode() { return reporterCode; }
    public void setReporterCode(String reporterCode) { this.reporterCode = reporterCode; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getHandlerCode() { return handlerCode; }
    public void setHandlerCode(String handlerCode) { this.handlerCode = handlerCode; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    public Integer getDueDays() { return dueDays; }
    public void setDueDays(Integer dueDays) { this.dueDays = dueDays; }
    public List<EvidenceItem> getEvidences() { return evidences; }
    public void setEvidences(List<EvidenceItem> evidences) { this.evidences = evidences; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
