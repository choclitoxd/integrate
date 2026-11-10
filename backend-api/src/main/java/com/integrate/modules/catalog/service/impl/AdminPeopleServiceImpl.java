package com.integrate.modules.catalog.service.impl;

import com.integrate.common.audit.AuditEntry;
import com.integrate.common.audit.AuditEntry.Severity;
import com.integrate.common.audit.AuditTrail;
import com.integrate.common.audit.SensitiveAccessRecorder;
import com.integrate.common.audit.SensitiveAccessRecorder.Access;
import com.integrate.common.payload.PageResponse;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.service.AssistedAccountService;
import com.integrate.modules.auth.service.RegistrationService;
import com.integrate.modules.auth.service.RegistrationService.AdminAccountCommand;
import com.integrate.modules.auth.service.UserDirectory;
import com.integrate.modules.auth.service.UserDirectory.AccountView;
import com.integrate.modules.auth.service.UserDirectory.UserSummary;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.RegisteredUser;
import com.integrate.modules.catalog.dto.request.AdminUserCreateRequest;
import com.integrate.modules.catalog.dto.response.AdminHostResponse;
import com.integrate.modules.catalog.dto.response.AdminLinkManagerResponse;
import com.integrate.modules.catalog.dto.response.AdminUserDetailResponse;
import com.integrate.modules.catalog.dto.response.AdminUserResponse;
import com.integrate.modules.catalog.entity.AccreditationStatus;
import com.integrate.modules.catalog.entity.CommunityAssociation;
import com.integrate.modules.catalog.entity.HostProfile;
import com.integrate.modules.catalog.entity.LinkManagerProfile;
import com.integrate.modules.catalog.repository.CommunityAssociationRepository;
import com.integrate.modules.catalog.repository.HostProfileRepository;
import com.integrate.modules.catalog.repository.LinkManagerProfileRepository;
import com.integrate.modules.catalog.service.AdminPeopleService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de personas por el administrador (ADR-0043 §1). Lee las cuentas solo por la API pública de {@code auth}
 * ({@link UserDirectory}) y deja una fila {@code SENSITIVE_DATA_ACCESSED} por consulta, de lo último que ocurre en
 * la transacción (encadenamiento de la bitácora). La transacción no es de solo lectura porque escribe esa fila.
 */
@Service
@RequiredArgsConstructor
public class AdminPeopleServiceImpl implements AdminPeopleService {

    private static final String MODULE = "catalog";
    private static final String ENTITY = "admin_people";
    private static final String SYNTHETIC_SUFFIX = "@" + AssistedAccountService.SYNTHETIC_EMAIL_DOMAIN;
    private static final String UNKNOWN_NAME = "(cuenta cerrada)";

    private final UserDirectory users;
    private final HostProfileRepository hostRepository;
    private final LinkManagerProfileRepository managerRepository;
    private final CommunityAssociationRepository associationRepository;
    private final SensitiveAccessRecorder accessRecorder;
    private final LookupRateLimiter rateLimiter;
    private final RegistrationService registration;
    private final AuditTrail auditTrail;

    // ------------------------------------------------------------------------------------------------ búsqueda

    @Override
    @Transactional
    public PageResponse<AdminUserResponse> searchByEmail(String email, UUID adminId, ClientInfo client) {
        rateLimiter.acquire(adminId);
        // Una cuenta con correo sintético no se encuentra por correo: responde igual que un correo inexistente.
        Optional<AccountView> found = users.findAccountByEmail(email.trim())
                .filter(account -> !isSynthetic(account.email()));
        return lookup(found, false, "ADMIN_USER_LOOKUP_EMAIL", adminId, client);
    }

    @Override
    @Transactional
    public PageResponse<AdminUserResponse> searchByDocument(DocType docType, String docNumber, UUID adminId,
                                                            ClientInfo client) {
        rateLimiter.acquire(adminId);
        return lookup(users.findAccountByDocument(docType, docNumber.trim()), true, "ADMIN_USER_LOOKUP_DOCUMENT",
                adminId, client);
    }

    private PageResponse<AdminUserResponse> lookup(Optional<AccountView> found, boolean maskEmail, String purpose,
                                                   UUID adminId, ClientInfo client) {
        List<AdminUserResponse> items = found.map(account -> summary(account, maskEmail)).stream().toList();
        // Se registra también la búsqueda sin coincidencia: un barrido deja huella. El criterio no se guarda.
        record(purpose, items.isEmpty() ? List.of() : List.of("fullName", maskEmail ? "emailMasked" : "email"),
                items.stream().map(item -> item.userId().toString()).toList(), adminId, client);
        return new PageResponse<>(items, 0, 1, items.size(), items.size());
    }

    // -------------------------------------------------------------------------------------------------- detalle

    @Override
    @Transactional
    public AdminUserDetailResponse detail(UUID userId, UUID adminId, ClientInfo client) {
        AccountView account = users.findAccount(userId).orElseThrow(() -> CatalogErrors.notFound("User"));
        Optional<HostProfile> host = hostRepository.findByUserIdAndDeletedAtIsNull(userId);
        Optional<LinkManagerProfile> manager = managerRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<AdminUserDetailResponse.RoleHistory> history = users.roleHistory(userId).stream()
                .map(entry -> new AdminUserDetailResponse.RoleHistory(entry.role().name(), entry.revokedAt(),
                        entry.revokedBy()))
                .toList();
        record("ADMIN_USER_DETAIL", List.of("fullName", "emailMasked", "docNumberLast4"),
                List.of(userId.toString()), adminId, client);
        return new AdminUserDetailResponse(account.userId(), account.fullName(), maskEmail(account.email()),
                account.status(), account.docType(), last4(account.docNumber()), roleNames(account),
                host.map(HostProfile::getId).orElse(null), manager.map(LinkManagerProfile::getId).orElse(null),
                manager.map(LinkManagerProfile::getAccreditationStatus).orElse(null), history);
    }

    // ------------------------------------------------------------------------------------------------ listados

    @Override
    @Transactional
    public PageResponse<AdminUserResponse> listUsers(RoleCode role, Pageable pageable, UUID adminId,
                                                     ClientInfo client) {
        Page<AccountView> page = users.listAccounts(role, pageable);
        List<AdminUserResponse> items = page.getContent().stream().map(account -> summary(account, false)).toList();
        record("ADMIN_USER_LIST", List.of("fullName", "email"),
                items.stream().map(item -> item.userId().toString()).toList(), adminId, client);
        return PageResponse.from(page, items);
    }

    @Override
    @Transactional
    public AdminUserResponse createUser(AdminUserCreateRequest request, UUID adminId) {
        RegisteredUser created = registration.registerByAdmin(new AdminAccountCommand(request.email(),
                request.password(), request.fullName(), request.docType(), request.docNumber(), request.phone()));
        AdminUserResponse response = summary(users.findAccount(created.userId())
                .orElseThrow(() -> CatalogErrors.notFound("User")), false);
        // Debe ser lo último de la transacción (encadenamiento de la bitácora); sin datos personales.
        auditTrail.record(new AuditEntry("catalog", "admin_people", created.userId().toString(),
                "USER_CREATED_BY_ADMIN", Severity.INFO, adminId, null, null,
                Map.of("userId", created.userId().toString())));
        return response;
    }

    @Override
    @Transactional
    public PageResponse<AdminLinkManagerResponse> listLinkManagers(AccreditationStatus status, Pageable pageable,
                                                                   UUID adminId, ClientInfo client) {
        Page<LinkManagerProfile> page = status == null
                ? managerRepository.findByDeletedAtIsNull(pageable)
                : managerRepository.findByAccreditationStatusAndDeletedAtIsNull(status, pageable);
        Set<UUID> userIds = new HashSet<>();
        page.forEach(profile -> {
            userIds.add(profile.getUserId());
            if (profile.getAccreditedBy() != null) {
                userIds.add(profile.getAccreditedBy());
            }
        });
        Map<UUID, UserSummary> people = users.findSummaries(userIds);
        Map<UUID, CommunityAssociation> associations = associationsOf(
                page.map(LinkManagerProfile::getAssociationId).getContent());
        List<AdminLinkManagerResponse> items = page.getContent().stream().map(profile -> {
            UserSummary person = people.get(profile.getUserId());
            UserSummary accreditedBy = profile.getAccreditedBy() == null ? null
                    : people.get(profile.getAccreditedBy());
            return new AdminLinkManagerResponse(profile.getId(), profile.getUserId(), nameOf(person),
                    person == null ? null : person.email(), profile.getAssociationId(),
                    associationName(associations, profile.getAssociationId()), profile.getAccreditationStatus(),
                    profile.getAccreditedOn(), accreditedBy == null ? null : accreditedBy.fullName());
        }).toList();
        record("ADMIN_USER_LIST", List.of("fullName", "email"), userIds.stream().map(UUID::toString).toList(),
                adminId, client);
        return PageResponse.from(page, items);
    }

    @Override
    @Transactional
    public PageResponse<AdminHostResponse> listHosts(UUID associationId, Pageable pageable, UUID adminId,
                                                     ClientInfo client) {
        Page<HostProfile> page = associationId == null
                ? hostRepository.findByDeletedAtIsNull(pageable)
                : hostRepository.findByAssociationIdAndDeletedAtIsNull(associationId, pageable);
        Set<UUID> userIds = page.getContent().stream().map(HostProfile::getUserId).collect(Collectors.toSet());
        Map<UUID, UserSummary> people = users.findSummaries(userIds);
        Map<UUID, CommunityAssociation> associations = associationsOf(
                page.map(HostProfile::getAssociationId).getContent());
        List<AdminHostResponse> items = page.getContent().stream().map(profile -> {
            UserSummary person = people.get(profile.getUserId());
            String email = person == null ? null : person.email();
            return new AdminHostResponse(profile.getId(), profile.getUserId(), nameOf(person), email,
                    email != null && isSynthetic(email), profile.getAssociationId(),
                    associationName(associations, profile.getAssociationId()), profile.isVerified(),
                    profile.getCreatedAt());
        }).toList();
        record("ADMIN_USER_LIST", List.of("fullName", "email"), userIds.stream().map(UUID::toString).toList(),
                adminId, client);
        return PageResponse.from(page, items);
    }

    // -------------------------------------------------------------------------------------------------- helpers

    private AdminUserResponse summary(AccountView account, boolean maskEmail) {
        Optional<HostProfile> host = hostRepository.findByUserIdAndDeletedAtIsNull(account.userId());
        Optional<LinkManagerProfile> manager = managerRepository.findByUserIdAndDeletedAtIsNull(account.userId());
        return new AdminUserResponse(account.userId(), account.fullName(),
                maskEmail ? maskEmail(account.email()) : account.email(), maskEmail, account.status(),
                roleNames(account), host.map(HostProfile::getId).orElse(null),
                manager.map(LinkManagerProfile::getId).orElse(null),
                manager.map(LinkManagerProfile::getAccreditationStatus).orElse(null));
    }

    private Map<UUID, CommunityAssociation> associationsOf(Collection<UUID> ids) {
        return associationRepository.findAllById(Set.copyOf(ids)).stream()
                .collect(Collectors.toMap(CommunityAssociation::getId, Function.identity()));
    }

    private static String associationName(Map<UUID, CommunityAssociation> associations, UUID id) {
        CommunityAssociation association = associations.get(id);
        return association == null ? null : association.getLegalName();
    }

    private static String nameOf(UserSummary person) {
        return person == null ? UNKNOWN_NAME : person.fullName();
    }

    private static List<String> roleNames(AccountView account) {
        return account.roles().stream().map(RoleCode::name).toList();
    }

    private static boolean isSynthetic(String email) {
        return email.toLowerCase(java.util.Locale.ROOT).endsWith(SYNTHETIC_SUFFIX);
    }

    /** {@code juan.perez@ejemplo.com} → {@code j***@ejemplo.com}. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String last4(String docNumber) {
        return docNumber.length() <= 4 ? docNumber : docNumber.substring(docNumber.length() - 4);
    }

    private void record(String purpose, List<String> fields, List<String> subjects, UUID adminId,
                        ClientInfo client) {
        accessRecorder.record(new Access(MODULE, ENTITY, adminId.toString(), purpose, fields, subjects, adminId,
                client.ipAddress(), client.userAgent()));
    }
}
