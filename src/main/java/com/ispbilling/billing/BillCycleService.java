package com.ispbilling.billing;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional
public class BillCycleService {

    private final BillCycleRepository cycles;
    private final BillRunRepository billRuns;
    private final JdbcClient jdbc;
    private final Clock clock;

    public BillCycleService(BillCycleRepository cycles, BillRunRepository billRuns, JdbcClient jdbc, Clock clock) {
        this.cycles = cycles;
        this.billRuns = billRuns;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public BillCycle create(CreateBillCycleRequest r) {
        String code = r.code().trim().toUpperCase();
        if (cycles.existsByCode(code)) {
            throw new BusinessException("Bu kodla bir fatura dönemi zaten var: " + code);
        }
        return cycles.save(new BillCycle(code, r.name().trim(), r.cycleDay(), Boolean.TRUE.equals(r.autoRun())));
    }

    /** Cycle belirtilmeden açılan yeni aboneler bu cycle'a atanır. */
    public void makeDefault(String code) {
        BillCycle target = getByCode(code);
        cycles.findByIsDefaultTrue().ifPresent(c -> {
            c.setDefault(false);
            cycles.flush();   // kısmi UNIQUE indeks: önce eskisini kaldır
        });
        target.setDefault(true);
    }

    public void setAutoRun(String code, boolean autoRun) {
        getByCode(code).setAutoRun(autoRun);
    }

    @Transactional(readOnly = true)
    public BillCycle getByCode(String code) {
        return cycles.findByCode(code.trim().toUpperCase()).orElseThrow(() -> new NotFoundException("Fatura dönemi", code));
    }

    @Transactional(readOnly = true)
    public BillCycle getDefault() {
        return cycles.findByIsDefaultTrue()
                .orElseThrow(() -> new BusinessException("Varsayılan fatura dönemi tanımlı değil"));
    }

    /** code boşsa varsayılan cycle. */
    @Transactional(readOnly = true)
    public BillCycle resolve(String code) {
        return code == null || code.isBlank() ? getDefault() : getByCode(code);
    }

    @Transactional(readOnly = true)
    public List<BillCycleView> all() {
        LocalDate today = LocalDate.now(clock);
        return cycles.findAllByOrderByCycleDayAscCodeAsc().stream().map(c -> {
            long customers = jdbc.sql("select count(*) from customer where bill_cycle_id = :id")
                    .param("id", c.getId()).query(Long.class).single();
            YearMonth latest = c.latestEndedPeriod(today);
            boolean billed = billRuns.findByBillCycleIdAndPeriodStart(c.getId(), c.periodStart(latest))
                    .map(r -> r.getStatus() == BillRun.Status.COMPLETED).orElse(false);
            return new BillCycleView(c.getId(), c.getCode(), c.getName(), c.getCycleDay(), c.isDefault(),
                    c.isAutoRun(), c.isActive(), customers, c.nextRunDate(today), latest, c.periodStart(latest),
                    c.periodEnd(latest), billed);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<BillCycle> autoRunCycles() {
        return cycles.findByActiveTrueAndAutoRunTrue();
    }
}
