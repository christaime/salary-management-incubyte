package com.salary.management.application.dto;

/**
 * Min, max, and average of a monetary measure within a group.
 *
 * @param min     smallest amount
 * @param max     largest amount
 * @param average mean amount
 */
public record StatisticsDto(AmountDto min, AmountDto max, AmountDto average) {
}