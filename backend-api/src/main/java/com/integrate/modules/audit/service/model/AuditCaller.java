package com.integrate.modules.audit.service.model;

import java.net.InetAddress;
import java.util.UUID;

/** Quién consulta la bitácora y desde dónde; queda en la entrada que registra la consulta. */
public record AuditCaller(UUID actorId, InetAddress ip, String userAgent) {
}