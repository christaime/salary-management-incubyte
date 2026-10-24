package com.salary.management.common.validation;

import java.util.Map;

public record ValidationError(
        String field,
        String code,
        String message,
        Map<String, Object> attributes
) {}