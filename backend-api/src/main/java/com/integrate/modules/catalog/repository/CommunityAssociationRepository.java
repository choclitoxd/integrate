package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.CommunityAssociation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityAssociationRepository extends JpaRepository<CommunityAssociation, UUID> {

    boolean existsByNitOrRntNumber(String nit, String rntNumber);

    Page<CommunityAssociation> findByDeletedAtIsNull(Pageable pageable);
}
