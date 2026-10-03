package com.evops.discipline.controller;

import com.evops.common.ApiResponse;
import com.evops.discipline.service.DisciplineCaseService;
import com.evops.discipline.vo.CasePortalView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员案件查询门户（任意已认证账号可访问，只按报名号查本人案件，脱敏展示）：
 * 不回证件号、案情详述、上报人/承办人/决定人/复核人、证据文件名与引用、催办与执行内部明细。
 */
@RestController
@RequestMapping("/api/discipline/portal")
public class CasePortalController {

    private final DisciplineCaseService caseService;

    public CasePortalController(DisciplineCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/cases")
    public ApiResponse<List<CasePortalView>> myCases(@RequestParam String regNo) {
        return ApiResponse.ok(caseService.portalCases(regNo));
    }
}
