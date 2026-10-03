package com.evops.discipline.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.discipline.common.DisciplineConstants;
import com.evops.discipline.entity.DisciplineCase;
import com.evops.discipline.entity.DisciplineExecution;
import com.evops.discipline.mapper.DisciplineCaseMapper;
import com.evops.discipline.mapper.DisciplineExecutionMapper;
import com.evops.grade.entity.ExamScore;
import com.evops.grade.event.ScoreImportedEvent;
import com.evops.grade.mapper.ExamScoreMapper;
import com.evops.grade.service.ScoreValidityOverride;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 违纪模块与成绩档案的桥接（成绩模块不反向依赖违纪模块）：
 * - 成绩作废决定早于成绩档案生成：执行记录挂 PENDING，成绩一录入即补回写 valid=false；
 * - 成绩录入预检：通过 ScoreValidityOverride 给出"成绩将记为无效"口径与说明。
 */
@Component
public class DisciplineScoreBridge implements ScoreValidityOverride {

    private final DisciplineExecutionMapper executionMapper;
    private final DisciplineCaseMapper caseMapper;
    private final ExamScoreMapper scoreMapper;

    public DisciplineScoreBridge(DisciplineExecutionMapper executionMapper,
                                 DisciplineCaseMapper caseMapper,
                                 ExamScoreMapper scoreMapper) {
        this.executionMapper = executionMapper;
        this.caseMapper = caseMapper;
        this.scoreMapper = scoreMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean invalidates(Long registrationId) {
        Long count = executionMapper.selectCount(new QueryWrapper<DisciplineExecution>()
                .eq("registration_id", registrationId)
                .eq("action_type", DisciplineConstants.EXEC_SCORE_INVALIDATE)
                .eq("apply_status", DisciplineConstants.APPLY_PENDING));
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public String describe(Long registrationId) {
        List<DisciplineExecution> pendings = executionMapper.selectList(new QueryWrapper<DisciplineExecution>()
                .eq("registration_id", registrationId)
                .eq("action_type", DisciplineConstants.EXEC_SCORE_INVALIDATE)
                .eq("apply_status", DisciplineConstants.APPLY_PENDING)
                .orderByAsc("id"));
        if (pendings.isEmpty()) {
            return null;
        }
        DisciplineCase c = caseMapper.selectById(pendings.get(0).getCaseId());
        return "该学员有已生效的违纪成绩作废决定"
                + (c == null ? "" : "（案件 " + c.getCaseNo() + "）") + "，成绩将记为无效";
    }

    /** 成绩批量录入完成（同事务）：补回写挂起的成绩作废。 */
    @EventListener
    @Transactional
    public void onScoreImported(ScoreImportedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        for (ExamScore score : event.getImportedScores()) {
            List<DisciplineExecution> pendings = executionMapper.selectList(new QueryWrapper<DisciplineExecution>()
                    .eq("registration_id", score.getRegistrationId())
                    .eq("action_type", DisciplineConstants.EXEC_SCORE_INVALIDATE)
                    .eq("apply_status", DisciplineConstants.APPLY_PENDING)
                    .orderByAsc("id"));
            for (DisciplineExecution pending : pendings) {
                Boolean before = score.getValid();
                if (!Boolean.FALSE.equals(before)) {
                    score.setValid(false);
                    scoreMapper.updateById(score);
                }
                pending.setApplyStatus(DisciplineConstants.APPLY_DONE);
                pending.setScoreId(score.getId());
                pending.setValidBefore(before);
                pending.setValidAfter(false);
                pending.setResultNote("成绩档案生成，违纪成绩作废补回写 valid=false");
                pending.setExecutedAt(now);
                executionMapper.updateById(pending);

                DisciplineCase c = caseMapper.selectById(pending.getCaseId());
                if (c != null && !Boolean.TRUE.equals(c.getScoreWriteback())) {
                    c.setScoreWriteback(true);
                    caseMapper.updateById(c);
                }
            }
        }
    }
}
