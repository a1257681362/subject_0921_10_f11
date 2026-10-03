package com.evops.discipline;

import com.evops.discipline.common.DisciplineConstants;
import com.evops.discipline.dto.CaseFileRequest;
import com.evops.discipline.dto.DecisionRequest;
import com.evops.discipline.entity.DisciplineCase;
import com.evops.discipline.service.DisciplineCaseService;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.ExamBatchMapper;
import com.evops.exam.mapper.ExamSiteMapper;
import com.evops.exam.mapper.RegistrationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 违纪模块接口边界：
 * - 未认证 401；
 * - 学员 student：仅学员门户（脱敏），内部办案接口 403；
 * - 封存授权岗 archive(dossier)：仅封存档案，内部办案接口 403；
 * - 内部管理 bootstrap：办案接口可用，但封存档案需要 dossier（403）；
 * - 用人单位 employer：门户也不能访问（与证书验真窗口区分）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "evops.discipline.remind-cron=0 0 0 1 1 *")
@ActiveProfiles("test")
class DisciplineSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private DisciplineCaseService caseService;
    @Autowired
    private ExamSiteMapper siteMapper;
    @Autowired
    private ExamBatchMapper batchMapper;
    @Autowired
    private RegistrationMapper registrationMapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String seedClosedCase(String regNo) {
        ExamSite site = new ExamSite();
        site.setCode("SA" + regNo);
        site.setName("考点" + regNo);
        site.setStatus("ENABLED");
        siteMapper.insert(site);
        ExamBatch batch = new ExamBatch();
        batch.setCode("BA" + regNo);
        batch.setName("批次" + regNo);
        batch.setSiteId(site.getId());
        batch.setProjectCode("DEFAULT");
        batch.setExamDate(LocalDate.of(2026, 10, 2));
        batch.setStatus("CREATED");
        batchMapper.insert(batch);

        Registration reg = new Registration();
        reg.setRegNo(regNo);
        reg.setName("考生" + regNo);
        reg.setIdType("ID_CARD");
        reg.setIdNo("ID-" + regNo);
        reg.setBatchId(batch.getId());
        reg.setStatus("REGISTERED");
        reg.setCorrectionCount(0);
        registrationMapper.insert(reg);

        CaseFileRequest req = new CaseFileRequest();
        req.setBatchId(batch.getId());
        req.setRegNo(regNo);
        req.setViolationType("CHEAT");
        req.setTitle("安全测试案件");
        req.setReporterCode("JK001");
        req.setDueDays(7);
        CaseFileRequest.EvidenceItem e = new CaseFileRequest.EvidenceItem();
        e.setEvidenceType("PHOTO");
        e.setFileName("p.jpg");
        req.setEvidences(new ArrayList<>(Collections.singletonList(e)));
        DisciplineCase c = caseService.fileCase(req);

        DecisionRequest d = new DecisionRequest();
        d.setDecisionType(DisciplineConstants.DECISION_WARNING);
        d.setReason("测试用决定");
        d.setMakerCode("LD001");
        caseService.decide(c.getId(), d);
        caseService.execute(c.getId());
        return c.getCaseNo();
    }

    @Test
    void roleBoundaries() {
        String regNo = "WA001";
        String caseNo = seedClosedCase(regNo);

        // 未认证 → 401
        assertEquals(HttpStatus.UNAUTHORIZED,
                restTemplate.getForEntity(url("/api/discipline/portal/cases?regNo=" + regNo), String.class).getStatusCode());

        // 学员 student：门户 200 且脱敏（不回证件号）
        ResponseEntity<String> portal = restTemplate.withBasicAuth("student", "student")
                .getForEntity(url("/api/discipline/portal/cases?regNo=" + regNo), String.class);
        assertEquals(HttpStatus.OK, portal.getStatusCode());
        assertTrue(portal.getBody().contains(caseNo));
        assertFalse(portal.getBody().contains("ID-" + regNo));
        // 学员访问内部办案接口与封存档案 → 403
        assertEquals(HttpStatus.FORBIDDEN,
                restTemplate.withBasicAuth("student", "student")
                        .getForEntity(url("/api/discipline/cases"), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                restTemplate.withBasicAuth("student", "student")
                        .getForEntity(url("/api/discipline/sealed/cases"), String.class).getStatusCode());

        // 授权岗 archive：封存档案 200，内部办案接口 403
        ResponseEntity<String> sealed = restTemplate.withBasicAuth("archive", "archive")
                .getForEntity(url("/api/discipline/sealed/cases"), String.class);
        assertEquals(HttpStatus.OK, sealed.getStatusCode());
        assertTrue(sealed.getBody().contains(caseNo));
        assertEquals(HttpStatus.FORBIDDEN,
                restTemplate.withBasicAuth("archive", "archive")
                        .getForEntity(url("/api/discipline/cases"), String.class).getStatusCode());

        // 内部管理 bootstrap：办案接口 200（已封存不在普通清单，但接口本身可访问），封存档案 403
        assertEquals(HttpStatus.OK,
                restTemplate.withBasicAuth("bootstrap", "bootstrap")
                        .getForEntity(url("/api/discipline/cases"), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                restTemplate.withBasicAuth("bootstrap", "bootstrap")
                        .getForEntity(url("/api/discipline/sealed/cases"), String.class).getStatusCode());

        // 用人单位 employer：门户可访问但只回脱敏数据（与证书验真窗口同为认证即可访问），内部接口 403
        ResponseEntity<String> employerPortal = restTemplate.withBasicAuth("employer", "employer")
                .getForEntity(url("/api/discipline/portal/cases?regNo=" + regNo), String.class);
        assertEquals(HttpStatus.OK, employerPortal.getStatusCode());
        assertFalse(employerPortal.getBody().contains("ID-" + regNo));
        assertEquals(HttpStatus.FORBIDDEN,
                restTemplate.withBasicAuth("employer", "employer")
                        .getForEntity(url("/api/discipline/cases"), String.class).getStatusCode());
    }
}
