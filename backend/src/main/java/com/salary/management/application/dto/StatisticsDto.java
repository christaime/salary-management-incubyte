package com.salary.management.application.dto;


public record StatisticsDto(
        StatisticsPerFrequency monthly,
        StatisticsPerFrequency biWeekly) {
}

