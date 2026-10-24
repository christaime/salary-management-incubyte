package com.salary.management.application.dto;

import lombok.Builder;

@Builder
public record CountryResponse(
        String id,
        String code,
        String name,
        String currencyCode
) {}