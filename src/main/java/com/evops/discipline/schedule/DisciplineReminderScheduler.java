package com.evops.discipline.schedule;

import com.evops.discipline.service.DisciplineCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 处理决定期限兜底：每分钟扫描一次，临期（到期前 24 小时）提醒、超时每 24 小时一轮催办，站内+邮件双通道落库。
 * 人工也可调 POST /api/discipline/reminders/scan 立即执行同一逻辑（幂等）。
 */
@Component
public class DisciplineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DisciplineReminderScheduler.class);

    private final DisciplineCaseService caseService;

    public DisciplineReminderScheduler(DisciplineCaseService caseService) {
        this.caseService = caseService;
    }

    @Scheduled(cron = "${evops.discipline.remind-cron:0 * * * * *}")
    public void scanOverdueCases() {
        Map<String, Object> result = caseService.scanReminders();
        int rounds = (Integer) result.get("roundCount");
        if (rounds > 0) {
            log.info("违纪处理决定催办扫描：发出催办 {} 轮，案件 {}", rounds, ((List<?>) result.get("caseIds")));
        }
    }
}
