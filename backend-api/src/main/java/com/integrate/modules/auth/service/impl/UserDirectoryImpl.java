package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.UserDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDirectoryImpl implements UserDirectory {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final IdentityProtection identityProtection;

    @Override
    @Transactional
    public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .map(u -> new UserSummary(u.getId(), u.getFullName(), u.getEmail(), identityProtection.readPhone(u),
                        u.getDocType(), identityProtection.readDocument(u), u.getCredentialStatus()))
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
    }

    @Override
    @Transactional
    public Optional<AccountView> findAccountByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).filter(u -> !u.isClosed()).map(this::view);
    }

    @Override
    @Transactional
    public Optional<AccountView> findAccountByDocument(DocType docType, String docNumber) {
        return identityProtection.findByDocument(docType, docNumber).filter(u -> !u.isClosed())
                .map(this::view);
    }

    @Override
    @Transactional
    public Optional<AccountView> findAccount(UUID userId) {
        return userRepository.findById(userId).filter(u -> !u.isClosed()).map(this::view);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleHistoryEntry> roleHistory(UUID userId) {
        return userRoleRepository.findHistory(userId).stream()
                .map(row -> new RoleHistoryEntry(row.getCode(), row.getRevokedAt(), row.getRevokedBy()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountView> listAccounts(RoleCode role, Pageable pageable) {
        Page<User> page = role == null
                ? userRepository.findOpenAccounts(pageable)
                : userRepository.findOpenAccountsByRole(role, pageable);
        return page.map(this::view);
    }

    private AccountView view(User user) {
        return new AccountView(user.getId(), user.getFullName(), user.getEmail(), user.getDocType(),
                identityProtection.readDocument(user), user.getStatus().name(),
                userRoleRepository.findActiveRoleCodes(user.getId()));
    }
}
