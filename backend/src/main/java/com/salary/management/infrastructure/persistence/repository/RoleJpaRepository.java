package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link RoleEntity}. */
@Repository
public interface RoleJpaRepository
        extends JpaRepository<RoleEntity, UUID>, JpaSpecificationExecutor<RoleEntity> {

    /**
     * Finds a role by name.
     *
     * @param name role name
     * @return matching role, or empty
     */
    Optional<RoleEntity> findByName(String name);

    /**
     * Returns whether a role with the given name exists.
     *
     * @param name role name
     * @return {@code true} when present
     */
    boolean existsByName(String name);

    /**
     * Returns whether any periodic assignment uses this role.
     *
     * @param roleId role identifier
     * @return {@code true} when at least one assignment references it
     */
    @Query("""
            SELECT COUNT(a) > 0
            FROM EmployeePeriodicAssignmentEntity a
            WHERE a.roleId = :roleId
            """)
    boolean isReferencedByAssignment(@Param("roleId") UUID roleId);

    /**
     * Returns whether any payroll summary has this role as its denormalized assignment role.
     *
     * @param roleId role identifier
     * @return {@code true} when at least one summary references it
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.assignmentRoleId = :roleId
            """)
    boolean isReferencedByPayrollSummary(@Param("roleId") UUID roleId);

    /**
     * Returns whether any payroll period aggregate groups by this role.
     *
     * @param roleId role identifier
     * @return {@code true} when at least one aggregate references it
     */
    @Query("""
            SELECT COUNT(g) > 0
            FROM PayrollPeriodAggregateEntity g
            WHERE g.roleId = :roleId
            """)
    boolean isReferencedByAggregate(@Param("roleId") UUID roleId);

    /**
     * Returns whether any earner ranking entry is scoped to this role.
     *
     * @param roleId role identifier
     * @return {@code true} when at least one ranking references it
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM PayrollPeriodEarnerRankingEntity r
            WHERE r.roleId = :roleId
            """)
    boolean isReferencedByRanking(@Param("roleId") UUID roleId);

    /**
     * Returns whether the role is referenced anywhere in the schema.
     *
     * @param roleId role identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID roleId) {
        return isReferencedByAssignment(roleId)
                || isReferencedByPayrollSummary(roleId)
                || isReferencedByAggregate(roleId)
                || isReferencedByRanking(roleId);
    }
}