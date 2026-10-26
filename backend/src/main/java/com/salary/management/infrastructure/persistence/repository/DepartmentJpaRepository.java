package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link DepartmentEntity}. */
@Repository
public interface DepartmentJpaRepository
        extends JpaRepository<DepartmentEntity, UUID>,
        JpaSpecificationExecutor<DepartmentEntity> {

    /**
     * Finds a department by name.
     *
     * @param name department name
     * @return matching department, or empty
     */
    Optional<DepartmentEntity> findByName(String name);

    /**
     * Returns whether a department with the given name exists.
     *
     * @param name department name
     * @return {@code true} when present
     */
    boolean existsByName(String name);

    /**
     * Finds a department by code.
     *
     * @param code department code
     * @return matching department, or empty
     */
    Optional<DepartmentEntity> findByCode(String code);

    /**
     * Returns whether a department with the given code exists.
     *
     * @param code department code
     * @return {@code true} when present
     */
    boolean existsByCode(String code);

    /**
     * Returns whether any periodic assignment uses this department.
     *
     * @param departmentId department identifier
     * @return {@code true} when at least one assignment references it
     */
    @Query("""
            SELECT COUNT(a) > 0
            FROM EmployeePeriodicAssignmentEntity a
            WHERE a.departmentId = :departmentId
            """)
    boolean isReferencedByAssignment(@Param("departmentId") UUID departmentId);

    /**
     * Returns whether any payroll summary has this department as its denormalized
     * assignment department.
     *
     * @param departmentId department identifier
     * @return {@code true} when at least one summary references it
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.assignmentDepartmentId = :departmentId
            """)
    boolean isReferencedByPayrollSummary(@Param("departmentId") UUID departmentId);

    /**
     * Returns whether any payroll period aggregate groups by this department.
     *
     * @param departmentId department identifier
     * @return {@code true} when at least one aggregate references it
     */
    @Query("""
            SELECT COUNT(g) > 0
            FROM PayrollPeriodAggregateEntity g
            WHERE g.departmentId = :departmentId
            """)
    boolean isReferencedByAggregate(@Param("departmentId") UUID departmentId);

    /**
     * Returns whether any earner ranking entry is scoped to this department.
     *
     * @param departmentId department identifier
     * @return {@code true} when at least one ranking references it
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM PayrollPeriodEarnerRankingEntity r
            WHERE r.departmentId = :departmentId
            """)
    boolean isReferencedByRanking(@Param("departmentId") UUID departmentId);

    /**
     * Returns whether any earner percentile entry is scoped to this department.
     *
     * @param departmentId department identifier
     * @return {@code true} when at least one percentile references it
     */
    @Query("""
            SELECT COUNT(p) > 0
            FROM PayrollPeriodEarnerPercentileEntity p
            WHERE p.departmentId = :departmentId
            """)
    boolean isReferencedByPercentile(@Param("departmentId") UUID departmentId);

    /**
     * Returns whether the department is referenced anywhere in the schema.
     *
     * @param departmentId department identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID departmentId) {
        return isReferencedByAssignment(departmentId)
                || isReferencedByPayrollSummary(departmentId)
                || isReferencedByAggregate(departmentId)
                || isReferencedByRanking(departmentId)
                || isReferencedByPercentile(departmentId);
    }
}