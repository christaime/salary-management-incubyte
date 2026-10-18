package com.salary.management.application.dto;

/**
 * Reference to a dimension value: a stable code and a human-readable name.
 *
 * @param code stable code
 * @param name display name
 */
public record RefDto(String code, String name) {
}