package com.salary.management.infrastructure.persistence.adapter.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataIntegrityMessagesTest {

    @Test
    void referencedIncludesEntityTypeAndId() {
        UUID id = UUID.randomUUID();
        var ex = DataIntegrityMessages.referenced("Country", id);
        assertEquals("Country is referenced and cannot be deleted: id=" + id,
                ex.getMessage());
    }

    @Test
    void invariantUsesDotSeparator() {
        var ex = DataIntegrityMessages.invariant("CurrencyRate", "saveAll", "reason");
        assertEquals("CurrencyRate.saveAll: reason", ex.getMessage());
    }

    @Test
    void invalidRowUsesCommaAndEquals() {
        UUID id = UUID.randomUUID();
        var ex = DataIntegrityMessages.invalidRow("Role", id, "missing name");
        assertEquals("Role is invalid: id=" + id + ", reason=missing name",
                ex.getMessage());
    }
}