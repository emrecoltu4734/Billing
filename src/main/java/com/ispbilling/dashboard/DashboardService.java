package com.ispbilling.dashboard;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Ana sayfa özet rakamları. Raporlama sorguları bilerek düz SQL ile yazıldı. */
@Service
public class DashboardService {

    private final JdbcClient jdbc;
    private final Clock clock;

    public DashboardService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Summary(long customerCount, long activeSubscriptions, long suspendedSubscriptions,
                          BigDecimal monthlyRecurringRevenue, BigDecimal collectedThisMonth,
                          BigDecimal openReceivables, BigDecimal overdueAmount, long overdueInvoiceCount) {
    }

    public Summary summary() {
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();

        long customers = jdbc.sql("select count(*) from customer").query(Long.class).single();

        // Bugün itibarıyla yürürlükte olan abonelikler ve vergiler hariç aylık tekrarlayan gelir (MRR)
        var subs = jdbc.sql("""
                        select count(*) filter (where s.status = 'ACTIVE')    as active,
                               count(*) filter (where s.status = 'SUSPENDED') as suspended,
                               coalesce(sum(p.monthly_fee), 0)                as mrr
                          from subscription s
                          join plan p on p.id = s.plan_id
                         where s.start_date <= :today
                           and (s.end_date is null or s.end_date >= :today)
                        """)
                .param("today", today)
                .query((rs, n) -> new Object[]{rs.getLong("active"), rs.getLong("suspended"), rs.getBigDecimal("mrr")})
                .single();

        BigDecimal collected = jdbc.sql("select coalesce(sum(amount), 0) from payment where paid_at >= :from")
                .param("from", today.withDayOfMonth(1).atStartOfDay(zone).toOffsetDateTime())
                .query(BigDecimal.class).single();

        var receivables = jdbc.sql("""
                        select coalesce(sum(total_amount - paid_amount), 0)                                 as open_amount,
                               coalesce(sum(total_amount - paid_amount) filter (where due_date < :today), 0) as overdue_amount,
                               count(*) filter (where due_date < :today)                                     as overdue_count
                          from invoice
                         where status in ('ISSUED', 'PARTIALLY_PAID')
                        """)
                .param("today", today)
                .query((rs, n) -> new Object[]{rs.getBigDecimal("open_amount"), rs.getBigDecimal("overdue_amount"),
                        rs.getLong("overdue_count")})
                .single();

        return new Summary(customers, (long) subs[0], (long) subs[1], (BigDecimal) subs[2], collected,
                (BigDecimal) receivables[0], (BigDecimal) receivables[1], (long) receivables[2]);
    }
}
