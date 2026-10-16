-- Seed for CurrencyRateRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.

INSERT INTO currency_rates (id, currency_code, reference_currency_code, rate) VALUES
    ('11111111-1111-1111-1111-111111111111', 'EUR', 'USD', 1.08),
    ('22222222-2222-2222-2222-222222222222', 'USD', 'EUR', 0.93),
    ('33333333-3333-3333-3333-333333333333', 'GBP', 'EUR', 1.17);