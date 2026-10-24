-- Seed for CurrencyRateRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.
-- Each row reads as: reference_amount reference_currency_code = rate currency_code.
--
-- All rows share reference_currency_code = 'USD' (single-reference trigger) and none
-- pairs a currency with itself (no-self-pair trigger). Mixing references or adding a
-- self-pair aborts the seed, which is the behaviour the tests depend on.

INSERT INTO currency_rates (id, currency_code, reference_currency_code, reference_amount, rate) VALUES
    ('11111111-1111-1111-1111-111111111111', 'EUR', 'USD', 1, 0.93),
    ('22222222-2222-2222-2222-222222222222', 'GBP', 'USD', 1, 0.79),
    ('33333333-3333-3333-3333-333333333333', 'CAD', 'USD', 1, 1.37);