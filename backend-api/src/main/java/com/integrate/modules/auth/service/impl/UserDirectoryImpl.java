package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.entity.DocType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDirectoryImpl implements UserDirectory {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .map(u -> new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getDocType(),
                        u.getDocNumber(), u.getCredentialStatus()))
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> findAccountByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).filter(u -> !u.isClosed()).map(this::view);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> findAccountByDocument(DocType docType, String docNumber) {
        return userRepository.findByDocTypeAndDocNumber(docType, docNumber).filter(u -> !u.isClosed())
                .map(this::view);
    }

    @Override
    @Transactional(readOnly = true)
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

    private AccountView view(User user) {
        return new AccountView(user.getId(), user.getFullName(), user.getEmail(), user.getDocType(),
                user.getDocNumber(), user.getStatus().name(), userRoleRepository.findActiveRoleCodes(user.getId()));
    }
}
