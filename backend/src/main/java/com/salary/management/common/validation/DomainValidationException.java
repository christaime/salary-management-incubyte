package com.salary.management.common.validation;

import java.util.List;

public class DomainValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public DomainValidationException(List<ValidationError> errors) {
        super("Validation failed on " + errors.size() + " field(s)");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}