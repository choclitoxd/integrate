package com.integrate.modules.audit.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.audit.dto.response.AuditRecordResponse;
import com.integrate.modules.audit.dto.response.AuditVerifyResponse;
import com.integrate.modules.audit.repository.AuditQueries;
import com.integrate.modules.audit.repository.AuditQueries.BrokenLink;
import com.integrate.modules.audit.service.AuditQueryService;
import com.integrate.modules.audit.service.model.AuditCaller;
import com.integrate.modules.audit.service.model.AuditFilter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
class AuditQueryServiceImpl implements AuditQueryService {

    static final String HEADER = "id,createdAt,module,entityName,entityId,action,severity,actorId,actorIp,userAgent,"
            + "details\r\n";
    /** Filas que verifica `verify` si no se indica rango, y máximo de un rango (la función recorre en serie). */
    static final long DEFAULT_WINDOW = 10_000;
    static final long MAX_WINDOW = 100_000;

    private final AuditQueries queries;
    private final AuditTrail audit;
    private final JsonMapper json;

    @Value("${integrate.audit.export.max-rows:50000}")
    private long exportMaxRows;

    @Override
    @Transactional
    public PageResponse<AuditRecordResponse> search(AuditCaller caller, AuditFilter filter, int page, int size) {
        long total = queries.count(filter);
        List<AuditRecordResponse> items = queries.search(filter, size, (long) page * size);
        record(caller, "AUDIT_QUERIED", filter, Map.of("resultCount", items.size(), "totalElements", total));
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Override
    @Transactional
    public byte[] export(AuditCaller caller, AuditFilter filter) {
        long total = queries.count(filter);
        if (total > exportMaxRows) {
            throw new BusinessRuleException(ErrorCode.AUDIT_EXPORT_TOO_LARGE,
                    "El filtro devuelve " + total + " filas y el tope es " + exportMaxRows + ": acote el rango")
                    .with("matchingRows", total).with("maxRows", exportMaxRows);
        }
        StringBuilder csv = new StringBuilder(HEADER);
        for (AuditRecordResponse row : queries.search(filter, (int) exportMaxRows, 0)) {
            csv.append(CsvWriter.row(cells(row)));
        }
        record(caller, "AUDIT_EXPORTED", filter, Map.of("rows", total));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public AuditVerifyResponse verify(AuditCaller caller, Long fromId, Long toId) {
        if (fromId != null && toId != null && toId - fromId + 1 > MAX_WINDOW) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Un rango no puede pasar de " + MAX_WINDOW + " filas: verifique por tramos");
        }
        long max = queries.maxId().orElse(0L);
        long to = toId == null ? max : Math.min(toId, max);
        long from = fromId == null ? Math.max(1, to - DEFAULT_WINDOW + 1) : fromId;
        if (toId != null && toId < from || from < 1) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "El rango de verificación no es válido");
        }
        if (to - from + 1 > MAX_WINDOW) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Un rango no puede pasar de " + MAX_WINDOW + " filas: verifique por tramos");
        }
        BrokenLink broken = from > to ? null : queries.verifyChain(from, to).orElse(null);
        long checked = from > to ? 0 : to - from + 1;
        AuditVerifyResponse response = new AuditVerifyResponse(broken == null, from, to, checked,
                broken == null ? null : broken.brokenId(), broken == null ? null : broken.problem());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromId", from);
        details.put("toId", to);
        details.put("intact", response.intact());
        if (broken != null) {
            details.put("brokenId", broken.brokenId());
        }
        audit.record(new AuditEntry("admin", "audit_trail", "verify", "AUDIT_VERIFIED",
                broken == null ? AuditEntry.Severity.INFO : AuditEntry.Severity.CRITICAL, caller.actorId(),
                caller.ip(), caller.userAgent(), details));
        return response;
    }

    private List<String> cells(AuditRecordResponse r) {
        List<String> cells = new ArrayList<>();
        cells.add(Long.toString(r.id()));
        cells.add(DateTimeFormatter.ISO_INSTANT.format(r.createdAt()));
        cells.add(r.module());
        cells.add(r.entityName());
        cells.add(r.entityId());
        cells.add(r.action());
        cells.add(r.severity());
        cells.add(r.actorId() == null ? null : r.actorId().toString());
        cells.add(r.actorIp());
        cells.add(r.userAgent());
        cells.add(json.writeValueAsString(r.details()));
        return cells;
    }

    /** Quien mira la bitácora también queda en ella: filtros y conteo, nunca el contenido devuelto. */
    private void record(AuditCaller caller, String action, AuditFilter filter, Map<String, Object> result) {
        Map<String, Object> details = new LinkedHashMap<>();
        put(details, "module", filter.module());
        put(details, "action", filter.action());
        put(details, "entityName", filter.entityName());
        put(details, "entityId", filter.entityId());
        put(details, "actorId", filter.actorId());
        put(details, "severity", filter.severity());
        put(details, "from", filter.from());
        put(details, "to", filter.to());
        put(details, "subject", filter.subject());
        details.putAll(result);
        audit.record(new AuditEntry("admin", "audit_trail", action.equals("AUDIT_EXPORTED") ? "export" : "query",
                action, AuditEntry.Severity.INFO, caller.actorId(), caller.ip(), caller.userAgent(), details));
    }

    private static void put(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value.toString());
        }
    }
}