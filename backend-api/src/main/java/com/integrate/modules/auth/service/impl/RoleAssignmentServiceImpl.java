package com.integrate.modules.auth.service.impl;

import com.integrate.common.exception.BusinessRuleException;
import com.integrate.common.exception.ErrorCode;
import com.integrate.modules.auth.entity.Role;
import com.integrate.modules.auth.entity.RoleCode;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.entity.UserRole;
import com.integrate.modules.auth.entity.UserStatus;
import com.integrate.modules.auth.repository.RoleRepository;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.repository.UserRoleRepository;
import com.integrate.modules.auth.service.RoleAssignmentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional
    public void grant(UUID userId, RoleCode role) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isClosed() && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (userRoleRepository.findActiveRoleCodes(user.getId()).contains(role)) {
            return;
        }
        Role entity = roleRepository.findByCode(role)
                .orElseThrow(() -> new IllegalStateException("Role " + role + " is missing from auth.roles"));
        userRoleRepository.save(UserRole.grant(user.getId(), entity));
    }
}
