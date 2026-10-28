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
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared report helpers. */
@RequiredArgsConstructor
public abstract class ReportService {

    protected final PayrollPeriodRepository payrollPeriodRepository;
    protected final PayrollPeriodAggregateRepository payrollPeriodAggregateRepository;
    protected final PayrollPeriodEarnerRankingRepository payrollPeriodEarnerRankingRepository;
    protected final SnapshotBuilder snapshotBuilder;

    protected abstract String referenceCurrency();

    protected AggregateReportResponse emptyResponse(AggregateReportRequest request) {
        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                request.dateRange(),
                request.groupKeys(),
                referenceCurrency(),
                List.of(),
                List.of());
    }

    protected List<ReportWarning> buildAndPersistSnapshots(List<PayrollPeriod> periods) {
        if (periods.isEmpty()) {
            return List.of();
        }
        List<ReportWarning> collectedWarnings = new ArrayList<>();
        List<SnapshotBuildResult> built = new ArrayList<>();
        for (PayrollPeriod period : periods) {
            SnapshotBuildResult result = snapshotBuilder.build(period);
            collectedWarnings.addAll(result.warnings());
            if (!result.blockers().isEmpty()) {
                throw new ReportBlockedException(result.blockers(), collectedWarnings);
            }
            built.add(result);
        }
        for (int i = 0; i < periods.size(); i++) {
            UUID periodId = periods.get(i).getId();
            SnapshotBuildResult result = built.get(i);
            payrollPeriodAggregateRepository.deleteDraftsByPayrollPeriodId(periodId);
            payrollPeriodEarnerRankingRepository.deleteDraftsByPayrollPeriodId(periodId);
            payrollPeriodEarnerRankingRepository.deleteDraftsPercentileByPayrollPeriodId(periodId);
            payrollPeriodAggregateRepository.saveAll(result.aggregates());
            payrollPeriodEarnerRankingRepository.saveAll(
                    result.rankingAndPercentiles().rankings(),
                    result.rankingAndPercentiles().percentiles());
        }
        return collectedWarnings;
    }
}