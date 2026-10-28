package com.salary.management.application.dto.aggregate;


public record StatisticsDto(
        StatisticsPerFrequency monthly,
        StatisticsPerFrequency biWeekly) {
}

