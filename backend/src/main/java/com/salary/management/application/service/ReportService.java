package com.salary.management.application.service;

import com.salary.management.application.dto.aggregate.AggregateReportRequest;
import com.salary.management.application.dto.aggregate.AggregateReportResponse;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.application.snapshot.SnapshotBuilder;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.model.values.SnapshotBuildResult;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;                                                            // NEW
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class ReportService {

    protected final PayrollPeriodAggregateRepository payrollPeriodAggregateRepository;
    protected final PayrollPeriodEarnerRankingRepository payrollPeriodEarnerRankingRepository;
    protected final SnapshotBuilder snapshotBuilder;
    private final EntityManager entityManager;

    protected AggregateReportResponse emptyResponse(AggregateReportRequest request, String referenceCurrency) {
        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                request.dateRange(),
                request.groupKeys(),
                referenceCurrency,
                List.of(),
                List.of());
    }

    @Transactional
    protected List<ReportWarning> buildAndPersistSnapshots(List<PayrollPeriod> periods) {
        if (periods.isEmpty()) {
            log.debug("No periods to snapshot; skipping");
            return List.of();
        }
        log.debug("Building and persisting snapshots for {} period(s)", periods.size());

        // ---------- Phase 1: build ----------  (unchanged)
        List<ReportWarning> collectedWarnings = new ArrayList<>();
        List<SnapshotBuildResult> built = new ArrayList<>();
        for (PayrollPeriod period : periods) {
            log.debug("Building snapshot for periodId={}", period.getId());
            SnapshotBuildResult result = snapshotBuilder.build(period);
            collectedWarnings.addAll(result.warnings());
            if (!result.blockers().isEmpty()) {
                log.warn("Snapshot build for periodId={} returned {} blocker(s); "
                                + "aborting report generation",
                        period.getId(), result.blockers().size());
                throw new ReportBlockedException(result.blockers(), collectedWarnings);
            }
            built.add(result);
        }

        // ---------- Phase 2: persist ----------  (only this part changes)
        for (int i = 0; i < periods.size(); i++) {
            UUID periodId = periods.get(i).getId();
            SnapshotBuildResult result = built.get(i);

            log.debug("Persisting snapshot for periodId={}: aggregates={}, rankings={}, percentiles={}",
                    periodId,
                    result.aggregates().size(),
                    result.rankingAndPercentiles().rankings().size(),
                    result.rankingAndPercentiles().percentiles().size());

            // (1) Drop any pending state from the previous iteration.
            //     Without this, the DELETE below auto-flushes the previous
            //     iteration's queued INSERTs and they collide on the unique key.
            log.debug("Persisting snapshot for periodId={} — flush+clear pending persistence context",
                    periodId);
            entityManager.flush();
            entityManager.clear();

            // (2) Delete the old DRAFT rows for this period.
            int aggregatesDeleted    = payrollPeriodAggregateRepository
                    .deleteDraftsByPayrollPeriodId(periodId);
            int rankingsDeleted      = payrollPeriodEarnerRankingRepository
                    .deleteDraftsByPayrollPeriodId(periodId);
            int percentilesDeleted   = payrollPeriodEarnerRankingRepository
                    .deleteDraftsPercentileByPayrollPeriodId(periodId);

            log.debug("Persisting snapshot for periodId={} — deleted DRAFT rows: "
                            + "aggregates={}, rankings={}, percentiles={}",
                    periodId, aggregatesDeleted, rankingsDeleted, percentilesDeleted);

            // (3) Force the DELETEs to reach SQLite before any INSERT is queued.
            log.debug("Persisting snapshot for periodId={} — flushing DELETEs to DB", periodId);
            entityManager.flush();

            // (4) Persist the fresh snapshot.
            try {
                payrollPeriodAggregateRepository.saveAll(result.aggregates());
                payrollPeriodEarnerRankingRepository.saveAll(
                        result.rankingAndPercentiles().rankings(),
                        result.rankingAndPercentiles().percentiles());

                // Force the INSERTs to be sent now so any UNIQUE violation is
                // reported against *this* periodId, not against the next
                // iteration's delete (which is what mis-attributed the error
                // in the previous run).
                entityManager.flush();
            } catch (RuntimeException ex) {
                log.error("Persisting snapshot for periodId={} failed: "
                                + "aggregates={}, rankings={}, percentiles={}",
                        periodId,
                        result.aggregates().size(),
                        result.rankingAndPercentiles().rankings().size(),
                        result.rankingAndPercentiles().percentiles().size(),
                        ex);
                throw ex;
            }

            log.debug("Persisted snapshot for periodId={}", periodId);
        }

        log.debug("Snapshot build complete: {} period(s), {} warning(s)",
                periods.size(), collectedWarnings.size());
        return collectedWarnings;
    }
}