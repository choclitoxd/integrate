package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Category;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Short> {

    List<Category> findByDeletedAtIsNullOrderByNameAsc();

    List<Category> findByCodeInAndDeletedAtIsNull(Collection<String> codes);
}
