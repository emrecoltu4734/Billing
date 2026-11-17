package com.ispbilling.billing;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * @param latestEndedPeriod en son biten dönem (faturalanmaya hazır)
 * @param latestEndedBilled o dönem tamamen faturalandı mı
 */
public record BillCycleView(Long id, String code, String name, int cycleDay, boolean isDefault, boolean autoRun,
                            boolean active, long customerCount, LocalDate nextRunDate,
                            YearMonth latestEndedPeriod, LocalDate latestEndedStart, LocalDate latestEndedEnd,
                            boolean latestEndedBilled) {
}
