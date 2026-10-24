package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A payroll period bounded by {@code startDate} and {@code endDate}.
 *
 * <p>Invariant: {@code startDate} must be strictly before {@code endDate}.</p>
 */
@Getter
public final class PayrollPeriod {

    @NotNull
    private final UUID id;

    @NotBlank
    @Size(min = 2, max = 100)
    private final String name;

    @NotNull
    private final LocalDate startDate;

    @NotNull
    private final LocalDate endDate;

    @NotNull
    private final PayrollPeriodFrequency frequency;

    @NotNull
    private final Boolean closed;

    private PayrollPeriod(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.frequency = builder.frequency;
        this.closed = builder.closed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PayrollPeriod withName(String newName) {
        return toBuilder().name(newName).build();
    }

    public PayrollPeriod withClosed(boolean newClosed) {
        return toBuilder().closed(newClosed).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .startDate(this.startDate)
                .endDate(this.endDate)
                .frequency(this.frequency)
                .closed(this.closed);
    }

    /**
     * Hand-written builder validating the composed period, including the
     * {@code startDate} before {@code endDate} invariant.
     */
    public static final class Builder {

        private UUID id;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private PayrollPeriodFrequency frequency;
        private Boolean closed;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder frequency(PayrollPeriodFrequency frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder closed(Boolean closed) {
            this.closed = closed;
            return this;
        }

        public PayrollPeriod build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }

            PayrollPeriod period = new PayrollPeriod(this);

            List<ValidationError> extraErrors = new ArrayList<>();
            if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
                extraErrors.add(new ValidationError(
                        "startDate",
                        "DateOrder",
                        "startDate must be before endDate",
                        Map.of()
                ));
            }

            DomainValidation.validate(period, extraErrors);
            return period;
        }
    }
}