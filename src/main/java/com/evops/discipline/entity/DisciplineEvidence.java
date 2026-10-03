package com.evops.discipline.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 违纪证据：立案必附至少一件；只追加不删除，随案封存。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_discipline_evidence")
public class DisciplineEvidence extends BaseEntity {
    private Long caseId;
    /** PHOTO 现场照片 / VIDEO 视频 / DOC 书证笔录 / WITNESS 证人证言 / OTHER 其他。 */
    private String evidenceType;
    private String fileName;
    /** 附件存储引用（路径/对象存储键），本版只登记元数据与引用。 */
    private String fileRef;
    private String description;
    private String uploaderCode;
    private String uploaderName;
    private LocalDateTime uploadedAt;
}
