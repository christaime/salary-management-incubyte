package com.salary.management.application.dto;

import java.util.UUID;

/**
 * Reference to a dimension value: a stable code and a human-readable name.
 *
 * @param code stable code
 * @param name display name
 */
public record RefDto(UUID id, String code, String name) {
}