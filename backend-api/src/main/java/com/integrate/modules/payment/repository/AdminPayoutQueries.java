package com.integrate.modules.payment.repository;

import com.integrate.common.payload.PageResponse;
import com.integrate.modules.payment.dto.response.PayoutBatchResponse;
import com.integrate.modules.payment.dto.response.PayoutBatchResponse.Status;
import com.integrate.modules.payment.repository.PayoutQueries.SubmittedBatch;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminPayoutQueries {

    private static final String SELECT = """
            SELECT b.*, a.legal_name, t.gross_amount_cop, t.gateway_fee_cop, t.net_community_cop,
                   t.disbursement_count, r.id AS receipt_id
              FROM payments.payout_batches b
              JOIN catalog.community_associations a ON a.id = b.association_id
              JOIN payments.v_payout_batch_totals t ON t.payout_batch_id = b.id
              LEFT JOIN payments.settlement_receipts r ON r.payout_batch_id = b.id
            """;
    private static final String FILTER = """
             WHERE (:status IS NULL OR b.status = :status)
               AND (CAST(:association AS uuid) IS NULL OR b.association_id = :association)
            """;
    private final JdbcClient jdbc;

    public record Disbursement(UUID id, BigDecimal amountCop, String status) {
    }

    public record Lookup(SubmittedBatch batch, String idempotencyKey, String gatewayPayoutId) {
    }

    public PageResponse<PayoutBatchResponse> list(Status status, UUID association, Pageable page) {
        var rows = filter(SELECT + FILTER + " ORDER BY b.created_at DESC, b.id LIMIT :limit OFFSET :offset",
                status, association).param("limit", page.getPageSize()).param("offset", page.getOffset())
                .query(AdminPayoutQueries::map).list();
        long total = filter("SELECT count(*) FROM payments.payout_batches b" + FILTER, status, association)
                .query(Long.class).single();
        return PageResponse.of(new PageImpl<>(rows, page, total), row -> row);
    }

    public Optional<PayoutBatchResponse> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE b.id = :id").param("id", id).query(AdminPayoutQueries::map).optional();
    }

    public List<Disbursement> disbursements(UUID id) {
        return jdbc.sql("""
                SELECT id, amount_cop, status FROM payments.disbursements
                 WHERE payout_batch_id = :id ORDER BY id
                """).param("id", id).query((rs, row) -> new Disbursement(rs.getObject("id", UUID.class),
                rs.getBigDecimal("amount_cop"), rs.getString("status"))).list();
    }

    public Optional<String> receiptKey(UUID id) {
        return jdbc.sql("SELECT pdf_s3_key FROM payments.settlement_receipts WHERE payout_batch_id = :id")
                .param("id", id).query(String.class).optional();
    }

    public Optional<Lookup> lookup(UUID id) {
        return jdbc.sql("""
                SELECT b.association_id, b.payout_account_id, b.idempotency_key, b.gateway_payout_id,
                       t.net_community_cop
                  FROM payments.payout_batches b JOIN payments.v_payout_batch_totals t ON t.payout_batch_id = b.id
                 WHERE b.id = :id AND b.status = 'SUBMITTED'
                """).param("id", id).query((rs, row) -> new Lookup(
                new SubmittedBatch(id, rs.getObject("association_id", UUID.class),
                        rs.getObject("payout_account_id", UUID.class), rs.getBigDecimal("net_community_cop")),
                rs.getString("idempotency_key"), rs.getString("gateway_payout_id"))).optional();
    }

    private JdbcClient.StatementSpec filter(String sql, Status status, UUID association) {
        return jdbc.sql(sql).param("status", status == null ? null : status.name(), Types.VARCHAR)
                .param("association", association);
    }

    private static PayoutBatchResponse map(ResultSet rs, int row) throws SQLException {
        var gross = rs.getBigDecimal("gross_amount_cop");
        var gatewayFee = rs.getBigDecimal("gateway_fee_cop");
        var net = rs.getBigDecimal("net_community_cop");
        return new PayoutBatchResponse(rs.getObject("id", UUID.class), rs.getObject("association_id", UUID.class),
                rs.getString("legal_name"), Status.valueOf(rs.getString("status")), gross, gatewayFee,
                gross.subtract(net), net, rs.getInt("disbursement_count"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getObject("paid_at", OffsetDateTime.class), rs.getString("failure_reason"),
                rs.getObject("receipt_id", UUID.class));
    }
}
