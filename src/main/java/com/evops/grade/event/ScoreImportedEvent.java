package com.evops.grade.event;

import com.evops.grade.entity.ExamScore;

import java.util.List;

/**
 * 成绩批量录入完成事件：在录入事务内同步发布，供其他模块补回写挂起的处理结果
 * （例如违纪成绩作废决定早于成绩档案生成时，档案一生成即补回写无效）。
 */
public class ScoreImportedEvent {
    private final List<ExamScore> importedScores;

    public ScoreImportedEvent(List<ExamScore> importedScores) {
        this.importedScores = importedScores;
    }

    public List<ExamScore> getImportedScores() {
        return importedScores;
    }
}
