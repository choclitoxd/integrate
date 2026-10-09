package com.integrate.modules.catalog.service.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de {@link com.integrate.modules.catalog.service.SlotInventory#reserve}: datos de la franja y de su
 * experiencia leídos bajo el mismo bloqueo que descontó los cupos.
 *
 * @param unitPriceCop          tarifa por persona vigente; la reserva la copia como hecho histórico (D4)
 * @param availableSlotsAfter   cupos que quedan libres tras el descuento
 */
public record ReservedSlots(UUID scheduleId, UUID experienceId, BigDecimal unitPriceCop, Instant startTime,
                            int availableSlotsAfter) {
}
