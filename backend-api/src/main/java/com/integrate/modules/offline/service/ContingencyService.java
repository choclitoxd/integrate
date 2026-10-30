package com.integrate.modules.offline.service;

import com.integrate.modules.offline.service.model.RedeemOutcome;
import java.util.UUID;

/** Canje manual por código alfanumérico cuando la cámara falla (RF-17, ADR-0032 §4). */
public interface ContingencyService {

    /** @param deviceIdentifier opcional; si llega, el dispositivo se registra y se valida igual que en el padrón */
    RedeemOutcome redeem(UUID userId, String shortCode, String deviceIdentifier, String userAgent);
}
