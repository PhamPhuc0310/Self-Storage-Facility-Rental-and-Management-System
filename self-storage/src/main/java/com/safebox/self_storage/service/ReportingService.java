package com.safebox.self_storage.service;

import com.safebox.self_storage.exception.BusinessException;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingService {
    private final Sprint2Access access;
    private final NamedParameterJdbcTemplate jdbc;
    public ReportingService(Sprint2Access access, NamedParameterJdbcTemplate jdbc) {
        this.access = access; this.jdbc = jdbc;
    }

    public record Filters(LocalDate startDate, LocalDate endDate, UUID facilityId) {}
    public record FinancialSummary(LocalDate startDate, LocalDate endDate, BigDecimal rentalCollections,
            BigDecimal depositCollections, BigDecimal otherCollections, BigDecimal unclassifiedCollections,
            BigDecimal refundOutflow, BigDecimal netCashMovement, BigDecimal outstandingReceivables,
            BigDecimal overdueReceivables) {}
    public record TrendPoint(LocalDate periodStart, BigDecimal rentalCollections, BigDecimal depositCollections,
            BigDecimal otherCollections, BigDecimal unclassifiedCollections, BigDecimal refundOutflow) {}
    public record RevenueTrend(String grouping, List<TrendPoint> data) {}
    public record FacilityPerformance(UUID facilityId, int totalUnits, int occupiedUnits, int availableUnits,
            int maintenanceUnits, BigDecimal occupancyRate, long activeContracts,
            BigDecimal confirmedCollections, BigDecimal outstandingReceivables) {}
    public record OperationalSummary(long activeContracts, long expiringWithin30Days, long overdueActiveContracts,
            long pendingReturns, long completedReturnsInPeriod, long maintenanceUnits) {}

    private record Scope(MapSqlParameterSource params, List<UUID> facilities, LocalDate start, LocalDate end) {}
    private Scope scope(Filters f) {
        String role = access.role();
        if (!Set.of("FACILITY_MANAGER", "BUSINESS_OPERATIONS_MANAGER").contains(role))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Reports require a management role.");
        if (f == null || f.startDate() == null || f.endDate() == null || f.endDate().isBefore(f.startDate())
                || ChronoUnit.DAYS.between(f.startDate(), f.endDate()) > 366)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose a valid period of at most 367 calendar days.");
        List<UUID> assigned = access.facilities();
        if (f.facilityId() != null && !assigned.contains(f.facilityId()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Facility is outside your assigned scope.");
        List<UUID> facilities = f.facilityId() == null ? assigned : List.of(f.facilityId());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("facilities", facilities)
                .addValue("start", Timestamp.valueOf(f.startDate().atStartOfDay()))
                .addValue("end", Timestamp.valueOf(f.endDate().plusDays(1).atStartOfDay()))
                .addValue("today", access.today())
                .addValue("expiry", access.today().plusDays(30));
        return new Scope(params, facilities, f.startDate(), f.endDate());
    }

    private static final String PAYMENT_FROM = " FROM (SELECT p0.*, ROW_NUMBER() OVER (PARTITION BY "
            + "COALESCE(p0.reference_code, CAST(p0.transaction_id AS VARCHAR(36))), p0.transaction_type, p0.payment_type "
            + "ORDER BY p0.transaction_date, p0.transaction_id) report_rank "
            + "FROM dbo.PAYMENT_TRANSACTIONS p0 WHERE p0.status='SUCCESS') p "
            + "JOIN dbo.INVOICES i ON i.invoice_id = p.invoice_id "
            + "JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = i.contract_id "
            + "JOIN dbo.STORAGE_UNITS u ON u.unit_id = c.unit_id "
            + "WHERE u.facility_id IN (:facilities) AND p.report_rank=1 "
            + "AND p.transaction_date >= :start AND p.transaction_date < :end ";
    // Legacy combined invoices without payment_type stay unclassified; they are never guessed to be rent or deposit.
    private static final String PAYMENT_SUMS =
            "COALESCE(SUM(CASE WHEN p.transaction_type <> 'REFUND' AND p.payment_type IN ('INITIAL_RENT','REMAINING_RENT','MONTHLY_RENT','RENEWAL') THEN p.amount ELSE 0 END),0) rental, "
            + "COALESCE(SUM(CASE WHEN p.transaction_type <> 'REFUND' AND p.payment_type = 'DEPOSIT' THEN p.amount ELSE 0 END),0) deposit, "
            + "COALESCE(SUM(CASE WHEN p.transaction_type <> 'REFUND' AND p.payment_type IN ('PENALTY','OVERDUE','OTHER') THEN p.amount ELSE 0 END),0) other_collections, "
            + "COALESCE(SUM(CASE WHEN p.transaction_type <> 'REFUND' AND (p.payment_type IS NULL OR p.payment_type NOT IN ('INITIAL_RENT','REMAINING_RENT','MONTHLY_RENT','RENEWAL','DEPOSIT','PENALTY','OVERDUE','OTHER')) THEN p.amount ELSE 0 END),0) unclassified, "
            + "COALESCE(SUM(CASE WHEN p.transaction_type = 'REFUND' THEN p.amount ELSE 0 END),0) refund_outflow";

    private BigDecimal money(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        BigDecimal result = rs.getBigDecimal(column);
        return result == null ? BigDecimal.ZERO : result;
    }
    private record Cash(BigDecimal rental, BigDecimal deposit, BigDecimal other, BigDecimal unclassified, BigDecimal refund) {
        static Cash zero() { return new Cash(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO); }
        Cash add(Cash b) { return new Cash(rental.add(b.rental), deposit.add(b.deposit), other.add(b.other), unclassified.add(b.unclassified), refund.add(b.refund)); }
    }
    private Cash cash(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Cash(money(rs,"rental"), money(rs,"deposit"), money(rs,"other_collections"),
                money(rs,"unclassified"), money(rs,"refund_outflow"));
    }
    private Cash totalCash(Scope s) {
        if (s.facilities().isEmpty()) return Cash.zero();
        return jdbc.queryForObject("SELECT " + PAYMENT_SUMS + PAYMENT_FROM, s.params(), (rs,n) -> cash(rs));
    }
    private static final String LEGACY_REFUND_FROM = " FROM dbo.DEPOSIT_REFUNDS r "
            + "WHERE r.facility_id IN (:facilities) AND r.status='COMPLETED' "
            + "AND r.completed_at >= :start AND r.completed_at < :end "
            + "AND NOT EXISTS (SELECT 1 FROM dbo.PAYMENT_TRANSACTIONS p "
            + "WHERE p.reference_code=r.refund_transaction_reference AND p.transaction_type='REFUND' AND p.status='SUCCESS') ";
    private BigDecimal legacyRefunds(Scope s) {
        if (s.facilities().isEmpty()) return BigDecimal.ZERO;
        return jdbc.queryForObject("SELECT COALESCE(SUM(r.approved_amount),0) amount" + LEGACY_REFUND_FROM,
                s.params(), (rs,n) -> money(rs,"amount"));
    }
    private BigDecimal receivables(Scope s, boolean overdue) {
        if (s.facilities().isEmpty()) return BigDecimal.ZERO;
        // Receivables are a point-in-time open balance on invoices issued by the inclusive report end date.
        String sql = "SELECT COALESCE(SUM(CASE WHEN i.total_amount > i.paid_amount THEN i.total_amount - i.paid_amount ELSE 0 END),0) amount "
                + "FROM dbo.INVOICES i JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id=i.contract_id "
                + "JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id WHERE u.facility_id IN (:facilities) "
                + "AND i.status <> 'CANCELLED' AND i.invoice_type <> 'DEPOSIT' AND i.issued_at < :end "
                + (overdue ? "AND i.due_date < :end " : "");
        return jdbc.queryForObject(sql, s.params(), (rs,n) -> money(rs,"amount"));
    }
    @Transactional(readOnly = true)
    public FinancialSummary financial(Filters f) {
        Scope s = scope(f); Cash c = totalCash(s);
        BigDecimal refunds = c.refund().add(legacyRefunds(s));
        return new FinancialSummary(s.start(), s.end(), c.rental(), c.deposit(), c.other(), c.unclassified(),
                refunds, c.rental().add(c.deposit()).add(c.other()).add(c.unclassified()).subtract(refunds),
                receivables(s,false), receivables(s,true));
    }
    @Transactional(readOnly = true)
    public RevenueTrend trend(Filters f, String grouping) {
        Scope s = scope(f);
        if (!Set.of("DAY","MONTH").contains(grouping))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Grouping must be DAY or MONTH.");
        if (s.facilities().isEmpty()) return new RevenueTrend(grouping, List.of());
        String sql = "SELECT CAST(p.transaction_date AS date) period_day, " + PAYMENT_SUMS + PAYMENT_FROM
                + "GROUP BY CAST(p.transaction_date AS date) ORDER BY period_day";
        Map<LocalDate,Cash> grouped = new TreeMap<>();
        jdbc.query(sql, s.params(), rs -> {
            LocalDate day = rs.getDate("period_day").toLocalDate();
            LocalDate key = "MONTH".equals(grouping) ? day.withDayOfMonth(1) : day;
            grouped.merge(key, cash(rs), Cash::add);
        });
        jdbc.query("SELECT CAST(r.completed_at AS date) period_day, COALESCE(SUM(r.approved_amount),0) amount "
                + LEGACY_REFUND_FROM + "GROUP BY CAST(r.completed_at AS date)", s.params(), rs -> {
            LocalDate day = rs.getDate("period_day").toLocalDate();
            LocalDate key = "MONTH".equals(grouping) ? day.withDayOfMonth(1) : day;
            grouped.merge(key, new Cash(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,money(rs,"amount")), Cash::add);
        });
        return new RevenueTrend(grouping, grouped.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), e.getValue().rental(), e.getValue().deposit(),
                        e.getValue().other(), e.getValue().unclassified(), e.getValue().refund())).toList());
    }
    @Transactional(readOnly = true)
    public List<FacilityPerformance> performance(Filters f) {
        Scope s = scope(f);
        if (s.facilities().isEmpty()) return List.of();
        Map<UUID,long[]> counts = new HashMap<>();
        jdbc.query("SELECT facility_id, COUNT(*) total, SUM(CASE WHEN status='OCCUPIED' THEN 1 ELSE 0 END) occupied, "
                + "SUM(CASE WHEN status='AVAILABLE' THEN 1 ELSE 0 END) available, "
                + "SUM(CASE WHEN status='MAINTENANCE' THEN 1 ELSE 0 END) maintenance "
                + "FROM dbo.STORAGE_UNITS WHERE facility_id IN (:facilities) GROUP BY facility_id", s.params(), rs ->
                { counts.put(UUID.fromString(rs.getString("facility_id")), new long[]{rs.getLong("total"),rs.getLong("occupied"),rs.getLong("available"),rs.getLong("maintenance")}); });
        Map<UUID,Long> active = new HashMap<>();
        jdbc.query("SELECT u.facility_id, COUNT(*) amount FROM dbo.RENTAL_CONTRACTS c JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id "
                + "WHERE u.facility_id IN (:facilities) AND c.status='ACTIVE' GROUP BY u.facility_id", s.params(), rs ->
                { active.put(UUID.fromString(rs.getString("facility_id")), rs.getLong("amount")); });
        Map<UUID,BigDecimal> collections = new HashMap<>();
        jdbc.query("SELECT u.facility_id, COALESCE(SUM(p.amount),0) amount " + PAYMENT_FROM
                + "AND p.transaction_type <> 'REFUND' GROUP BY u.facility_id", s.params(), rs ->
                { collections.put(UUID.fromString(rs.getString("facility_id")), money(rs,"amount")); });
        Map<UUID,BigDecimal> outstanding = new HashMap<>();
        jdbc.query("SELECT u.facility_id, COALESCE(SUM(CASE WHEN i.total_amount > i.paid_amount THEN i.total_amount-i.paid_amount ELSE 0 END),0) amount "
                + "FROM dbo.INVOICES i JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id=i.contract_id "
                + "JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id WHERE u.facility_id IN (:facilities) "
                + "AND i.status <> 'CANCELLED' AND i.invoice_type <> 'DEPOSIT' AND i.issued_at < :end GROUP BY u.facility_id", s.params(), rs ->
                { outstanding.put(UUID.fromString(rs.getString("facility_id")), money(rs,"amount")); });
        return s.facilities().stream().map(id -> {
            long[] n = counts.getOrDefault(id,new long[4]);
            long denominator = n[0] - n[3]; // rentable units exclude maintenance
            BigDecimal rate = denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(n[1])
                    .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
            return new FacilityPerformance(id,(int)n[0],(int)n[1],(int)n[2],(int)n[3],rate,
                    active.getOrDefault(id,0L),collections.getOrDefault(id,BigDecimal.ZERO),
                    outstanding.getOrDefault(id,BigDecimal.ZERO));
        }).toList();
    }
    @Transactional(readOnly = true)
    public OperationalSummary operational(Filters f) {
        Scope s = scope(f);
        if (s.facilities().isEmpty()) return new OperationalSummary(0,0,0,0,0,0);
        String contractSql = "SELECT COUNT(*) active, "
                + "SUM(CASE WHEN c.end_date >= :today AND c.end_date <= :expiry THEN 1 ELSE 0 END) expiring, "
                + "SUM(CASE WHEN c.end_date < :today THEN 1 ELSE 0 END) overdue "
                + "FROM dbo.RENTAL_CONTRACTS c JOIN dbo.STORAGE_UNITS u ON u.unit_id=c.unit_id "
                + "WHERE u.facility_id IN (:facilities) AND c.status='ACTIVE'";
        long[] contract = jdbc.queryForObject(contractSql,s.params(),(rs,n) -> new long[]{rs.getLong("active"),rs.getLong("expiring"),rs.getLong("overdue")});
        String returnSql = "SELECT SUM(CASE WHEN h.status='SCHEDULED' THEN 1 ELSE 0 END) pending, "
                + "SUM(CASE WHEN h.status='COMPLETED' AND h.actual_at >= :start AND h.actual_at < :end THEN 1 ELSE 0 END) completed "
                + "FROM dbo.HANDOVER_RECORDS h JOIN dbo.STORAGE_UNITS u ON u.unit_id=h.unit_id "
                + "WHERE u.facility_id IN (:facilities) AND h.handover_type='RETURN'";
        long[] returns = jdbc.queryForObject(returnSql,s.params(),(rs,n) -> new long[]{rs.getLong("pending"),rs.getLong("completed")});
        Long maintenance = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.STORAGE_UNITS WHERE facility_id IN (:facilities) AND status='MAINTENANCE'",
                s.params(), Long.class);
        return new OperationalSummary(contract[0],contract[1],contract[2],returns[0],returns[1],maintenance);
    }
}
