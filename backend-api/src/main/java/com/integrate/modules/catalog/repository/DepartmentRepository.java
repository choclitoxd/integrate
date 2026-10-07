package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {

    List<Department> findAllByOrderByNameAsc();
}
