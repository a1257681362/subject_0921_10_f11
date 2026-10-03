package com.evops.discipline.controller;

import com.evops.common.ApiResponse;
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
import com.evops.discipline.service.DisciplineCaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 违纪立案处置（内部管理，system 角色）：
 * 监考上报附证据立案 → 限期出处理决定（超时自动催办）→ 决定执行回写成绩档案/禁考名册 → 撤销走复核。
 * 注意：已封存（CLOSED/REVOKED）案件不在本接口返回，封存档案走 /api/discipline/sealed（授权岗 dossier）。
 */
@RestController
@RequestMapping("/api/discipline")
public class DisciplineCaseController {

    private final DisciplineCaseService caseService;

    public DisciplineCaseController(DisciplineCaseService caseService) {
        this.caseService = caseService;
    }

    // ---------- 立案 / 证据 ----------

    /** 监考上报附证据立案：立案即定处理决定期限（缺省 7 天），无证据不立案。 */
    @PostMapping("/cases")
    public ApiResponse<DisciplineCase> fileCase(@Valid @RequestBody CaseFileRequest request) {
        return ApiResponse.ok(caseService.fileCase(request));
    }

    /** 待决定阶段补充证据（只追加，决定作出后证据固定）。 */
    @PostMapping("/cases/{id}/evidences")
    public ApiResponse<DisciplineEvidence> addEvidence(@PathVariable Long id,
                                                       @Valid @RequestBody CaseFileRequest.EvidenceItem item) {
        return ApiResponse.ok(caseService.addEvidence(id, item));
    }

    // ---------- 决定 / 执行 ----------

    /** 限期作出处理决定：WARNING / SCORE_VOID / BAN / SCORE_VOID_BAN，禁考须带月数。 */
    @PostMapping("/cases/{id}/decision")
    public ApiResponse<DisciplineCase> decide(@PathVariable Long id,
                                              @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok(caseService.decide(id, request));
    }

    /** 执行闭环：成绩作废回写成绩档案（档案未生成挂待回写），禁考入册，执行后案件封存。 */
    @PostMapping("/cases/{id}/execute")
    public ApiResponse<DisciplineCase> execute(@PathVariable Long id) {
        return ApiResponse.ok(caseService.execute(id));
    }

    // ---------- 撤销立案走复核 ----------

    /** 撤销立案申请：DECIDED/CLOSED 可申请，进入 REVIEWING，复核期间不停止执行。 */
    @PostMapping("/cases/{id}/revoke-apply")
    public ApiResponse<DisciplineReview> applyRevoke(@PathVariable Long id,
                                                     @Valid @RequestBody RevokeReviewRequest request) {
        return ApiResponse.ok(caseService.applyRevoke(id, request));
    }

    /** 第二人复核结论：通过撤销并回滚后果；驳回维持原决定（复核人不能是申请人，驳回须写意见）。 */
    @PostMapping("/reviews/{reviewId}/conclusion")
    public ApiResponse<DisciplineReview> conclude(@PathVariable Long reviewId,
                                                  @Valid @RequestBody DisciplineReviewConclusionRequest request) {
        return ApiResponse.ok(caseService.concludeReview(reviewId, request));
    }

    // ---------- 催办 ----------

    /** 手动触发催办扫描（定时任务每分钟自动执行同一逻辑，幂等）。 */
    @PostMapping("/reminders/scan")
    public ApiResponse<Map<String, Object>> scanReminders() {
        return ApiResponse.ok(caseService.scanReminders());
    }

    // ---------- 查询（不含封存） ----------

    /** 办案清单：默认只回未封存案件；封存档案请到封存接口（授权岗）。 */
    @GetMapping("/cases")
    public ApiResponse<List<DisciplineCase>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String regNo) {
        return ApiResponse.ok(caseService.list(status, regNo, false));
    }

    @GetMapping("/cases/{id}")
    public ApiResponse<DisciplineCase> get(@PathVariable Long id) {
        return ApiResponse.ok(caseService.get(id, false));
    }

    /** 版本轨迹（立案/决定/执行/撤销全程只追加）。 */
    @GetMapping("/cases/{id}/versions")
    public ApiResponse<List<DisciplineCaseVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(caseService.versions(id, false));
    }

    @GetMapping("/cases/{id}/evidences")
    public ApiResponse<List<DisciplineEvidence>> evidences(@PathVariable Long id) {
        return ApiResponse.ok(caseService.evidences(id, false));
    }

    @GetMapping("/cases/{id}/executions")
    public ApiResponse<List<DisciplineExecution>> executions(@PathVariable Long id) {
        return ApiResponse.ok(caseService.executions(id, false));
    }

    @GetMapping("/cases/{id}/reviews")
    public ApiResponse<List<DisciplineReview>> reviews(@PathVariable Long id) {
        return ApiResponse.ok(caseService.reviews(id, false));
    }

    @GetMapping("/cases/{id}/reminders")
    public ApiResponse<List<DisciplineReminder>> reminders(@PathVariable Long id) {
        return ApiResponse.ok(caseService.reminders(id, false));
    }
}
