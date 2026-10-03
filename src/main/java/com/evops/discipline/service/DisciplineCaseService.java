package com.evops.discipline.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.evops.absence.common.TimeFormats;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.discipline.common.DisciplineConstants;
import com.evops.discipline.dto.CaseFileRequest;
import com.evops.discipline.dto.DecisionRequest;
import com.evops.discipline.dto.DisciplineReviewConclusionRequest;
import com.evops.discipline.dto.RevokeReviewRequest;
import com.evops.discipline.entity.DisciplineCase;
import com.evops.discipline.entity.DisciplineCaseVersion;
import com.evops.discipline.entity.DisciplineEvidence;
import com.evops.discipline.entity.DisciplineExecution;
import com.evops.discipline.entity.DisciplineReminder;
import com.evops.discipline.entity.DisciplineReview;
import com.evops.discipline.mapper.DisciplineCaseMapper;
import com.evops.discipline.mapper.DisciplineCaseVersionMapper;
import com.evops.discipline.mapper.DisciplineEvidenceMapper;
import com.evops.discipline.mapper.DisciplineExecutionMapper;
import com.evops.discipline.mapper.DisciplineReminderMapper;
import com.evops.discipline.mapper.DisciplineReviewMapper;
import com.evops.discipline.vo.CasePortalView;
import com.evops.exam.entity.ExamBan;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBanMapper;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.RegistrationService;
import com.evops.absence.service.AbsenceMarkService;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.mapper.ExamScoreMapper;
import com.evops.grade.service.ScoreNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 违纪立案（模块 / 版本 / 状态）：
 * - 监考上报附证据立案：立案即定处理决定期限（缺省 7 天），无证据不立案；
 * - 限期出处理决定：FILED → DECIDED，决定类型对应后果（成绩作废 / 禁考 / 警告）；
 * - 超时自动催办：扫描任务对临期（到期前 24 小时）与超时（每 24 小时一轮）案件向承办人发站内+邮件催办（本版先落库）；
 * - 执行结果回写成绩档案：DECIDED → CLOSED，成绩作废回写 t_exam_score.valid（档案未生成挂待回写，成绩导入补回写），
 *   禁考入禁考名册（报名导入按证件号拦截）；
 * - 撤销立案走复核：DECIDED/CLOSED → REVIEWING → REVOKED（通过，回滚后果）/ 回到原状态（驳回），复核人不能是申请人；
 * - 轨迹留全：每次流转只追加一版 t_discipline_case_version，证据/催办/执行均只追加；
 * - 学员查自己案子：门户按报名号脱敏展示；
 * - 封存：CLOSED / REVOKED 即封存，封存记录仅授权岗（dossier）可查。
 */
@Service
public class DisciplineCaseService {

    private static final Set<String> VIOLATION_TYPES = new HashSet<>(Arrays.asList(
            DisciplineConstants.VIOLATION_CHEAT,
            DisciplineConstants.VIOLATION_IMPERSONATE,
            DisciplineConstants.VIOLATION_PROHIBITED,
            DisciplineConstants.VIOLATION_DISORDER,
            DisciplineConstants.VIOLATION_OTHER));

    private static final Set<String> EVIDENCE_TYPES = new HashSet<>(Arrays.asList(
            DisciplineConstants.EVIDENCE_PHOTO,
            DisciplineConstants.EVIDENCE_VIDEO,
            DisciplineConstants.EVIDENCE_DOC,
            DisciplineConstants.EVIDENCE_WITNESS,
            DisciplineConstants.EVIDENCE_OTHER));

    private static final Set<String> DECISION_TYPES = new HashSet<>(Arrays.asList(
            DisciplineConstants.DECISION_WARNING,
            DisciplineConstants.DECISION_SCORE_VOID,
            DisciplineConstants.DECISION_BAN,
            DisciplineConstants.DECISION_SCORE_VOID_BAN));

    private static final DateTimeFormatter NO_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TEXT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DisciplineCaseMapper caseMapper;
    private final DisciplineCaseVersionMapper versionMapper;
    private final DisciplineEvidenceMapper evidenceMapper;
    private final DisciplineReminderMapper reminderMapper;
    private final DisciplineReviewMapper reviewMapper;
    private final DisciplineExecutionMapper executionMapper;
    private final ExamBanMapper banMapper;
    private final ExamScoreMapper scoreMapper;
    private final ExamBatchService batchService;
    private final RegistrationService registrationService;
    private final ScoreNotificationService notificationService;
    private final AbsenceMarkService absenceMarkService;

    public DisciplineCaseService(DisciplineCaseMapper caseMapper,
                                 DisciplineCaseVersionMapper versionMapper,
                                 DisciplineEvidenceMapper evidenceMapper,
                                 DisciplineReminderMapper reminderMapper,
                                 DisciplineReviewMapper reviewMapper,
                                 DisciplineExecutionMapper executionMapper,
                                 ExamBanMapper banMapper,
                                 ExamScoreMapper scoreMapper,
                                 ExamBatchService batchService,
                                 RegistrationService registrationService,
                                 ScoreNotificationService notificationService,
                                 AbsenceMarkService absenceMarkService) {
        this.caseMapper = caseMapper;
        this.versionMapper = versionMapper;
        this.evidenceMapper = evidenceMapper;
        this.reminderMapper = reminderMapper;
        this.reviewMapper = reviewMapper;
        this.executionMapper = executionMapper;
        this.banMapper = banMapper;
        this.scoreMapper = scoreMapper;
        this.batchService = batchService;
        this.registrationService = registrationService;
        this.notificationService = notificationService;
        this.absenceMarkService = absenceMarkService;
    }

    // ==================== 监考上报附证据立案 ====================

    @Transactional
    public DisciplineCase fileCase(CaseFileRequest request) {
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        ExamBatch batch = batchService.getBatch(request.getBatchId());
        Registration reg = requireRegistration(batch.getId(), request.getRegNo());
        String violationType = normalizeViolationType(request.getViolationType());
        LocalDateTime occurredAt = TimeFormats.parse(request.getOccurredAt(), now);
        TimeFormats.assertNotFuture(occurredAt, now);
        int dueDays = normalizeDueDays(request.getDueDays());
        if (request.getEvidences() == null || request.getEvidences().isEmpty()) {
            throw new BusinessException("EVIDENCE_REQUIRED", "立案必须附证据（现场照片/视频/书证笔录/证人证言等）");
        }

        DisciplineCase c = new DisciplineCase();
        c.setCaseNo(nextCaseNo(now));
        c.setBatchId(batch.getId());
        c.setRegistrationId(reg.getId());
        c.setRegNo(reg.getRegNo());
        c.setName(reg.getName());
        c.setIdNo(reg.getIdNo());
        c.setViolationType(violationType);
        c.setTitle(request.getTitle().trim());
        c.setDescription(trimToNull(request.getDescription()));
        c.setReporterCode(request.getReporterCode().trim());
        c.setReporterName(trimToNull(request.getReporterName()));
        // 承办人缺省为上报人：催办发给承办人，决定责任到人
        c.setHandlerCode(trimOr(request.getHandlerCode(), c.getReporterCode()));
        c.setHandlerName(trimOr(request.getHandlerName(), c.getReporterName()));
        c.setReportedAt(occurredAt);
        c.setStatus(DisciplineConstants.CASE_FILED);
        c.setCurrentVersion(1);
        c.setDecisionDueDays(dueDays);
        c.setDecisionDueAt(now.plusDays(dueDays));
        c.setInvalidateScore(false);
        c.setBanFlag(false);
        c.setScoreWriteback(false);
        c.setSealed(false);
        c.setOperator(operator);
        c.setRemark(trimToNull(request.getRemark()));
        caseMapper.insert(c);

        for (CaseFileRequest.EvidenceItem item : request.getEvidences()) {
            insertEvidence(c, item, operator, now);
        }
        c.setEvidenceCount(request.getEvidences().size());
        caseMapper.updateById(c);

        appendVersion(c, DisciplineConstants.CASE_FILED, DisciplineConstants.CHANGE_FILE, null,
                "监考上报附证据立案（证据 " + request.getEvidences().size() + " 件），处理决定期限 "
                        + dueDays + " 天（至 " + c.getDecisionDueAt().format(TEXT_TS) + "）", operator, now);
        return c;
    }

    /** 补充证据：仅 FILED 待决定阶段可追加，只追加不删除。 */
    @Transactional
    public DisciplineEvidence addEvidence(Long caseId, CaseFileRequest.EvidenceItem item) {
        DisciplineCase c = requireCase(caseId);
        if (!DisciplineConstants.CASE_FILED.equals(c.getStatus())) {
            throw new BusinessException("CASE_NOT_FILED",
                    "案件 " + c.getCaseNo() + " 当前状态 " + c.getStatus() + "，证据已固定，不能再补充");
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        DisciplineEvidence evidence = insertEvidence(c, item, operator, now);
        c.setEvidenceCount((c.getEvidenceCount() == null ? 0 : c.getEvidenceCount()) + 1);
        caseMapper.updateById(c);
        return evidence;
    }

    private DisciplineEvidence insertEvidence(DisciplineCase c, CaseFileRequest.EvidenceItem item,
                                              String operator, LocalDateTime now) {
        if (item == null) {
            throw new BusinessException("EVIDENCE_REQUIRED", "立案必须附证据");
        }
        String type = item.getEvidenceType() == null ? null : item.getEvidenceType().trim().toUpperCase();
        if (type == null || !EVIDENCE_TYPES.contains(type)) {
            throw new BusinessException("EVIDENCE_TYPE_INVALID",
                    "证据类型仅支持 PHOTO 现场照片 / VIDEO 视频 / DOC 书证笔录 / WITNESS 证人证言 / OTHER 其他："
                            + item.getEvidenceType());
        }
        if (item.getFileName() == null || item.getFileName().trim().isEmpty()) {
            throw new BusinessException("EVIDENCE_FILE_REQUIRED", "证据文件名不能为空");
        }
        DisciplineEvidence evidence = new DisciplineEvidence();
        evidence.setCaseId(c.getId());
        evidence.setEvidenceType(type);
        evidence.setFileName(item.getFileName().trim());
        evidence.setFileRef(trimToNull(item.getFileRef()));
        evidence.setDescription(trimToNull(item.getDescription()));
        evidence.setUploaderCode(operator);
        evidence.setUploadedAt(now);
        evidenceMapper.insert(evidence);
        return evidence;
    }

    // ==================== 限期出处理决定 ====================

    @Transactional
    public DisciplineCase decide(Long caseId, DecisionRequest request) {
        DisciplineCase c = requireCase(caseId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        if (!DisciplineConstants.CASE_FILED.equals(c.getStatus())) {
            throw new BusinessException("CASE_STATUS_INVALID",
                    "案件 " + c.getCaseNo() + " 当前状态 " + c.getStatus()
                            + "，仅已立案待决定（FILED）可作出处理决定");
        }
        String decisionType = request.getDecisionType().trim().toUpperCase();
        if (!DECISION_TYPES.contains(decisionType)) {
            throw new BusinessException("DECISION_TYPE_INVALID",
                    "决定类型仅支持 WARNING 警告 / SCORE_VOID 成绩作废 / BAN 禁考 / SCORE_VOID_BAN 作废并禁考："
                            + request.getDecisionType());
        }
        boolean invalidate = DisciplineConstants.DECISION_SCORE_VOID.equals(decisionType)
                || DisciplineConstants.DECISION_SCORE_VOID_BAN.equals(decisionType);
        boolean ban = DisciplineConstants.DECISION_BAN.equals(decisionType)
                || DisciplineConstants.DECISION_SCORE_VOID_BAN.equals(decisionType);
        Integer banMonths = null;
        LocalDate banUntil = null;
        if (ban) {
            if (request.getBanMonths() == null || request.getBanMonths() <= 0) {
                throw new BusinessException("BAN_MONTHS_REQUIRED", "禁考决定必须填写正整数禁考月数");
            }
            banMonths = request.getBanMonths();
            banUntil = LocalDate.now().plusMonths(banMonths);
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("DECISION_REASON_REQUIRED", "处理决定必须写明理由与依据");
        }

        c.setStatus(DisciplineConstants.CASE_DECIDED);
        c.setCurrentVersion(c.getCurrentVersion() + 1);
        c.setDecisionType(decisionType);
        c.setDecisionReason(request.getReason().trim());
        c.setDecisionMakerCode(request.getMakerCode().trim());
        c.setDecisionMakerName(trimToNull(request.getMakerName()));
        c.setDecidedAt(now);
        c.setInvalidateScore(invalidate);
        c.setBanFlag(ban);
        c.setBanMonths(banMonths);
        c.setBanUntil(banUntil);
        c.setOperator(operator);
        caseMapper.updateById(c);

        appendVersion(c, DisciplineConstants.CASE_DECIDED, DisciplineConstants.CHANGE_DECIDE, decisionType,
                "作出处理决定：" + decisionTypeText(decisionType) + "。" + request.getReason().trim(),
                operator, now);
        notifyStudent(c, "DISCIPLINE_DECISION", "违纪处理决定通知",
                String.format("您的违纪案件 %s 已作出处理决定：%s。决定将在执行后闭环，请知悉。",
                        c.getCaseNo(), decisionTypeText(decisionType)),
                operator);
        return c;
    }

    // ==================== 执行：后果落地 + 回写成绩档案 ====================

    @Transactional
    public DisciplineCase execute(Long caseId) {
        DisciplineCase c = requireCase(caseId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        if (!DisciplineConstants.CASE_DECIDED.equals(c.getStatus())) {
            throw new BusinessException("CASE_STATUS_INVALID",
                    "案件 " + c.getCaseNo() + " 当前状态 " + c.getStatus()
                            + "，仅已决定待执行（DECIDED）可执行闭环");
        }

        if (Boolean.TRUE.equals(c.getInvalidateScore())) {
            applyScoreInvalidate(c, operator, now);
        }
        if (Boolean.TRUE.equals(c.getBanFlag())) {
            applyBan(c, operator, now);
        }

        c.setStatus(DisciplineConstants.CASE_CLOSED);
        c.setCurrentVersion(c.getCurrentVersion() + 1);
        c.setExecutedAt(now);
        seal(c, now);
        c.setOperator(operator);
        caseMapper.updateById(c);
        appendVersion(c, DisciplineConstants.CASE_CLOSED, DisciplineConstants.CHANGE_EXECUTE,
                c.getDecisionType(), "处理决定执行闭环，后果已落地并回写成绩档案，案件封存", operator, now);
        notifyStudent(c, "DISCIPLINE_EXECUTED", "违纪处理决定执行通知",
                String.format("您的违纪案件 %s 的处理决定（%s）已执行闭环。如有异议，可按规定申请复核。",
                        c.getCaseNo(), decisionTypeText(c.getDecisionType())),
                operator);
        return c;
    }

    /** 成绩作废回写：档案已存在即回写 valid=false；尚未录入挂待回写，成绩导入时自动补回写。 */
    private void applyScoreInvalidate(DisciplineCase c, String operator, LocalDateTime now) {
        ExamScore score = scoreMapper.selectOne(new QueryWrapper<ExamScore>()
                .eq("registration_id", c.getRegistrationId()));
        DisciplineExecution execution = new DisciplineExecution();
        execution.setCaseId(c.getId());
        execution.setBatchId(c.getBatchId());
        execution.setRegistrationId(c.getRegistrationId());
        execution.setRegNo(c.getRegNo());
        execution.setActionType(DisciplineConstants.EXEC_SCORE_INVALIDATE);
        execution.setExecutedAt(now);
        execution.setOperator(operator);
        if (score == null) {
            execution.setApplyStatus(DisciplineConstants.APPLY_PENDING);
            execution.setResultNote("成绩档案尚未生成，挂待回写；成绩导入时自动补回写为无效");
            executionMapper.insert(execution);
            c.setScoreWriteback(false);
            return;
        }
        Boolean before = score.getValid();
        score.setValid(false);
        scoreMapper.updateById(score);
        execution.setApplyStatus(DisciplineConstants.APPLY_DONE);
        execution.setScoreId(score.getId());
        execution.setValidBefore(before);
        execution.setValidAfter(false);
        execution.setResultNote("成绩作废，已回写成绩档案 valid=false");
        executionMapper.insert(execution);
        c.setScoreWriteback(true);
    }

    /** 禁考生效：入禁考名册，报名导入按证件号拦截。 */
    private void applyBan(DisciplineCase c, String operator, LocalDateTime now) {
        ExamBan ban = new ExamBan();
        ban.setRegistrationId(c.getRegistrationId());
        ban.setIdNo(c.getIdNo());
        ban.setName(c.getName());
        ban.setCaseId(c.getId());
        ban.setCaseNo(c.getCaseNo());
        ban.setReason("违纪案件 " + c.getCaseNo() + " 处理决定：" + decisionTypeText(c.getDecisionType()));
        ban.setBanFrom(LocalDate.now());
        ban.setBanUntil(c.getBanUntil());
        ban.setStatus(DisciplineConstants.BAN_ACTIVE);
        ban.setOperator(operator);
        banMapper.insert(ban);

        DisciplineExecution execution = new DisciplineExecution();
        execution.setCaseId(c.getId());
        execution.setBatchId(c.getBatchId());
        execution.setRegistrationId(c.getRegistrationId());
        execution.setRegNo(c.getRegNo());
        execution.setActionType(DisciplineConstants.EXEC_BAN_APPLY);
        execution.setApplyStatus(DisciplineConstants.APPLY_DONE);
        execution.setBanFrom(ban.getBanFrom());
        execution.setBanUntil(ban.getBanUntil());
        execution.setResultNote("禁考 " + c.getBanMonths() + " 个月，已入禁考名册（至 " + ban.getBanUntil() + "）");
        execution.setOperator(operator);
        execution.setExecutedAt(now);
        executionMapper.insert(execution);
    }

    // ==================== 撤销立案走复核 ====================

    @Transactional
    public DisciplineReview applyRevoke(Long caseId, RevokeReviewRequest request) {
        DisciplineCase c = requireCase(caseId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        if (!DisciplineConstants.CASE_DECIDED.equals(c.getStatus())
                && !DisciplineConstants.CASE_CLOSED.equals(c.getStatus())) {
            throw new BusinessException("CASE_STATUS_INVALID",
                    "案件 " + c.getCaseNo() + " 当前状态 " + c.getStatus()
                            + "，仅已决定（DECIDED）/ 已闭环（CLOSED）可申请撤销复核");
        }
        if (request.getApplyReason() == null || request.getApplyReason().trim().isEmpty()) {
            throw new BusinessException("REVOKE_REASON_REQUIRED", "撤销立案必须填写申请理由");
        }
        Long pending = reviewMapper.selectCount(new QueryWrapper<DisciplineReview>()
                .eq("case_id", c.getId())
                .eq("status", DisciplineConstants.REVIEW_PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException("REVIEW_ALREADY_PENDING", "案件已有待处理的撤销复核申请，请勿重复提交");
        }

        DisciplineReview review = new DisciplineReview();
        review.setCaseId(c.getId());
        review.setReviewNo(nextReviewNo(now));
        review.setStatus(DisciplineConstants.REVIEW_PENDING);
        review.setApplicantCode(request.getApplicantCode().trim());
        review.setApplicantName(trimToNull(request.getApplicantName()));
        review.setApplyReason(request.getApplyReason().trim());
        review.setAppliedAt(now);
        review.setPrevStatus(c.getStatus());
        reviewMapper.insert(review);

        String prevStatus = c.getStatus();
        c.setStatus(DisciplineConstants.CASE_REVIEWING);
        c.setCurrentVersion(c.getCurrentVersion() + 1);
        c.setActiveReviewId(review.getId());
        c.setOperator(operator);
        caseMapper.updateById(c);
        // 复核期间原决定不停止执行；通过才回滚后果，驳回维持原决定
        appendVersion(c, DisciplineConstants.CASE_REVIEWING, DisciplineConstants.CHANGE_REVOKE_APPLY,
                c.getDecisionType(), "撤销立案复核申请（原状态 " + prevStatus + "，复核期间不停止执行）："
                        + request.getApplyReason().trim(), operator, now);
        return review;
    }

    @Transactional
    public DisciplineReview concludeReview(Long reviewId, DisciplineReviewConclusionRequest request) {
        DisciplineReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "撤销复核申请不存在：id=" + reviewId);
        }
        if (!DisciplineConstants.REVIEW_PENDING.equals(review.getStatus())) {
            throw new BusinessException("REVIEW_STATUS_INVALID",
                    "复核申请 " + review.getReviewNo() + " 当前状态 " + review.getStatus() + "，已处理");
        }
        DisciplineCase c = requireCase(review.getCaseId());
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (request.getReviewerCode() == null || request.getReviewerCode().trim().isEmpty()) {
            throw new BusinessException("REVIEWER_REQUIRED", "复核人编码不能为空");
        }
        if (request.getReviewerCode().trim().equals(review.getApplicantCode())) {
            throw new BusinessException("REVIEWER_SAME_AS_APPLICANT",
                    "复核人不能是撤销申请人本人（须第二人复核）：" + request.getReviewerCode());
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String comment = request.getComment() == null ? null : request.getComment().trim();
        if (!approved && (comment == null || comment.isEmpty())) {
            throw new BusinessException("REVIEW_REJECT_COMMENT_REQUIRED", "复核驳回必须填写复核意见");
        }

        review.setReviewerCode(request.getReviewerCode().trim());
        review.setReviewerName(trimToNull(request.getReviewerName()));
        review.setReviewComment(comment);
        review.setReviewedAt(now);

        if (approved) {
            review.setStatus(DisciplineConstants.REVIEW_APPROVED);
            reviewMapper.updateById(review);
            rollbackConsequences(c, operator, now);

            c.setStatus(DisciplineConstants.CASE_REVOKED);
            c.setCurrentVersion(c.getCurrentVersion() + 1);
            seal(c, now);
            c.setOperator(operator);
            caseMapper.updateById(c);
            // 清空活动复核指针（updateById 默认忽略 null，显式置空）
            caseMapper.update(null, new UpdateWrapper<DisciplineCase>()
                    .eq("id", c.getId()).set("active_review_id", null));
            appendVersion(c, DisciplineConstants.CASE_REVOKED, DisciplineConstants.CHANGE_REVOKE_APPROVE,
                    c.getDecisionType(), "复核通过，撤销立案，已落地后果全部回滚，案件封存", operator, now);
            notifyStudent(c, "DISCIPLINE_REVOKED", "违纪案件撤销通知",
                    String.format("您的违纪案件 %s 经复核予以撤销，已生效的处理后果已回滚（成绩恢复 / 禁考解除）。",
                            c.getCaseNo()), operator);
        } else {
            review.setStatus(DisciplineConstants.REVIEW_REJECTED);
            reviewMapper.updateById(review);

            String backTo = review.getPrevStatus();
            c.setStatus(backTo);
            c.setCurrentVersion(c.getCurrentVersion() + 1);
            c.setOperator(operator);
            caseMapper.updateById(c);
            // 清空活动复核指针（updateById 默认忽略 null，显式置空）
            caseMapper.update(null, new UpdateWrapper<DisciplineCase>()
                    .eq("id", c.getId()).set("active_review_id", null));
            appendVersion(c, backTo, DisciplineConstants.CHANGE_REVOKE_REJECT, c.getDecisionType(),
                    "复核驳回，维持原决定（回到 " + backTo + "）。复核意见：" + comment, operator, now);
            notifyStudent(c, "DISCIPLINE_REVIEW_REJECT", "违纪复核结果通知",
                    String.format("您对案件 %s 的撤销申请经复核予以驳回，维持原处理决定。", c.getCaseNo()),
                    operator);
        }
        return review;
    }

    /** 撤销通过：回滚已落地后果（禁考解除、成绩恢复、挂起回写取消），逐项落账只追加。 */
    private void rollbackConsequences(DisciplineCase c, String operator, LocalDateTime now) {
        if (Boolean.TRUE.equals(c.getBanFlag())) {
            List<ExamBan> bans = banMapper.selectList(new QueryWrapper<ExamBan>()
                    .eq("case_id", c.getId())
                    .eq("status", DisciplineConstants.BAN_ACTIVE));
            for (ExamBan ban : bans) {
                ban.setStatus(DisciplineConstants.BAN_LIFTED);
                ban.setLiftedAt(now);
                ban.setLiftReason("案件 " + c.getCaseNo() + " 复核通过撤销立案，解除禁考");
                banMapper.updateById(ban);

                DisciplineExecution lift = new DisciplineExecution();
                lift.setCaseId(c.getId());
                lift.setBatchId(c.getBatchId());
                lift.setRegistrationId(c.getRegistrationId());
                lift.setRegNo(c.getRegNo());
                lift.setActionType(DisciplineConstants.EXEC_BAN_LIFT);
                lift.setApplyStatus(DisciplineConstants.APPLY_DONE);
                lift.setBanFrom(ban.getBanFrom());
                lift.setBanUntil(ban.getBanUntil());
                lift.setResultNote("复核通过，禁考解除");
                lift.setOperator(operator);
                lift.setExecutedAt(now);
                executionMapper.insert(lift);
            }
        }
        if (Boolean.TRUE.equals(c.getInvalidateScore())) {
            List<DisciplineExecution> invalids = executionMapper.selectList(new QueryWrapper<DisciplineExecution>()
                    .eq("case_id", c.getId())
                    .eq("action_type", DisciplineConstants.EXEC_SCORE_INVALIDATE)
                    .orderByAsc("id"));
            for (DisciplineExecution invalid : invalids) {
                if (DisciplineConstants.APPLY_PENDING.equals(invalid.getApplyStatus())) {
                    invalid.setApplyStatus(DisciplineConstants.APPLY_CANCELED);
                    invalid.setResultNote("复核通过撤销立案，成绩档案仍未生成，待回写取消");
                    invalid.setExecutedAt(now);
                    executionMapper.updateById(invalid);
                } else if (DisciplineConstants.APPLY_DONE.equals(invalid.getApplyStatus())
                        && invalid.getScoreId() != null) {
                    ExamScore score = scoreMapper.selectById(invalid.getScoreId());
                    if (score != null && Boolean.FALSE.equals(score.getValid())) {
                        // 撤销违纪作废后：若另有生效的缺考无效裁定或其他案件的生效作废决定，仍维持无效，避免覆盖别口径
                        boolean stillInvalid = absenceMarkService.isInvalidated(score.getRegistrationId())
                                || hasOtherActiveVoidDecision(score.getRegistrationId(), c.getId());
                        boolean restored = !stillInvalid;
                        score.setValid(restored);
                        scoreMapper.updateById(score);

                        DisciplineExecution restore = new DisciplineExecution();
                        restore.setCaseId(c.getId());
                        restore.setBatchId(c.getBatchId());
                        restore.setRegistrationId(c.getRegistrationId());
                        restore.setRegNo(c.getRegNo());
                        restore.setActionType(DisciplineConstants.EXEC_SCORE_RESTORE);
                        restore.setApplyStatus(DisciplineConstants.APPLY_DONE);
                        restore.setScoreId(score.getId());
                        restore.setValidBefore(false);
                        restore.setValidAfter(restored);
                        restore.setResultNote(restored
                                ? "复核通过，成绩恢复有效，已回写成绩档案 valid=true"
                                : "复核通过撤销违纪作废，但另有生效的缺考无效裁定，成绩维持无效");
                        restore.setOperator(operator);
                        restore.setExecutedAt(now);
                        executionMapper.insert(restore);
                    }
                }
            }
            c.setScoreWriteback(false);
        }
    }

    /**
     * 是否还有其他案件对该学员的成绩作废仍然生效（已作废回写 DONE 且尚无 DONE 恢复）。
     * 复核中不停止执行，故原 CLOSED 转 REVIEWING 的案件仍算生效。
     */
    private boolean hasOtherActiveVoidDecision(Long registrationId, Long currentCaseId) {
        List<DisciplineExecution> invalids = executionMapper.selectList(new QueryWrapper<DisciplineExecution>()
                .eq("registration_id", registrationId)
                .eq("action_type", DisciplineConstants.EXEC_SCORE_INVALIDATE)
                .eq("apply_status", DisciplineConstants.APPLY_DONE)
                .ne("case_id", currentCaseId));
        for (DisciplineExecution invalid : invalids) {
            Long restored = executionMapper.selectCount(new QueryWrapper<DisciplineExecution>()
                    .eq("case_id", invalid.getCaseId())
                    .eq("action_type", DisciplineConstants.EXEC_SCORE_RESTORE)
                    .eq("apply_status", DisciplineConstants.APPLY_DONE)
                    .eq("valid_after", true));
            if (restored == null || restored == 0) {
                return true;
            }
        }
        return false;
    }

    // ==================== 超时自动催办 ====================

    /**
     * 催办扫描（定时任务每分钟调一次，幂等）：
     * - 临期：到期前 24 小时内的 FILED 案件，补一轮 DUE_SOON（每轮站内+邮件各一条）；
     * - 超时：已过期限的 FILED 案件，到期当日首轮 OVERDUE，此后每 24 小时一轮。
     */
    @Transactional
    public Map<String, Object> scanReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<DisciplineCase> filed = caseMapper.selectList(new QueryWrapper<DisciplineCase>()
                .eq("status", DisciplineConstants.CASE_FILED));
        List<Long> remindedCaseIds = new ArrayList<>();
        int rounds = 0;
        for (DisciplineCase c : filed) {
            LocalDateTime due = c.getDecisionDueAt();
            int overdueRound = maxRemindRound(c.getId(), DisciplineConstants.REMIND_OVERDUE);
            if (now.isAfter(due) || now.isEqual(due)) {
                // 超时第 N 轮的触发钟点：dueAt + (N-1)*24h（首轮即到期钟点）
                long nextRound = overdueRound + 1L;
                LocalDateTime nextAt = due.plusHours((nextRound - 1) * DisciplineConstants.OVERDUE_REMIND_EVERY_HOURS);
                if (!now.isBefore(nextAt)) {
                    fireReminder(c, (int) nextRound, DisciplineConstants.REMIND_OVERDUE, now);
                    remindedCaseIds.add(c.getId());
                    rounds++;
                }
            } else if (!now.isBefore(due.minusHours(DisciplineConstants.DUE_SOON_HOURS))) {
                // 临期只有一轮（轮次号 0，站内+邮件各一条）
                Long dueSoonRows = reminderMapper.selectCount(new QueryWrapper<DisciplineReminder>()
                        .eq("case_id", c.getId())
                        .eq("remind_type", DisciplineConstants.REMIND_DUE_SOON));
                if (dueSoonRows == null || dueSoonRows == 0) {
                    fireReminder(c, 0, DisciplineConstants.REMIND_DUE_SOON, now);
                    remindedCaseIds.add(c.getId());
                    rounds++;
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("roundCount", rounds);
        result.put("caseIds", remindedCaseIds);
        result.put("scannedAt", now.toString());
        return result;
    }

    /** 某类催办已发到第几轮（每轮站内+邮件各一条，按轮次号取大值；无记录为 0）。 */
    private int maxRemindRound(Long caseId, String remindType) {
        List<DisciplineReminder> rows = reminderMapper.selectList(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", caseId)
                .eq("remind_type", remindType)
                .orderByDesc("remind_round"));
        return rows.isEmpty() ? 0 : rows.get(0).getRemindRound();
    }

    private void fireReminder(DisciplineCase c, int round, String remindType, LocalDateTime now) {
        String title;
        String content;
        if (DisciplineConstants.REMIND_DUE_SOON.equals(remindType)) {
            title = "违纪案件处理决定临期提醒";
            content = String.format("案件 %s（%s，%s）处理决定期限为 %s，即将到期，请尽快作出处理决定。",
                    c.getCaseNo(), c.getRegNo(), violationTypeText(c.getViolationType()),
                    c.getDecisionDueAt().format(TEXT_TS));
        } else {
            title = "违纪案件处理决定超时催办（第 " + round + " 轮）";
            content = String.format("案件 %s（%s，%s）处理决定期限 %s 已超时，请立即作出处理决定。",
                    c.getCaseNo(), c.getRegNo(), violationTypeText(c.getViolationType()),
                    c.getDecisionDueAt().format(TEXT_TS));
        }
        // 站内 + 邮件双通道，同一轮各一条（唯一约束 case+round+channel 兜底）
        insertReminder(c, round, remindType, DisciplineConstants.CHANNEL_IN_SITE, title, content, now);
        insertReminder(c, round, remindType, DisciplineConstants.CHANNEL_EMAIL, title, content, now);
    }

    private void insertReminder(DisciplineCase c, int round, String remindType, String channel,
                                String title, String content, LocalDateTime now) {
        DisciplineReminder reminder = new DisciplineReminder();
        reminder.setCaseId(c.getId());
        reminder.setRemindRound(round);
        reminder.setRemindType(remindType);
        reminder.setChannel(channel);
        reminder.setReceiverCode(c.getHandlerCode());
        reminder.setReceiverName(c.getHandlerName());
        reminder.setTitle(title);
        reminder.setContent(content);
        reminder.setDueAt(c.getDecisionDueAt());
        reminder.setOperator("system");
        reminder.setRemindedAt(now);
        reminderMapper.insert(reminder);
    }

    // ==================== 内部查询（封存记录仅授权岗） ====================

    @Transactional(readOnly = true)
    public List<DisciplineCase> list(String status, String regNo, boolean includeSealed) {
        QueryWrapper<DisciplineCase> wrapper = new QueryWrapper<DisciplineCase>()
                .orderByDesc("reported_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        if (!includeSealed) {
            wrapper.eq("sealed", false);
        }
        return caseMapper.selectList(wrapper);
    }

    /** 封存档案：仅授权岗调用（控制器链路由 roles[dossier] 兜底）。 */
    @Transactional(readOnly = true)
    public List<DisciplineCase> listSealed(String regNo) {
        QueryWrapper<DisciplineCase> wrapper = new QueryWrapper<DisciplineCase>()
                .eq("sealed", true)
                .orderByDesc("sealed_at", "id");
        if (regNo != null && !regNo.trim().isEmpty()) {
            wrapper.eq("reg_no", regNo.trim());
        }
        return caseMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public DisciplineCase get(Long caseId, boolean includeSealed) {
        DisciplineCase c = requireCase(caseId);
        if (Boolean.TRUE.equals(c.getSealed()) && !includeSealed) {
            throw new BusinessException("CASE_SEALED_FORBIDDEN",
                    "案件 " + c.getCaseNo() + " 已封存，仅授权岗（dossier）可查");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<DisciplineCaseVersion> versions(Long caseId, boolean includeSealed) {
        get(caseId, includeSealed);
        return versionMapper.selectList(new QueryWrapper<DisciplineCaseVersion>()
                .eq("case_id", caseId)
                .orderByAsc("version_no"));
    }

    @Transactional(readOnly = true)
    public List<DisciplineEvidence> evidences(Long caseId, boolean includeSealed) {
        get(caseId, includeSealed);
        return evidenceMapper.selectList(new QueryWrapper<DisciplineEvidence>()
                .eq("case_id", caseId)
                .orderByAsc("id"));
    }

    @Transactional(readOnly = true)
    public List<DisciplineExecution> executions(Long caseId, boolean includeSealed) {
        get(caseId, includeSealed);
        return executionMapper.selectList(new QueryWrapper<DisciplineExecution>()
                .eq("case_id", caseId)
                .orderByAsc("id"));
    }

    @Transactional(readOnly = true)
    public List<DisciplineReview> reviews(Long caseId, boolean includeSealed) {
        get(caseId, includeSealed);
        return reviewMapper.selectList(new QueryWrapper<DisciplineReview>()
                .eq("case_id", caseId)
                .orderByDesc("id"));
    }

    @Transactional(readOnly = true)
    public List<DisciplineReminder> reminders(Long caseId, boolean includeSealed) {
        get(caseId, includeSealed);
        return reminderMapper.selectList(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", caseId)
                .orderByAsc("remind_round", "channel"));
    }

    public DisciplineReview requireReview(Long reviewId) {
        DisciplineReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "撤销复核申请不存在：id=" + reviewId);
        }
        return review;
    }

    // ==================== 学员门户（脱敏） ====================

    /** 学员查自己案子：只按报名号返回本人案件，证件号/内部人员/证据引用/案情详述一律不回。 */
    @Transactional(readOnly = true)
    public List<CasePortalView> portalCases(String regNo) {
        if (regNo == null || regNo.trim().isEmpty()) {
            throw new BusinessException("REG_NO_REQUIRED", "查询须提供报名号");
        }
        List<DisciplineCase> cases = caseMapper.selectList(new QueryWrapper<DisciplineCase>()
                .eq("reg_no", regNo.trim())
                .orderByDesc("reported_at", "id"));
        List<CasePortalView> views = new ArrayList<>();
        for (DisciplineCase c : cases) {
            views.add(toPortalView(c));
        }
        return views;
    }

    private CasePortalView toPortalView(DisciplineCase c) {
        CasePortalView v = new CasePortalView();
        v.setCaseNo(c.getCaseNo());
        v.setStatus(c.getStatus());
        v.setStatusText(caseStatusText(c.getStatus()));
        v.setViolationType(c.getViolationType());
        v.setViolationTypeText(violationTypeText(c.getViolationType()));
        v.setTitle(c.getTitle());
        v.setDecisionType(c.getDecisionType());
        v.setDecisionTypeText(c.getDecisionType() == null ? null : decisionTypeText(c.getDecisionType()));
        v.setDecidedAt(c.getDecidedAt() == null ? null : c.getDecidedAt().format(TEXT_TS));
        v.setDecisionDueAt(c.getDecisionDueAt() == null ? null : c.getDecisionDueAt().format(TEXT_TS));
        v.setOverdue(DisciplineConstants.CASE_FILED.equals(c.getStatus())
                && LocalDateTime.now().isAfter(c.getDecisionDueAt()));
        v.setExecutedAt(c.getExecutedAt() == null ? null : c.getExecutedAt().format(TEXT_TS));
        v.setFiledAt(c.getCreateTime() == null ? null : c.getCreateTime().format(TEXT_TS));
        v.setSealedAt(c.getSealedAt() == null ? null : c.getSealedAt().format(TEXT_TS));
        v.setEvidenceCount(c.getEvidenceCount());
        if (c.getActiveReviewId() != null) {
            DisciplineReview review = reviewMapper.selectById(c.getActiveReviewId());
            if (review != null) {
                v.setReviewStatus(review.getStatus());
                v.setReviewStatusText(reviewStatusText(review.getStatus()));
            }
        }
        return v;
    }

    // ==================== 文案 / 工具 ====================

    public static String caseStatusText(String status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case DisciplineConstants.CASE_FILED: return "已立案待决定";
            case DisciplineConstants.CASE_DECIDED: return "已决定待执行";
            case DisciplineConstants.CASE_CLOSED: return "已执行闭环（已封存）";
            case DisciplineConstants.CASE_REVIEWING: return "复核中";
            case DisciplineConstants.CASE_REVOKED: return "已撤销（已封存）";
            default: return status;
        }
    }

    public static String violationTypeText(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case DisciplineConstants.VIOLATION_CHEAT: return "作弊";
            case DisciplineConstants.VIOLATION_IMPERSONATE: return "替考";
            case DisciplineConstants.VIOLATION_PROHIBITED: return "携带违禁品";
            case DisciplineConstants.VIOLATION_DISORDER: return "扰乱秩序";
            case DisciplineConstants.VIOLATION_OTHER: return "其他违纪";
            default: return type;
        }
    }

    public static String decisionTypeText(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case DisciplineConstants.DECISION_WARNING: return "警告";
            case DisciplineConstants.DECISION_SCORE_VOID: return "成绩作废";
            case DisciplineConstants.DECISION_BAN: return "禁考";
            case DisciplineConstants.DECISION_SCORE_VOID_BAN: return "成绩作废并禁考";
            default: return type;
        }
    }

    public static String reviewStatusText(String status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case DisciplineConstants.REVIEW_PENDING: return "待复核";
            case DisciplineConstants.REVIEW_APPROVED: return "复核通过（已撤销）";
            case DisciplineConstants.REVIEW_REJECTED: return "复核驳回（维持原决定）";
            default: return status;
        }
    }

    DisciplineCase requireCase(Long id) {
        if (id == null) {
            throw new BusinessException("CASE_ID_REQUIRED", "案件 id 不能为空");
        }
        DisciplineCase c = caseMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("CASE_NOT_FOUND", "违纪案件不存在：id=" + id);
        }
        return c;
    }

    private Registration requireRegistration(Long batchId, String regNo) {
        if (regNo == null || regNo.trim().isEmpty()) {
            throw new BusinessException("REG_NO_REQUIRED", "报名号不能为空");
        }
        Registration reg = registrationService.findByRegNo(regNo.trim());
        if (reg == null) {
            throw new BusinessException("REG_NOT_FOUND", "名册中查无此报名号：" + regNo.trim());
        }
        if (!reg.getBatchId().equals(batchId)) {
            throw new BusinessException("REG_NOT_IN_BATCH",
                    "报名号不属于本批次（属于批次 id=" + reg.getBatchId() + "）：" + regNo.trim());
        }
        return reg;
    }

    private String normalizeViolationType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new BusinessException("VIOLATION_TYPE_REQUIRED", "违纪类型不能为空");
        }
        String normalized = type.trim().toUpperCase();
        if (!VIOLATION_TYPES.contains(normalized)) {
            throw new BusinessException("VIOLATION_TYPE_INVALID",
                    "违纪类型仅支持 CHEAT 作弊 / IMPERSONATE 替考 / PROHIBITED 携带违禁品 / DISORDER 扰乱秩序 / OTHER 其他："
                            + type);
        }
        return normalized;
    }

    private int normalizeDueDays(Integer dueDays) {
        if (dueDays == null) {
            return DisciplineConstants.DEFAULT_DECISION_DUE_DAYS;
        }
        if (dueDays <= 0 || dueDays > 365) {
            throw new BusinessException("DUE_DAYS_INVALID", "处理决定期限天数须在 1~365 之间：" + dueDays);
        }
        return dueDays;
    }

    private void seal(DisciplineCase c, LocalDateTime now) {
        c.setSealed(true);
        c.setSealedAt(now);
    }

    private void appendVersion(DisciplineCase c, String status, String changeType, String decisionType,
                               String reason, String operator, LocalDateTime now) {
        DisciplineCaseVersion version = new DisciplineCaseVersion();
        version.setCaseId(c.getId());
        version.setVersionNo(c.getCurrentVersion());
        version.setStatus(status);
        version.setChangeType(changeType);
        version.setDecisionType(decisionType);
        version.setChangeReason(reason);
        version.setOperator(operator);
        version.setChangedAt(now);
        versionMapper.insert(version);
    }

    private void notifyStudent(DisciplineCase c, String type, String title, String content, String operator) {
        notificationService.notify(c.getBatchId(), c.getRegistrationId(), c.getRegNo(), c.getName(),
                type, title, content, null, operator);
    }

    /** 案件编号：WJ + 立案日期 8 位 + 4 位流水（按日递增，唯一约束兜底）。 */
    private String nextCaseNo(LocalDateTime now) {
        String prefix = "WJ" + now.format(NO_DAY) + "-";
        int max = 0;
        for (DisciplineCase c : caseMapper.selectList(new QueryWrapper<DisciplineCase>()
                .likeRight("case_no", prefix))) {
            if (c.getCaseNo() != null && c.getCaseNo().startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(c.getCaseNo().substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    /** 复核申请号：FH + 申请日期 8 位 + 4 位流水。 */
    private String nextReviewNo(LocalDateTime now) {
        String prefix = "FH" + now.format(NO_DAY) + "-";
        int max = 0;
        List<DisciplineReview> exist = reviewMapper.selectList(new QueryWrapper<DisciplineReview>()
                .likeRight("review_no", prefix));
        for (DisciplineReview r : exist) {
            if (r.getReviewNo() != null && r.getReviewNo().startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(r.getReviewNo().substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则流水忽略
                }
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimOr(String s, String fallback) {
        String t = trimToNull(s);
        return t == null ? fallback : t;
    }
}
