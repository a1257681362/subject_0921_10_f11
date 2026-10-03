package com.evops.discipline;

import com.evops.discipline.common.DisciplineConstants;
import com.evops.discipline.dto.CaseFileRequest;
import com.evops.discipline.dto.DecisionRequest;
import com.evops.discipline.dto.DisciplineReviewConclusionRequest;
import com.evops.discipline.dto.RevokeReviewRequest;
import com.evops.discipline.entity.DisciplineCase;
import com.evops.discipline.entity.DisciplineEvidence;
import com.evops.discipline.entity.DisciplineExecution;
import com.evops.discipline.entity.DisciplineReminder;
import com.evops.discipline.entity.DisciplineReview;
import com.evops.discipline.mapper.DisciplineCaseMapper;
import com.evops.discipline.mapper.DisciplineReminderMapper;
import com.evops.discipline.service.DisciplineCaseService;
import com.evops.discipline.service.DisciplineScoreBridge;
import com.evops.discipline.vo.CasePortalView;
import com.evops.common.BusinessException;
import com.evops.exam.dto.RegistrationImportRequest;
import com.evops.exam.dto.RegistrationRequest;
import com.evops.exam.entity.ExamBan;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBanMapper;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.service.RegistrationService;
import com.evops.exam.vo.ActionPreview;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.mapper.ExamScoreMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 违纪立案端到端：
 * 附证据立案（限期）→ 决定（类型对应后果）→ 执行回写成绩档案/禁考名册、闭环封存
 * → 超时催办 → 撤销走复核（通过回滚 / 驳回维持）→ 学员门户脱敏 → 封存仅授权口径 → 禁考报名拦截。
 */
@SpringBootTest(properties = "evops.discipline.remind-cron=0 0 0 1 1 *")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DisciplineFlowTest {

    @Autowired
    private DisciplineCaseService caseService;
    @Autowired
    private DisciplineScoreBridge scoreBridge;
    @Autowired
    private DisciplineCaseMapper caseMapper;
    @Autowired
    private DisciplineReminderMapper reminderMapper;
    @Autowired
    private ExamBanMapper banMapper;
    @Autowired
    private ExamScoreMapper scoreMapper;
    @Autowired
    private RegistrationMapper registrationMapper;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private ExamSiteMapper siteMapper;
    @Autowired
    private ExamBatchMapper batchMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static volatile ExamBatch sharedBatch;

    private ExamBatch batch() {
        if (sharedBatch != null) {
            return sharedBatch;
        }
        ExamSite site = new ExamSite();
        site.setCode("SWJ01");
        site.setName("违纪考点");
        site.setStatus("ENABLED");
        siteMapper.insert(site);
        ExamBatch b = new ExamBatch();
        b.setCode("BWJ01");
        b.setName("违纪批次");
        b.setSiteId(site.getId());
        b.setProjectCode("DEFAULT");
        b.setExamDate(LocalDate.of(2026, 10, 2));
        b.setStatus("CREATED");
        batchMapper.insert(b);
        sharedBatch = b;
        return b;
    }

    private Registration student(String regNo, String idNo) {
        Registration reg = new Registration();
        reg.setRegNo(regNo);
        reg.setName("考生" + regNo);
        reg.setIdType("ID_CARD");
        reg.setIdNo(idNo);
        reg.setBatchId(batch().getId());
        reg.setStatus("REGISTERED");
        reg.setCorrectionCount(0);
        registrationMapper.insert(reg);
        return reg;
    }

    private CaseFileRequest.EvidenceItem evidence(String type, String fileName) {
        CaseFileRequest.EvidenceItem e = new CaseFileRequest.EvidenceItem();
        e.setEvidenceType(type);
        e.setFileName(fileName);
        e.setFileRef("oss://discipline/" + fileName);
        e.setDescription("证据说明-" + fileName);
        return e;
    }

    private CaseFileRequest fileRequest(String regNo, Integer dueDays) {
        CaseFileRequest req = new CaseFileRequest();
        req.setBatchId(batch().getId());
        req.setRegNo(regNo);
        req.setViolationType("CHEAT");
        req.setTitle("考场夹带纸条");
        req.setDescription("考试中发现携带与考试相关的纸质材料");
        req.setReporterCode("JK001");
        req.setReporterName("监考甲");
        req.setOccurredAt(LocalDateTime.now().minusMinutes(10)
                .toString().replace('T', ' ').substring(0, 19));
        req.setDueDays(dueDays);
        req.setEvidences(new ArrayList<>(Arrays.asList(
                evidence("PHOTO", "evidence-" + regNo + "-1.jpg"),
                evidence("DOC", "笔录-" + regNo + ".pdf"))));
        return req;
    }

    private DecisionRequest decision(String type, Integer banMonths) {
        DecisionRequest d = new DecisionRequest();
        d.setDecisionType(type);
        d.setReason("事实清楚、证据充分，依据考试违纪处理规定作出决定");
        d.setBanMonths(banMonths);
        d.setMakerCode("LD001");
        d.setMakerName("考务负责人");
        return d;
    }

    private ExamScore insertScore(Registration reg, boolean valid) {
        ExamScore score = new ExamScore();
        score.setBatchId(reg.getBatchId());
        score.setRegistrationId(reg.getId());
        score.setRegNo(reg.getRegNo());
        score.setName(reg.getName());
        score.setPaperScore(new BigDecimal("60.00"));
        score.setGrade("合格");
        score.setGradedAt(LocalDateTime.now());
        score.setStatus("RECORDED");
        score.setValid(valid);
        scoreMapper.insert(score);
        return score;
    }

    @Test
    void fileRequiresEvidenceAndSetsDue() {
        student("WJ001", "ID-WJ001");
        CaseFileRequest req = fileRequest("WJ001", 7);
        req.setEvidences(Collections.emptyList());
        // 无证据不立案
        BusinessException ex = assertThrows(BusinessException.class, () -> caseService.fileCase(req));
        assertEquals("EVIDENCE_REQUIRED", ex.getCode());

        req.setEvidences(new ArrayList<>(Collections.singletonList(evidence("PHOTO", "only.jpg"))));
        DisciplineCase c = caseService.fileCase(req);
        assertTrue(c.getCaseNo().startsWith("WJ"));
        assertEquals(DisciplineConstants.CASE_FILED, c.getStatus());
        assertEquals(Integer.valueOf(1), c.getCurrentVersion());
        assertEquals(Integer.valueOf(1), c.getEvidenceCount());
        // 立案即定处理决定期限：约为 7 天后
        assertTrue(c.getDecisionDueAt().isAfter(LocalDateTime.now().plusDays(6).plusHours(23)));
        assertTrue(c.getDecisionDueAt().isBefore(LocalDateTime.now().plusDays(7).plusMinutes(1)));
        assertFalse(c.getSealed());
        // 承办人缺省为上报人
        assertEquals("JK001", c.getHandlerCode());

        // 决定作出后证据固定，不能再补充
        caseService.decide(c.getId(), decision("WARNING", null));
        BusinessException fixed = assertThrows(BusinessException.class,
                () -> caseService.addEvidence(c.getId(), evidence("PHOTO", "late.jpg")));
        assertEquals("CASE_NOT_FILED", fixed.getCode());
    }

    @Test
    void decisionBanRequiresMonths() {
        student("WJ002", "ID-WJ002");
        DisciplineCase c = caseService.fileCase(fileRequest("WJ002", null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.decide(c.getId(), decision("BAN", null)));
        assertEquals("BAN_MONTHS_REQUIRED", ex.getCode());
    }

    @Test
    void execute_writesBackScoreAndBan_thenSealed() {
        Registration reg = student("WJ003", "ID-WJ003");
        DisciplineCase c = caseService.fileCase(fileRequest("WJ003", 7));
        c = caseService.decide(c.getId(), decision("SCORE_VOID_BAN", 24));
        assertEquals(Boolean.TRUE, c.getInvalidateScore());
        assertEquals(Boolean.TRUE, c.getBanFlag());
        assertNotNull(c.getBanUntil());

        // 成绩档案尚未生成：执行时成绩回写挂 PENDING，禁考照常入册，案件闭环封存
        DisciplineCase closed = caseService.execute(c.getId());
        assertEquals(DisciplineConstants.CASE_CLOSED, closed.getStatus());
        assertTrue(closed.getSealed());
        assertNotNull(closed.getSealedAt());
        assertFalse(closed.getScoreWriteback());

        List<DisciplineExecution> execs = caseService.executions(closed.getId(), true);
        DisciplineExecution scoreExec = exec(execs, DisciplineConstants.EXEC_SCORE_INVALIDATE);
        assertEquals(DisciplineConstants.APPLY_PENDING, scoreExec.getApplyStatus());
        DisciplineExecution banExec = exec(execs, DisciplineConstants.EXEC_BAN_APPLY);
        assertEquals(DisciplineConstants.APPLY_DONE, banExec.getApplyStatus());

        ExamBan ban = banMapper.selectOne(new QueryWrapper<ExamBan>()
                .eq("case_id", closed.getId()).eq("status", DisciplineConstants.BAN_ACTIVE));
        assertNotNull(ban);
        assertEquals("ID-WJ003", ban.getIdNo());

        // 未封存口径看不到该案件
        assertThrows(BusinessException.class, () -> caseService.get(closed.getId(), false));
        assertTrue(caseService.list(null, "WJ003", false).isEmpty());
        // 封存档案授权口径可调阅
        assertEquals(1, caseService.listSealed("WJ003").size());
        assertNotNull(caseService.get(closed.getId(), true));

        // 成绩档案随后生成：录入事件触发补回写 valid=false
        ExamScore score = insertScore(reg, true);
        scoreBridge.onScoreImported(new com.evops.grade.event.ScoreImportedEvent(Collections.singletonList(score)));
        ExamScore after = scoreMapper.selectById(score.getId());
        assertFalse(after.getValid());
        DisciplineExecution scoreExecDone = exec(caseService.executions(closed.getId(), true),
                DisciplineConstants.EXEC_SCORE_INVALIDATE);
        assertEquals(DisciplineConstants.APPLY_DONE, scoreExecDone.getApplyStatus());
        assertTrue(caseService.get(closed.getId(), true).getScoreWriteback());

        // 禁考名册拦截后续报名（同证件号，新批次）
        BusinessException banned = assertThrows(BusinessException.class,
                () -> registrationService.importRegistrations(bannedImportRequest("ID-WJ003", "WJ903")));
        assertEquals("REG_BANNED", banned.getCode());
        ActionPreview preview = registrationService.previewImport(bannedImportRequest("ID-WJ003", "WJ904"));
        assertEquals(0, preview.getActionableCount());
        assertTrue(preview.getItems().get(0).getNote().contains("禁考"));
    }

    private RegistrationImportRequest bannedImportRequest(String idNo, String regNo) {
        RegistrationRequest r = new RegistrationRequest();
        r.setRegNo(regNo);
        r.setName("新生" + regNo);
        r.setIdNo(idNo);
        RegistrationImportRequest req = new RegistrationImportRequest();
        req.setBatchId(batch().getId());
        // 预检只看禁考，报名号唯一性用不同号规避
        req.setRegistrations(Collections.singletonList(r));
        return req;
    }

    private DisciplineExecution exec(List<DisciplineExecution> execs, String actionType) {
        for (DisciplineExecution e : execs) {
            if (actionType.equals(e.getActionType())) {
                return e;
            }
        }
        throw new IllegalStateException("执行记录不存在：" + actionType);
    }

    @Test
    void revokeApproved_afterClosed_rollsBackEverything() {
        Registration reg = student("WJ005", "ID-WJ005");
        DisciplineCase c = caseService.fileCase(fileRequest("WJ005", 7));
        caseService.decide(c.getId(), decision("SCORE_VOID_BAN", 12));
        caseService.execute(c.getId());
        ExamScore score = insertScore(reg, true);
        scoreBridge.onScoreImported(new com.evops.grade.event.ScoreImportedEvent(Collections.singletonList(score)));
        assertFalse(scoreMapper.selectById(score.getId()).getValid());

        // 撤销申请进入复核中
        RevokeReviewRequest apply = new RevokeReviewRequest();
        apply.setApplicantCode("LD001");
        apply.setApplicantName("考务负责人");
        apply.setApplyReason("新证据证明非本人作弊，申请撤销立案");
        DisciplineReview review = caseService.applyRevoke(c.getId(), apply);
        assertEquals(DisciplineConstants.REVIEW_PENDING, review.getStatus());
        DisciplineCase reviewing = caseService.get(c.getId(), true);
        assertEquals(DisciplineConstants.CASE_REVIEWING, reviewing.getStatus());

        // 复核人不能是申请人本人
        DisciplineReviewConclusionRequest self = new DisciplineReviewConclusionRequest();
        self.setApproved(true);
        self.setReviewerCode("LD001");
        BusinessException same = assertThrows(BusinessException.class,
                () -> caseService.concludeReview(review.getId(), self));
        assertEquals("REVIEWER_SAME_AS_APPLICANT", same.getCode());

        // 第二人复核通过：撤销 + 回滚后果 + 封存
        DisciplineReviewConclusionRequest approve = new DisciplineReviewConclusionRequest();
        approve.setApproved(true);
        approve.setReviewerCode("LD002");
        approve.setReviewerName("复核负责人");
        caseService.concludeReview(review.getId(), approve);

        DisciplineCase revoked = caseService.get(c.getId(), true);
        assertEquals(DisciplineConstants.CASE_REVOKED, revoked.getStatus());
        assertTrue(revoked.getSealed());
        assertNull(revoked.getActiveReviewId());
        assertTrue(scoreMapper.selectById(score.getId()).getValid());
        assertEquals(DisciplineConstants.BAN_LIFTED,
                banMapper.selectOne(new QueryWrapper<ExamBan>().eq("case_id", c.getId())).getStatus());

        List<DisciplineExecution> execs = caseService.executions(c.getId(), true);
        assertNotNull(exec(execs, DisciplineConstants.EXEC_SCORE_RESTORE));
        assertNotNull(exec(execs, DisciplineConstants.EXEC_BAN_LIFT));
        // 解除后同证件号报名不再拦截
        registrationService.importRegistrations(bannedImportRequest("ID-WJ005", "WJ905"));

        // 轨迹留全：立案/决定/执行/撤销申请/复核通过共 5 版
        assertEquals(5, caseService.versions(c.getId(), true).size());
    }

    @Test
    void revokeRejected_backsToOriginalStatus() {
        student("WJ006", "ID-WJ006");
        DisciplineCase c = caseService.fileCase(fileRequest("WJ006", 7));
        caseService.decide(c.getId(), decision("WARNING", null));
        assertEquals(DisciplineConstants.CASE_DECIDED, caseService.get(c.getId(), false).getStatus());

        RevokeReviewRequest apply = new RevokeReviewRequest();
        apply.setApplicantCode("JK001");
        apply.setApplyReason("学员本人申诉，认为情节轻微");
        DisciplineReview review = caseService.applyRevoke(c.getId(), apply);

        // 驳回必须写意见
        DisciplineReviewConclusionRequest noComment = new DisciplineReviewConclusionRequest();
        noComment.setApproved(false);
        noComment.setReviewerCode("LD002");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.concludeReview(review.getId(), noComment));
        assertEquals("REVIEW_REJECT_COMMENT_REQUIRED", ex.getCode());

        // 驳回维持原决定，回到 DECIDED，不封存
        DisciplineReviewConclusionRequest reject = new DisciplineReviewConclusionRequest();
        reject.setApproved(false);
        reject.setReviewerCode("LD002");
        reject.setComment("违纪事实清楚，警告适当，维持原决定");
        caseService.concludeReview(review.getId(), reject);
        DisciplineCase back = caseService.get(c.getId(), false);
        assertEquals(DisciplineConstants.CASE_DECIDED, back.getStatus());
        assertFalse(back.getSealed());
        // 复核中不能重复提交申请，驳回后可再次申请
        RevokeReviewRequest again = new RevokeReviewRequest();
        again.setApplicantCode("JK001");
        again.setApplyReason("补充材料再申请");
        assertNotNull(caseService.applyRevoke(c.getId(), again));
    }

    @Test
    void reminders_dueSoonAndOverdueAreIdempotent() {
        student("WJ007", "ID-WJ007");
        DisciplineCase overdue = caseService.fileCase(fileRequest("WJ007", 7));
        overdue.setDecisionDueAt(LocalDateTime.now().minusHours(1));
        caseMapper.updateById(overdue);

        student("WJ008", "ID-WJ008");
        DisciplineCase dueSoon = caseService.fileCase(fileRequest("WJ008", 7));
        dueSoon.setDecisionDueAt(LocalDateTime.now().plusHours(2));
        caseMapper.updateById(dueSoon);

        student("WJ009", "ID-WJ009");
        DisciplineCase future = caseService.fileCase(fileRequest("WJ009", 7));

        caseService.scanReminders();

        List<DisciplineReminder> overdueRows = reminderMapper.selectList(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", overdue.getId()));
        assertEquals(2, overdueRows.size());
        assertEquals(DisciplineConstants.REMIND_OVERDUE, overdueRows.get(0).getRemindType());
        assertEquals(Integer.valueOf(1), overdueRows.get(0).getRemindRound());
        // 站内 + 邮件双通道
        assertTrue(overdueRows.stream().anyMatch(r -> DisciplineConstants.CHANNEL_IN_SITE.equals(r.getChannel())));
        assertTrue(overdueRows.stream().anyMatch(r -> DisciplineConstants.CHANNEL_EMAIL.equals(r.getChannel())));
        // 催办发给承办人（缺省上报人）
        assertEquals("JK001", overdueRows.get(0).getReceiverCode());

        List<DisciplineReminder> dueSoonRows = reminderMapper.selectList(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", dueSoon.getId()));
        assertEquals(2, dueSoonRows.size());
        assertEquals(DisciplineConstants.REMIND_DUE_SOON, dueSoonRows.get(0).getRemindType());

        // 期限还远：不催办
        assertEquals(0, reminderMapper.selectCount(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", future.getId())).intValue());

        // 再扫一遍：超时未到 24 小时间隔不重复催办，临期不重复
        caseService.scanReminders();
        assertEquals(2, reminderMapper.selectCount(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", overdue.getId())).intValue());
        assertEquals(2, reminderMapper.selectCount(new QueryWrapper<DisciplineReminder>()
                .eq("case_id", dueSoon.getId())).intValue());
    }

    @Test
    void portalIsMaskedAndStudentOnlySeesSelf() throws Exception {
        student("WJ010", "ID-WJ010");
        DisciplineCase c = caseService.fileCase(fileRequest("WJ010", 7));
        caseService.decide(c.getId(), decision("SCORE_VOID", null));

        List<CasePortalView> views = caseService.portalCases("WJ010");
        assertEquals(1, views.size());
        CasePortalView v = views.get(0);
        assertEquals(c.getCaseNo(), v.getCaseNo());
        assertEquals("已决定待执行", v.getStatusText());
        assertEquals("成绩作废", v.getDecisionTypeText());
        assertEquals(Integer.valueOf(2), v.getEvidenceCount());

        // 序列化后不含敏感字段：证件号、上报人/承办人/决定人、案情详述、证据引用
        String json = objectMapper.writeValueAsString(v);
        assertFalse(json.contains("idNo"));
        assertFalse(json.contains("ID-WJ010"));
        assertFalse(json.contains("reporter"));
        assertFalse(json.contains("handler"));
        assertFalse(json.contains("decisionMaker"));
        assertFalse(json.contains("description"));
        assertFalse(json.contains("fileRef"));
        assertFalse(json.contains("oss://"));

        // 查别人的报名号看不到本案
        assertTrue(caseService.portalCases("WJ999").isEmpty());
    }
}
