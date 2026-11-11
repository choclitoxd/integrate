package com.integrate.modules.booking.service;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Métricas individuales del anfitrión (ADR-0061 Entrega 2) y sus series para las gráficas (ADR-0063).
 * Todo se agrega por la fecha de inicio de la franja en {@code America/Bogota}, cuenta solo reservas
 * {@code CONFIRMED} y se limita a las experiencias del titular autenticado.
 */
@Service
@RequiredArgsConstructor
public class HostMetricsService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int MONTHS = 12;
    private static final String QUERY = """
            WITH per_experience AS (
                SELECT e.id, e.title, count(*) AS reservations, sum(b.slots_count) AS people,
                       count(DISTINCT s.id) FILTER (WHERE s.start_time > ? AND s.status <> 'CANCELLED') AS upcoming,
                       coalesce(sum(b.slots_count) FILTER (
                           WHERE s.start_time > ? AND s.status <> 'CANCELLED'), 0) AS upcoming_people
                FROM catalog.host_profiles h
                JOIN catalog.experiences e ON e.host_id = h.id AND e.deleted_at IS NULL
                JOIN catalog.schedules s ON s.experience_id = e.id
                JOIN booking.bookings b ON b.schedule_id = s.id AND b.status = 'CONFIRMED'
                WHERE h.user_id = ? AND h.deleted_at IS NULL
                  AND s.start_time >= ? AND s.start_time < ?
                GROUP BY e.id, e.title
            )
            SELECT id, title, reservations, people, sum(reservations) OVER (), sum(people) OVER (),
                   sum(upcoming) OVER (), sum(upcoming_people) OVER ()
            FROM per_experience
            ORDER BY reservations DESC, id
            LIMIT 5
            """;
    /** Reservas y personas por día del rango, con el día contado en hora de Bogotá. */
    private static final String DAILY_QUERY = """
            SELECT (s.start_time AT TIME ZONE 'America/Bogota')::date AS day,
                   count(*) AS reservations, coalesce(sum(b.slots_count), 0) AS people
            FROM catalog.host_profiles h
            JOIN catalog.experiences e ON e.host_id = h.id AND e.deleted_at IS NULL
            JOIN catalog.schedules s ON s.experience_id = e.id
            JOIN booking.bookings b ON b.schedule_id = s.id AND b.status = 'CONFIRMED'
            WHERE h.user_id = ? AND h.deleted_at IS NULL
              AND s.start_time >= ? AND s.start_time < ?
            GROUP BY day
            ORDER BY day
            """;
    /** Reservas y personas por mes del rango, con el mes contado en hora de Bogotá. */
    private static final String MONTHLY_QUERY = """
            SELECT to_char(s.start_time AT TIME ZONE 'America/Bogota', 'YYYY-MM') AS month,
                   count(*) AS reservations, coalesce(sum(b.slots_count), 0) AS people
            FROM catalog.host_profiles h
            JOIN catalog.experiences e ON e.host_id = h.id AND e.deleted_at IS NULL
            JOIN catalog.schedules s ON s.experience_id = e.id
            JOIN booking.bookings b ON b.schedule_id = s.id AND b.status = 'CONFIRMED'
            WHERE h.user_id = ? AND h.deleted_at IS NULL
              AND s.start_time >= ? AND s.start_time < ?
            GROUP BY month
            """;
    /** Cupos ofrecidos y cupos con reserva confirmada en las franjas futuras no canceladas. */
    private static final String OCCUPANCY_QUERY = """
            SELECT coalesce(sum(s.total_slots), 0),
                   coalesce(sum((SELECT coalesce(sum(b.slots_count), 0) FROM booking.bookings b
                                 WHERE b.schedule_id = s.id AND b.status = 'CONFIRMED')), 0)
            FROM catalog.host_profiles h
            JOIN catalog.experiences e ON e.host_id = h.id AND e.deleted_at IS NULL
            JOIN catalog.schedules s ON s.experience_id = e.id
            WHERE h.user_id = ? AND h.deleted_at IS NULL
              AND s.start_time > ? AND s.status <> 'CANCELLED'
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public record Ranking(UUID experienceId, String title, long reservations, long people) { }
    public record DayPoint(LocalDate date, long reservations, long people) { }
    public record MonthPoint(String month, long reservations, long people) { }
    /** {@code slots} = cupos ofrecidos; {@code booked} = cupos con reserva confirmada. */
    public record Occupancy(long slots, long booked) { }
    public record Metrics(String month, String currentMonth, String earliestMonth, Instant updatedAt,
                          long reservations, long people, long upcoming, long upcomingPeople,
                          List<Ranking> ranking, List<DayPoint> daily, List<MonthPoint> monthly,
                          Occupancy occupancy) { }
    private record Row(Ranking ranking, long reservations, long people, long upcoming, long upcomingPeople) { }

    public Metrics get(UUID userId, YearMonth requested) {
        Instant now = clock.instant();
        YearMonth current = YearMonth.from(now.atZone(BOGOTA));
        YearMonth earliest = current.minusMonths(MONTHS - 1L);
        YearMonth month = requested == null ? current : requested;
        if (month.isBefore(earliest) || month.isAfter(current)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Month is outside the available range.");
        }
        Timestamp reference = Timestamp.from(now);
        List<Row> rows = jdbc.query(QUERY, (rs, index) -> new Row(
                new Ranking(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getLong(4)),
                rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8)),
                reference, reference, userId, startOf(month), startOf(month.plusMonths(1)));
        Row totals = rows.isEmpty() ? new Row(null, 0, 0, 0, 0) : rows.getFirst();
        return new Metrics(month.toString(), current.toString(), earliest.toString(), now,
                totals.reservations(), totals.people(), totals.upcoming(), totals.upcomingPeople(),
                rows.stream().map(Row::ranking).toList(),
                daily(userId, month), monthly(userId, earliest, current), occupancy(userId, reference));
    }

    private List<DayPoint> daily(UUID userId, YearMonth month) {
        return jdbc.query(DAILY_QUERY, (rs, index) -> new DayPoint(
                rs.getObject(1, LocalDate.class), rs.getLong(2), rs.getLong(3)),
                userId, startOf(month), startOf(month.plusMonths(1)));
    }

    /** Los 12 meses del selector, en orden y con ceros, para que la serie sea continua. */
    private List<MonthPoint> monthly(UUID userId, YearMonth earliest, YearMonth current) {
        Map<String, MonthPoint> found = new HashMap<>();
        jdbc.query(MONTHLY_QUERY, (rs, index) -> new MonthPoint(rs.getString(1), rs.getLong(2), rs.getLong(3)),
                userId, startOf(earliest), startOf(current.plusMonths(1)))
                .forEach(point -> found.put(point.month(), point));
        return java.util.stream.IntStream.range(0, MONTHS)
                .mapToObj(offset -> earliest.plusMonths(offset).toString())
                .map(key -> found.getOrDefault(key, new MonthPoint(key, 0, 0)))
                .toList();
    }

    private Occupancy occupancy(UUID userId, Timestamp reference) {
        return jdbc.queryForObject(OCCUPANCY_QUERY,
                (rs, index) -> new Occupancy(rs.getLong(1), rs.getLong(2)), userId, reference);
    }

    private static Timestamp startOf(YearMonth month) {
        return Timestamp.from(month.atDay(1).atStartOfDay(BOGOTA).toInstant());
    }
}
