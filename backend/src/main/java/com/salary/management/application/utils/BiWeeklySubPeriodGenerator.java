package com.salary.management.application.utils;

import com.salary.management.application.dto.period.PayrollPeriodCreateRequest;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Computes the bi-weekly sub-periods that belong to a monthly pay schedule.
 *
 * <p><b>Chain rule.</b> Bi-weeklies form a single 14-day chain across time.
 * For a given monthly range {@code [monthlyStart, monthlyEnd]} (both
 * inclusive), the chain phase is anchored on the latest bi-weekly whose start
 * is strictly before {@code monthlyStart}:
 * <ul>
 *   <li>If {@code lastBeforeStart} is {@code null} — no bi-weekly exists
 *       before the monthly — the chain starts at {@code monthlyStart}.</li>
 *   <li>Otherwise the chain starts at {@code lastBeforeStart + 14 days}.</li>
 * </ul>
 * The chain is then advanced by 14-day steps until it reaches
 * {@code monthlyStart}; only positions inside {@code [monthlyStart,
 * monthlyEnd)} are emitted. If the last bi-weekly is far in the past, the
 * intermediate positions are skipped, not generated.</p>
 *
 * <p><b>Naming.</b> Names follow {@code BW<n> <Mon> <Year>}, where {@code <n>}
 * restarts at 1 for each monthly schedule, and {@code <Mon> <Year>} come from
 * the <em>monthly</em> range, not from each sub-period's own start. This keeps
 * all sub-periods of a monthly schedule grouped under the same month even when
 * the range crosses a calendar-month boundary.</p>
 */
@Component
public class BiWeeklySubPeriodGenerator {

    private static final Logger log = LoggerFactory.getLogger(BiWeeklySubPeriodGenerator.class);

    /** Distance between consecutive bi-weekly start dates, in days. */
    private static final int STEP_DAYS = 14;

    /** Offset from a bi-weekly start to its end (14 days inclusive). */
    private static final int END_OFFSET_DAYS = 13;

    /**
     * Builds the list of bi-weekly sub-periods to create for a monthly range.
     *
     * @param monthlyStart    inclusive start of the monthly range
     * @param monthlyEnd      inclusive end of the monthly range
     * @param lastBeforeStart start date of the latest bi-weekly whose start is
     *                        strictly before {@code monthlyStart}, or {@code null}
     *                        if no bi-weekly exists before the monthly
     * @return the sub-periods to create (may be empty when the chain has no
     *         position inside the monthly range)
     */
    public List<PayrollPeriodCreateRequest> generate(
            LocalDate monthlyStart,
            LocalDate monthlyEnd,
            LocalDate lastBeforeStart) {

        List<PayrollPeriodCreateRequest> out = new ArrayList<>();

        // Anchor: where the chain resumes for this monthly.
        LocalDate chainStart = lastBeforeStart == null
                ? monthlyStart
                : lastBeforeStart.plusDays(STEP_DAYS);
        log.debug("Sub-period generation: monthlyStart={}, monthlyEnd={}, lastBeforeStart={}, chainStart={}",
                monthlyStart, monthlyEnd, lastBeforeStart, chainStart);

        // Skip forward to the first chain position that lands INSIDE the
        // monthly range. Without this, an old lastBeforeStart would produce
        // a long list of historical sub-periods that don't belong to this
        // monthly schedule. The chain phase is preserved, only the historical
        // positions are dropped.
        LocalDate firstStart = chainStart;
        int skipped = 0;
        while (firstStart.isBefore(monthlyStart)) {
            firstStart = firstStart.plusDays(STEP_DAYS);
            skipped++;
        }
        if (skipped > 0) {
            log.debug("Sub-period generation: skipped {} historical chain position(s); firstInRange={}",
                    skipped, firstStart);
        }

        int index = 1;
        LocalDate cursor = firstStart;
        while (cursor.isBefore(monthlyEnd)) {
            LocalDate end = cursor.plusDays(END_OFFSET_DAYS);

            String name = "BW" + index
                    + " " + monthAbbrev(monthlyStart)
                    + " " + monthlyStart.getYear();

            out.add(PayrollPeriodCreateRequest.builder()
                    .name(name)
                    .startDate(cursor)
                    .endDate(end)
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY)
                    .build());

            log.debug("Sub-period generation: {} {} .. {}", name, cursor, end);

            cursor = cursor.plusDays(STEP_DAYS);
            index++;
        }

        log.debug("Sub-period generation: produced {} item(s)", out.size());
        return out;
    }

    /** Three-letter English month abbreviation, e.g. {@code "Jan"}, {@code "May"}. */
    private String monthAbbrev(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}