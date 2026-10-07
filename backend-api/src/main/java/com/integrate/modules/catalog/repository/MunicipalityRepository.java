package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Municipality;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityRepository extends JpaRepository<Municipality, String> {

    List<Municipality> findByDepartmentCodeOrderByNameAsc(String departmentCode);
}
