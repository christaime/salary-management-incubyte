-- Rename payroll periods to human-readable labels.
--
-- MONTHLY  : '2026-01 Monthly'    -> 'January 2026'
-- BI_WEEKLY: '2026-B01 Bi-weekly' -> 'BW1 January 2026'
--            '2026-B02 Bi-weekly' -> 'BW2 January 2026'
--            '2026-B03 Bi-weekly' -> 'BW1 February 2026'  (starts 2026-01-29)
--            ...
--
-- Bi-weekly numbering restarts at 1 each calendar month. The month is taken
-- from start_date (a bi-weekly period belongs to the month it starts in), so
-- B03 (2026-01-29 .. 2026-02-11) is "BW1 February 2026".
--
-- Only the 19 rows seeded by V202610173 are touched (id prefix f0000001 /
-- f0000002). Any user-created period keeps its name.

UPDATE payroll_periods
SET name = CASE frequency
    WHEN 'MONTHLY' THEN
        -- month name from start_date + year from start_date
        CASE CAST(strftime('%m', start_date) AS INTEGER)
            WHEN 1  THEN 'January'   WHEN 2  THEN 'February'
            WHEN 3  THEN 'March'     WHEN 4  THEN 'April'
            WHEN 5  THEN 'May'       WHEN 6  THEN 'June'
            WHEN 7  THEN 'July'      WHEN 8  THEN 'August'
            WHEN 9  THEN 'September' WHEN 10 THEN 'October'
            WHEN 11 THEN 'November'  WHEN 12 THEN 'December'
        END
        || ' ' || strftime('%Y', start_date)

    WHEN 'BI_WEEKLY' THEN
        -- ordinal within the month: number of bi-weekly periods of the same
        -- frequency that start earlier in the same calendar month, + 1
        'BW' || (
            SELECT COUNT(*) + 1
            FROM payroll_periods p2
            WHERE p2.frequency = 'BI_WEEKLY'
              AND p2.id LIKE 'f0000002-%'
              AND strftime('%Y-%m', p2.start_date) = strftime('%Y-%m', payroll_periods.start_date)
              AND p2.start_date < payroll_periods.start_date
        )
        || ' '
        || CASE CAST(strftime('%m', start_date) AS INTEGER)
            WHEN 1  THEN 'January'   WHEN 2  THEN 'February'
            WHEN 3  THEN 'March'     WHEN 4  THEN 'April'
            WHEN 5  THEN 'May'       WHEN 6  THEN 'June'
            WHEN 7  THEN 'July'      WHEN 8  THEN 'August'
            WHEN 9  THEN 'September' WHEN 10 THEN 'October'
            WHEN 11 THEN 'November'  WHEN 12 THEN 'December'
        END
        || ' ' || strftime('%Y', start_date)
END
WHERE id LIKE 'f0000001-%' OR id LIKE 'f0000002-%';