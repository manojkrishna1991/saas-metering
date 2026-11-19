package com.example.demo.invoice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Reads entitlements straight from the control-plane's {@code subscriptions} and {@code plans}
 * tables (shared database). The plan has no base fee column yet, so the base fee is zero.
 */
@Component
public class JdbcPlanEntitlementProvider implements PlanEntitlementProvider {

    private static final String SQL = """
            select p.metric_type, p.included_calls, p.overage_price_paise, p.currency
              from subscriptions s
              join plans p on p.id = s.plan_id
             where s.tenant_id = ?
               and s.start_date < ?
               and (s.end_date is null or s.end_date >= ?)
             order by s.start_date desc
             limit 1""";

    private final JdbcTemplate jdbc;

    public JdbcPlanEntitlementProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlanEntitlement> findEntitlements(String tenantId, String billingCycle) {
        UUID tenant;
        try {
            tenant = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        YearMonth month = YearMonth.parse(billingCycle);
        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return jdbc.query(SQL, (rs, i) -> new PlanEntitlement(
                rs.getString("metric_type").toLowerCase(),
                rs.getLong("included_calls"),
                BigDecimal.valueOf(rs.getLong("overage_price_paise")).movePointLeft(2).setScale(6),
                BigDecimal.ZERO,
                rs.getString("currency")), tenant, end, start);
    }
}
