package com.integrate.modules.voucher.service;

import com.integrate.modules.voucher.dto.response.VoucherResponse;
import java.util.UUID;

/** Consulta del voucher por el turista dueño de la reserva (ADR-0010 §1). */
public interface VoucherQueryService {

    /**
     * @throws com.integrate.common.exception.BusinessRuleException 404 si la reserva no existe, es ajena o no tiene
     *         voucher por no estar confirmada; 409 {@code VOUCHER_NOT_READY} (+ {@code Retry-After}) si está
     *         confirmada pero el voucher aún no se emitió
     */
    VoucherResponse get(UUID touristId, UUID bookingId);
}
