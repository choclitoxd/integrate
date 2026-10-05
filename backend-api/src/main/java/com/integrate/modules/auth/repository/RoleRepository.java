package com.integrate.modules.auth.repository;

import com.integrate.modules.auth.entity.Role;
import com.integrate.modules.auth.entity.RoleCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByCode(RoleCode code);
}
