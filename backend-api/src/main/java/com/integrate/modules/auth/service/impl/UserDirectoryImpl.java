package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.UserDirectory;
import java.util.Collection;
import java.util.Map;
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
}
