package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.ExperienceCategory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceCategoryRepository extends JpaRepository<ExperienceCategory, ExperienceCategory.Key> {

    /** Códigos de categoría por experiencia, para mapear páginas sin N+1. */
    @Query("""
            select ec.experienceId as experienceId, c.code as code
              from ExperienceCategory ec join Category c on c.id = ec.categoryId
             where ec.experienceId in :ids
             order by c.code""")
    List<ExperienceCategoryCode> findCodesByExperienceIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true)
    @Query("delete from ExperienceCategory ec where ec.experienceId = :experienceId")
    void deleteByExperienceId(@Param("experienceId") UUID experienceId);

    interface ExperienceCategoryCode {
        UUID getExperienceId();

        String getCode();
    }
}
