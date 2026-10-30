package com.integrate.modules.payment.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos del pago por lotes y del comprobante (ADR-0034 §5 y §6). Escribe solo en {@code payments.*}; lee
 * el contacto de la asociación de {@code catalog} (lectura documentada, solo el correo y el nombre).
 */
@Repository
@RequiredArgsConstructor
public class PayoutQueries {

    private final JdbcClient jdbc;

    public record SubmittedBatch(UUID id, UUID associationId, UUID payoutAccountId, BigDecimal amountCop) {
    }

    public record Totals(BigDecimal grossCop, BigDecimal gatewayFeeCop, BigDecimal netCommunityCop, int count) {
    }

    public record AssociationContact(String legalName, String contactEmail) {
    }

    /** Asociaciones con porciones liberadas que aún no están en ningún lote. */
    public List<UUID> associationsWithUnbatchedShares() {
        return jdbc.sql("""
                SELECT DISTINCT association_id FROM payments.disbursements
                 WHERE status = 'RELEASED' AND payout_batch_id IS NULL""")
                .query((rs, n) -> rs.getObject("association_id", UUID.class)).list();
    }

    /** La cuenta por defecto, vigente y verificada. */
    public Optional<UUID> defaultAccount(UUID associationId) {
        return jdbc.sql("""
                SELECT id FROM payments.payout_accounts
                 WHERE association_id = :association AND is_default AND deleted_at IS NULL AND verified_at IS NOT NULL""")
                .param("association", associationId).query((rs, n) -> rs.getObject("id", UUID.class)).optional();
    }

    public void createSubmittedBatch(UUID batchId, UUID associationId, UUID accountId, Instant now) {
        jdbc.sql("""
                INSERT INTO payments.payout_batches (id, association_id, payout_account_id, status, idempotency_key,
                                                     submitted_at)
                VALUES (:id, :association, :account, 'SUBMITTED', :key, :now)""")
                .param("id", batchId).param("association", associationId).param("account", accountId)
                .param("key", "payout:" + batchId).param("now", utc(now)).update();
    }

    /** Asocia al lote las porciones liberadas; {@code SKIP LOCKED} deja las que otra instancia esté tomando. */
    public int attachShares(UUID batchId, UUID associationId) {
        return jdbc.sql("""
                UPDATE payments.disbursements SET payout_batch_id = :batch
                 WHERE id IN (SELECT id FROM payments.disbursements
                               WHERE association_id = :association AND status = 'RELEASED' AND payout_batch_id IS NULL
                                 FOR UPDATE SKIP LOCKED)""")
                .param("batch", batchId).param("association", associationId).update();
    }

    public List<SubmittedBatch> submittedBatches(int limit) {
        return jdbc.sql("""
                SELECT b.id, b.association_id, b.payout_account_id, COALESCE(sum(d.amount_cop), 0) AS amount
                  FROM payments.payout_batches b LEFT JOIN payments.disbursements d ON d.payout_batch_id = b.id
                 WHERE b.status = 'SUBMITTED' GROUP BY b.id, b.association_id, b.payout_account_id
                 ORDER BY b.submitted_at LIMIT :limit""")
                .param("limit", limit)
                .query((rs, n) -> new SubmittedBatch(rs.getObject("id", UUID.class),
                        rs.getObject("association_id", UUID.class), rs.getObject("payout_account_id", UUID.class),
                        rs.getBigDecimal("amount"))).list();
    }

    public void markPaid(UUID batchId, String gatewayPayoutId, Instant now) {
        jdbc.sql("""
                UPDATE payments.payout_batches SET status = 'PAID', gateway_payout_id = :gateway, paid_at = :now
                 WHERE id = :id AND status = 'SUBMITTED'""")
                .param("id", batchId).param("gateway", gatewayPayoutId).param("now", utc(now)).update();
    }

    public int payShares(UUID batchId) {
        return jdbc.sql("UPDATE payments.disbursements SET status = 'PAID' WHERE payout_batch_id = :id AND status = 'RELEASED'")
                .param("id", batchId).update();
    }

    public void markFailed(UUID batchId, String reason) {
        jdbc.sql("""
                UPDATE payments.payout_batches SET status = 'FAILED', failure_reason = :reason
                 WHERE id = :id AND status = 'SUBMITTED'""").param("id", batchId).param("reason", reason).update();
    }

    /** Devuelve las porciones al conjunto de pendientes para que entren en el siguiente lote. */
    public int detachShares(UUID batchId) {
        return jdbc.sql("UPDATE payments.disbursements SET payout_batch_id = NULL WHERE payout_batch_id = :id AND status = 'RELEASED'")
                .param("id", batchId).update();
    }

    public Totals totals(UUID batchId) {
        return jdbc.sql("""
                SELECT t.gross_amount_cop, t.gateway_fee_cop, t.net_community_cop, t.disbursement_count
                  FROM payments.v_payout_batch_totals t WHERE t.payout_batch_id = :id""")
                .param("id", batchId)
                .query((rs, n) -> new Totals(rs.getBigDecimal("gross_amount_cop"), rs.getBigDecimal("gateway_fee_cop"),
                        rs.getBigDecimal("net_community_cop"), rs.getInt("disbursement_count"))).single();
    }

    public UUID insertReceipt(UUID batchId, BigDecimal gross, BigDecimal gatewayFee, BigDecimal platformFee,
                              BigDecimal net, String pdfKey, Instant issuedAt) {
        return jdbc.sql("""
                INSERT INTO payments.settlement_receipts (payout_batch_id, gross_amount_cop, gateway_fee_cop,
                                                          platform_fee_cop, net_community_cop, pdf_s3_key, issued_at)
                VALUES (:batch, :gross, :fee, :platform, :net, :key, :issued) RETURNING id""")
                .param("batch", batchId).param("gross", gross).param("fee", gatewayFee).param("platform", platformFee)
                .param("net", net).param("key", pdfKey).param("issued", utc(issuedAt))
                .query((rs, n) -> rs.getObject("id", UUID.class)).single();
    }

    public Optional<AssociationContact> contactOf(UUID associationId) {
        return jdbc.sql("SELECT legal_name, contact_email FROM catalog.community_associations WHERE id = :id")
                .param("id", associationId)
                .query((rs, n) -> new AssociationContact(rs.getString("legal_name"), rs.getString("contact_email")))
                .optional();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}