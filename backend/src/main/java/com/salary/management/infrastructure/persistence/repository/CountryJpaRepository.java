package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link CountryEntity}. */
@Repository
public interface CountryJpaRepository
        extends JpaRepository<CountryEntity, UUID>, JpaSpecificationExecutor<CountryEntity> {

    /**
     * Finds a country by ISO code.
     *
     * @param isoCode ISO code
     * @return matching country, or empty
     */
    Optional<CountryEntity> findByIsoCode(String isoCode);

    /**
     * Returns whether a country with the given ISO code exists.
     *
     * @param isoCode ISO code
     * @return {@code true} when present
     */
    boolean existsByIsoCode(String isoCode);


    /**
     * Returns whether any employee lists this country as their origin country.
     *
     * @param countryId country identifier
     * @return {@code true} when at least one employee references it
     */
    @Query("""
            SELECT COUNT(e) > 0
            FROM EmployeeEntity e
            WHERE e.originCountryId = :countryId
            """)
    boolean isReferencedByEmployee(@Param("countryId") UUID countryId);

    /**
     * Returns whether any periodic assignment uses this country.
     *
     * @param countryId country identifier
     * @return {@code true} when at least one assignment references it
     */
    @Query("""
            SELECT COUNT(a) > 0
            FROM EmployeePeriodicAssignmentEntity a
            WHERE a.countryId = :countryId
            """)
    boolean isReferencedByAssignment(@Param("countryId") UUID countryId);

    /**
     * Returns whether any payroll summary has this country as its denormalized
     * assignment country.
     *
     * @param countryId country identifier
     * @return {@code true} when at least one summary references it
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.assignmentCountryId = :countryId
            """)
    boolean isReferencedByPayrollSummary(@Param("countryId") UUID countryId);

    /**
     * Returns whether any payroll period aggregate groups by this country.
     *
     * @param countryId country identifier
     * @return {@code true} when at least one aggregate references it
     */
    @Query("""
            SELECT COUNT(g) > 0
            FROM PayrollPeriodAggregateEntity g
            WHERE g.countryId = :countryId
            """)
    boolean isReferencedByAggregate(@Param("countryId") UUID countryId);

    /**
     * Returns whether any earner ranking entry is scoped to this country.
     *
     * @param countryId country identifier
     * @return {@code true} when at least one ranking references it
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM PayrollPeriodEarnerRankingEntity r
            WHERE r.countryId = :countryId
            """)
    boolean isReferencedByRanking(@Param("countryId") UUID countryId);

    /**
     * Returns whether the country is referenced anywhere in the schema. Combines all
     * referrer checks so callers do not need to know the referrer set.
     *
     * @param countryId country identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID countryId) {
        return isReferencedByEmployee(countryId)
                || isReferencedByAssignment(countryId)
                || isReferencedByPayrollSummary(countryId)
                || isReferencedByAggregate(countryId)
                || isReferencedByRanking(countryId);
    }
}