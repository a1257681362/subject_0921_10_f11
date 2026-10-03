package com.evops.discipline.controller;

import com.evops.common.ApiResponse;
import com.evops.discipline.entity.DisciplineCase;
import com.evops.discipline.entity.DisciplineCaseVersion;
import com.evops.discipline.entity.DisciplineEvidence;
import com.evops.discipline.entity.DisciplineExecution;
import com.evops.discipline.entity.DisciplineReminder;
import com.evops.discipline.entity.DisciplineReview;
import com.evops.discipline.service.DisciplineCaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 违纪封存档案（仅授权岗 dossier，链路由 Shiro roles[dossier] 兜底）：
 * 执行闭环（CLOSED）或复核撤销（REVOKED）即封存；封存记录只有授权岗能看，轨迹/证据/执行明细可完整调阅。
 */
@RestController
@RequestMapping("/api/discipline/sealed")
public class SealedCaseController {

    private final DisciplineCaseService caseService;

    public SealedCaseController(DisciplineCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/cases")
    public ApiResponse<List<DisciplineCase>> listSealed(@RequestParam(required = false) String regNo) {
        return ApiResponse.ok(caseService.listSealed(regNo));
    }

    @GetMapping("/cases/{id}")
    public ApiResponse<DisciplineCase> get(@PathVariable Long id) {
        return ApiResponse.ok(caseService.get(id, true));
    }

    @GetMapping("/cases/{id}/versions")
    public ApiResponse<List<DisciplineCaseVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(caseService.versions(id, true));
    }

    @GetMapping("/cases/{id}/evidences")
    public ApiResponse<List<DisciplineEvidence>> evidences(@PathVariable Long id) {
        return ApiResponse.ok(caseService.evidences(id, true));
    }

    @GetMapping("/cases/{id}/executions")
    public ApiResponse<List<DisciplineExecution>> executions(@PathVariable Long id) {
        return ApiResponse.ok(caseService.executions(id, true));
    }

    @GetMapping("/cases/{id}/reviews")
    public ApiResponse<List<DisciplineReview>> reviews(@PathVariable Long id) {
        return ApiResponse.ok(caseService.reviews(id, true));
    }

    @GetMapping("/cases/{id}/reminders")
    public ApiResponse<List<DisciplineReminder>> reminders(@PathVariable Long id) {
        return ApiResponse.ok(caseService.reminders(id, true));
    }
}
