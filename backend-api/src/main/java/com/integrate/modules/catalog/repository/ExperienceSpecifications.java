package com.integrate.modules.catalog.repository;

import com.integrate.modules.catalog.entity.Category;
import com.integrate.modules.catalog.entity.Experience;
import com.integrate.modules.catalog.entity.ExperienceCategory;
import com.integrate.modules.catalog.entity.ExperienceStatus;
import com.integrate.modules.catalog.entity.PhysicalIntensity;
import com.integrate.modules.catalog.entity.Schedule;
import com.integrate.modules.catalog.entity.ScheduleStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de la consulta pública del catálogo (RF-04, ADR-0006 §6). Siempre parten de
 * {@link #publicCatalog()}: solo experiencias {@code APPROVED} sin baja lógica
 * (índice parcial {@code ix_experiences_public_catalog}).
 */
public final class ExperienceSpecifications {

    private ExperienceSpecifications() {
    }

    public static Specification<Experience> publicCatalog() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ExperienceStatus.APPROVED),
                cb.isNull(root.get("deletedAt")));
    }

    public static Specification<Experience> inMunicipality(String municipalityCode) {
        return (root, query, cb) -> cb.equal(root.get("municipalityCode"), municipalityCode);
    }

    /** El código DIVIPOLA del municipio empieza por el del departamento. */
    public static Specification<Experience> inDepartment(String departmentCode) {
        return (root, query, cb) -> cb.like(root.get("municipalityCode"), departmentCode + "%");
    }

    public static Specification<Experience> withIntensity(PhysicalIntensity intensity) {
        return (root, query, cb) -> cb.equal(root.get("physicalIntensity"), intensity);
    }

    public static Specification<Experience> inCategory(String categoryCode) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<ExperienceCategory> ec = sub.from(ExperienceCategory.class);
            Root<Category> c = sub.from(Category.class);
            sub.select(cb.literal(1)).where(
                    cb.equal(ec.get("experienceId"), root.get("id")),
                    cb.equal(c.get("id"), ec.get("categoryId")),
                    cb.equal(c.get("code"), categoryCode));
            return cb.exists(sub);
        };
    }

    /** Coincidencia parcial en el título sin distinguir mayúsculas (lo acelera el índice trigram de V3). */
    public static Specification<Experience> titleContains(String text) {
        String pattern = "%" + escapeLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
    }

    /** Al menos una franja {@code OPEN}, futura, que empiece en {@code [from, to)} y con {@code minSlots} cupos. */
    public static Specification<Experience> withBookableSchedule(Instant now, Instant from, Instant to, int minSlots) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<Schedule> s = sub.from(Schedule.class);
            sub.select(cb.literal(1)).where(
                    cb.equal(s.get("experienceId"), root.get("id")),
                    cb.equal(s.get("status"), ScheduleStatus.OPEN),
                    cb.greaterThan(s.get("startTime"), now),
                    cb.greaterThanOrEqualTo(s.get("startTime"), from),
                    cb.lessThan(s.get("startTime"), to),
                    cb.greaterThanOrEqualTo(s.get("availableSlots"), (short) minSlots));
            return cb.exists(sub);
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
