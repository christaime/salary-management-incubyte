package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.values.PayrollPeriodSnapshotResult;

public interface PayrollPeriodSnapshotRepository {

    public PayrollPeriodSnapshotResult buildAndSaveAggregates(PayrollPeriod period);
    public PayrollPeriodSnapshotResult buildAndSaveRankings(PayrollPeriod period);

}
