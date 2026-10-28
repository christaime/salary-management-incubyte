package com.salary.management.domain.model.values;

import com.salary.management.domain.model.PayrollPeriodEarnerPercentile;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;

import java.util.List;

public record RankingAndPercentileSnapshot(
        List<PayrollPeriodEarnerRanking> rankings,
        List<PayrollPeriodEarnerPercentile> percentiles
) {
    public static RankingAndPercentileSnapshot empty(){
        return new RankingAndPercentileSnapshot(List.of(), List.of());
    }
    public boolean isEmpty(){
        return (rankings() == null || rankings().isEmpty()) && (percentiles() == null || percentiles().isEmpty());
    }
}
