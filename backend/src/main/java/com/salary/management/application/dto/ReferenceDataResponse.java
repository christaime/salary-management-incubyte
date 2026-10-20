package com.salary.management.application.dto;

import lombok.Builder;

@Builder
public record ReferenceDataResponse(
        String id,
        String code,
        String name
) {}