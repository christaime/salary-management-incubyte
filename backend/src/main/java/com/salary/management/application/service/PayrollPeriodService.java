package com.salary.management.application.service;

import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.dto.period.*;
import com.salary.management.application.exception.ClosePayrollPeriodException;
import com.salary.management.application.mapper.PayrollPeriodApiMapper;
import com.salary.management.application.utils.BiWeeklySubPeriodGenerator;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.exception.DomainValidationException;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.values.PayrollPeriodSnapshotResult;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.PayrollPeriodSnapshotRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.infrastructure.api.advice.ApiError;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollPeriodService {

    private static final Logger log = LoggerFactory.getLogger(PayrollPeriodService.class);

    private final PayrollPeriodRepository repository;
    private final PayrollPeriodApiMapper mapper;
    private final PlatformTransactionManager transactionManager;
    private final PayrollPeriodSnapshotRepository snapshotRepository;
    private final PayrollPeriodEarnerRankingRepository periodEarnerRankingRepository;
    private final PayrollPeriodAggregateRepository periodAggregateRepository;
    private final BiWeeklySubPeriodGenerator subPeriodGenerator;

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    public DataPage<PayrollPeriodResponse> search(PayrollPeriodCriteria criteria) {
        DataPage<PayrollPeriod> domainPage = repository.page(criteria);
        return domainPage.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PayrollPeriodResponse> findById(UUID id) {
        return repository.findById(id).map(period -> {
            return PayrollPeriodResponse.builder()
                    .id(period.getId().toString())
                    .name(period.getName())
                    .startDate(period.getStartDate())
                    .endDate(period.getEndDate())
                    .frequency(period.getFrequency())
                    .closed(period.getClosed())
                    .referenced(repository.isReferenced(period.getId()))
                    .build();
        });
    }
    // ------------------------------------------------------------------
    // Create — single
    // ------------------------------------------------------------------

    /**
     * Creates one payroll period in its own transaction.
     *
     * @param request validated request body
     * @return the created period
     */
    @Transactional
    public PayrollPeriodResponse create(PayrollPeriodCreateRequest request) {
        log.info("Creating payroll period: name='{}', frequency={}, {} .. {}",
                request.name(), request.frequency(), request.startDate(), request.endDate());
        PayrollPeriod saved = createInternal(request);
        log.info("Payroll period created: id={}, name='{}'", saved.getId(), saved.getName());
        return mapper.toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Create — batch
    // ------------------------------------------------------------------

    /**
     * Creates a batch of payroll periods.
     *
     * <p>The batch itself is not transactional. Monthly periods are saved first,
     * then bi-weekly periods, each list sorted ascending by {@code startDate}, and
     * each item is committed in its own {@code REQUIRES_NEW} transaction. A failing
     * item does not roll back its siblings — but because earlier items are already
     * committed, a later item that conflicts with one of them is rejected by the
     * normal validation rules and reported in {@code failed}.</p>
     *
     * @param requests validated request bodies
     * @return per-item outcomes
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PayrollPeriodBatchCreationResult createBatch(List<PayrollPeriodCreateRequest> requests) {
        log.info("Creating payroll period batch of {} item(s)", requests.size());

        List<PayrollPeriodCreateRequest> monthly = requests.stream()
                .filter(r -> r.frequency() == PayrollPeriodFrequency.MONTHLY)
                .sorted(Comparator.comparing(PayrollPeriodCreateRequest::startDate))
                .toList();
        List<PayrollPeriodCreateRequest> biWeekly = requests.stream()
                .filter(r -> r.frequency() == PayrollPeriodFrequency.BI_WEEKLY)
                .sorted(Comparator.comparing(PayrollPeriodCreateRequest::startDate))
                .toList();

        List<PayrollPeriodResponse> succeeded = new ArrayList<>();
        List<PayrollPeriodBatchCreationResult.FailedCreation> failed = new ArrayList<>();

        TransactionTemplate perItem = new TransactionTemplate(transactionManager);
        perItem.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (PayrollPeriodCreateRequest request : monthly) {
            runOne(perItem, request, succeeded, failed);
        }
        for (PayrollPeriodCreateRequest request : biWeekly) {
            runOne(perItem, request, succeeded, failed);
        }

        log.info("Payroll period batch finished: {} succeeded, {} failed",
                succeeded.size(), failed.size());
        return new PayrollPeriodBatchCreationResult(succeeded, failed);
    }

    /**
     * Read-only preview of the bi-weekly sub-periods that belong to a monthly
     * pay schedule.
     *
     * <p>Returns two buckets:
     * <ul>
     *   <li>{@code new} — sub-periods the generator would create (not yet in
     *       the database). Names and dates are suggestions; the client may
     *       rename them before calling {@code POST /batch}.</li>
     *   <li>{@code exist} — sub-periods already on the chain whose start lies
     *       inside the monthly range {@code [startDate, endDate)}. The client
     *       renders them read-only.</li>
     * </ul>
     *
     * <p>Validates the range against the same calendar rules as creation
     * (MONTHLY: 28–31 days inclusive; {@code startDate < endDate}). Invalid
     * ranges are rejected with {@code 400 VALIDATION_FAILED} via the usual
     * exception handler.</p>
     *
     * @param startDate inclusive start of the monthly range
     * @param endDate   inclusive end of the monthly range
     * @return the preview split into {@code new} and {@code exist}
     */
    public SubPayrollPeriodCreateResponse previewSubPeriods(LocalDate startDate, LocalDate endDate) {
        log.info("Previewing sub-periods: monthlyStart={}, monthlyEnd={}", startDate, endDate);

        // 1) Reuse the calendar validation via a probe request so the preview
        //    rejects exactly the same ranges that creation would reject.
        PayrollPeriodCreateRequest probe = PayrollPeriodCreateRequest.builder()
                .name("__preview__")
                .startDate(startDate)
                .endDate(endDate)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .build();
        validateCalendar(probe);

        // 2) Find the last bi-weekly whose start is STRICTLY before monthlyStart.
        //    The chain continues from `lastBeforeStart + 14 days`.
        //
        //    We query by `startDateBefore` (strict upper bound on startDate),
        //    NOT by endDate: a bi-weekly's end can fall in a later month, so an
        //    `endDate`-based filter would return the wrong rows (it would miss
        //    the bi-weekly whose tail overlaps the monthly range).
        PayrollPeriodCriteria beforeCriteria = PayrollPeriodCriteria.builder()
                .frequency(PayrollPeriodFrequency.BI_WEEKLY)
                .startDateBefore(startDate)            // bi.startDate <  monthlyStart
                .sortBy("startDate")
                .sortDirection(SortDirection.DESC)     // closest to monthlyStart first
                .page(0)
                .size(1)                               // we only need the last one
                .build();

        LocalDate lastBeforeStart = repository.page(beforeCriteria)
                .content()
                .stream()
                .findFirst()
                .map(PayrollPeriod::getStartDate)
                .orElse(null);
        log.info("Preview: lastBeforeStart={}", lastBeforeStart);

        // 3) Fetch the bi-weeklies whose START is inside [monthlyStart, monthlyEnd).
        //    We filter by startDate (inclusive lower) + startDateBefore (strict
        //    upper). We do NOT filter by endDate: a sub-period that starts inside
        //    the monthly range may legitimately end in the following month, and
        //    any endDate-based upper bound would either exclude it (wrong) or be
        //    arbitrary (guessing how many days past the month the end can be).
        PayrollPeriodCriteria insideCriteria = PayrollPeriodCriteria.builder()
                .frequency(PayrollPeriodFrequency.BI_WEEKLY)
                .startDate(startDate)                  // bi.startDate >= monthlyStart
                .startDateBefore(endDate)              // bi.startDate <  monthlyEnd
                .sortBy("startDate")
                .sortDirection(SortDirection.ASC)      // chronological for the UI
                .page(0)
                .size(100)                             // max page size; realistic count is 2–3
                .build();

        List<PayrollPeriodResponse> inside = repository.page(insideCriteria)
                .content()
                .stream()
                .map(mapper::toResponse)
                .toList();
        log.info("Preview: {} existing bi-weekly(s) inside [{} .. {})",
                inside.size(), startDate, endDate);

        // 4) Generate the `new` list from the last-before seed.
        //    The generator owns the chain rule and the naming (`BW<n> <Mon> <Year>`),
        //    including skipping historical positions so only bi-weeklies that fall
        //    inside the monthly range are emitted.
        List<PayrollPeriodCreateRequest> toCreate =
                subPeriodGenerator.generate(startDate, endDate, lastBeforeStart);
        log.info("Preview: generator produced {} sub-period(s)", toCreate.size());
        if (log.isDebugEnabled()) {
            for (PayrollPeriodCreateRequest p : toCreate) {
                log.debug("Preview sub-period: {} {} .. {}",
                        p.name(), p.startDate(), p.endDate());
            }
        }

        // 5) Bundle the two buckets for the frontend.
        return SubPayrollPeriodCreateResponse.builder()
                .newPeriods(toCreate)
                .exist(inside)
                .build();
    }

    /**
     * Updates one payroll period.
     *
     * <p>A closed period cannot be edited at all. A referenced period (any row in
     * the schema points at it) can only have its name changed; dates and frequency
     * are frozen because the snapshot rows are keyed on them.</p>
     *
     * @param id      period identifier
     * @param request validated request body
     * @return the updated period
     */
    @Transactional
    public PayrollPeriodResponse update(UUID id, PayrollPeriodCreateRequest request) {
        log.info("Updating payroll period: id={}, name='{}'", id, request.name());

        PayrollPeriod existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod not found: " + id));

        if (Boolean.TRUE.equals(existing.getClosed())) {
            throw new ConflictException(
                    "PERIOD_CLOSED",
                    "Pay schedule '" + existing.getName() + "' is settled and cannot be edited.",
                    Map.of("periodId", id, "periodName", existing.getName()));
        }

        if (repository.isReferenced(id)) {
            boolean datesChanged = !existing.getStartDate().equals(request.startDate())
                    || !existing.getEndDate().equals(request.endDate());
            boolean frequencyChanged = existing.getFrequency() != request.frequency();
            if (datesChanged || frequencyChanged) {
                throw new ConflictException(
                        "PERIOD_REFERENCED_NAME_ONLY",
                        "Pay schedule '" + existing.getName() + "' has payroll records; "
                                + "only its name can be changed.",
                        Map.of("periodId", id, "periodName", existing.getName()));
            }
            repository.findByName(request.name().trim()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ConflictException(
                            "PERIOD_NAME_CONFLICT",
                            "A pay schedule named '" + request.name().trim() + "' already exists.",
                            Map.of("field", "name", "conflictingPeriodId", other.getId()));
                }
            });
            PayrollPeriod saved = repository.save(existing.withName(request.name().trim()));
            log.info("Payroll period renamed: id={}, name='{}'", id, saved.getName());
            return mapper.toResponse(saved);
        }

        validateCalendar(request);
        validateAgainstExisting(request, id);

        PayrollPeriod edited = PayrollPeriod.builder()
                .id(existing.getId())
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .frequency(request.frequency())
                .closed(false)
                .build();

        PayrollPeriod saved = repository.save(edited);
        log.info("Payroll period updated: id={}, name='{}'", id, saved.getName());
        return mapper.toResponse(saved);
    }

    /**
     * Deletes one payroll period.
     *
     * @param id period identifier
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting payroll period: id={}", id);
        deleteInternal(id);
        log.info("Payroll period deleted: id={}", id);
    }

    /**
     * Deletes several payroll periods in one transaction. All-or-nothing: if any
     * item fails, nothing is deleted and the exception names the offending id.
     *
     * <p>The caller (frontend) is responsible for sending a monthly period
     * together with its bi-weekly children when the user chooses to delete the
     * whole block; the backend does not infer the block.</p>
     *
     * @param ids period identifiers
     */
    @Transactional
    public void deleteBatch(List<UUID> ids) {
        log.info("Deleting payroll period batch: {} item(s)", ids.size());
        for (UUID id : ids) {
            deleteInternal(id);
        }
        log.info("Payroll period batch deleted: {} item(s)", ids.size());
    }

    private void deleteInternal(UUID id) {
        PayrollPeriod existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod not found: " + id));

        if (Boolean.TRUE.equals(existing.getClosed())) {
            throw new ConflictException(
                    "PERIOD_CLOSED",
                    "Pay schedule '" + existing.getName() + "' is settled and cannot be deleted.",
                    Map.of("periodId", id, "periodName", existing.getName()));
        }

        if (repository.isReferenced(id)) {
            throw new ConflictException(
                    "PERIOD_REFERENCED",
                    "Pay schedule '" + existing.getName() + "' has payroll records and cannot be deleted.",
                    Map.of("periodId", id, "periodName", existing.getName()));
        }

        repository.deleteById(id);
    }

    /**
     * Pre-flight for the close flow. Runs the coverage checks on the monthly
     * period (which cover the bi-weekly sub-periods by date range) and reports
     * what would block or warn, without writing anything.
     *
     * @param id monthly period identifier
     * @return the periods that would be closed and the warnings to accept
     * @throws ClosePayrollPeriodException if the period has blockers
     */
    @Transactional(readOnly = true)
    public ClosePeriodResponse checkClose(UUID id) {
        PayrollPeriod monthly = requireClosableMonthly(id);

        PayrollPeriodSnapshotResult coverage = snapshotRepository.checkCoverage(monthly);
        if (!coverage.blockers().isEmpty()) {
            throw new ClosePayrollPeriodException(coverage.blockers(), coverage.warnings());
        }

        List<PayrollPeriod> periods = repository.findMonthlyAndSubPeriods(
                monthly.getId(), monthly.getStartDate(), monthly.getEndDate());
        List<PayrollPeriodResponse> responses = periods.stream()
                .map(mapper::toResponse)
                .toList();

        return new ClosePeriodResponse(responses, coverage.warnings());
    }

    /**
     * Settles a monthly pay schedule and its bi-weekly sub-periods.
     *
     * <p>Order: coverage check → snapshot build (aggregates + rankings) → promote
     * the DRAFT snapshots to VALIDATED → close the monthly period and every
     * bi-weekly whose {@code startDate} falls inside the monthly range. All in one
     * transaction; a blocker anywhere rolls the whole thing back, so a partially
     * closed month cannot exist.</p>
     *
     * <p>Snapshots are monthly-only by design: {@code buildAndSaveAggregates} and
     * {@code buildAndSaveRankings} already aggregate over the monthly date range,
     * which includes every bi-weekly inside it. The bi-weekly rows only need
     * their {@code closed} flag flipped.</p>
     *
     * @param id monthly period identifier
     * @return the closed periods and the accepted warnings
     * @throws ClosePayrollPeriodException if the period has blockers
     */
    @Transactional
    public ClosePeriodResponse close(UUID id) {
        log.info("Closing payroll period: id={}", id);
        PayrollPeriod monthly = requireClosableMonthly(id);

        PayrollPeriodSnapshotResult coverage = snapshotRepository.buildSaveAggregatesAndRankings(monthly);
        if (!coverage.blockers().isEmpty()) {
            throw new ClosePayrollPeriodException(coverage.blockers(), coverage.warnings());
        }
        periodAggregateRepository.validateDraftsByPeriodId(monthly.getId());
        periodEarnerRankingRepository.validateDraftsByPeriodId(monthly.getId());
        periodEarnerRankingRepository.validateDraftsPercentileByPeriodId(monthly.getId());

        int closed = repository.closeMonthlyAndSubPeriods(
                monthly.getId(), monthly.getStartDate(), monthly.getEndDate());
        log.info("Payroll period closed: id={}, rows set to closed={}", id, closed);

        List<PayrollPeriod> periods = repository.findMonthlyAndSubPeriods(
                monthly.getId(), monthly.getStartDate(), monthly.getEndDate());
        List<PayrollPeriodResponse> responses = periods.stream()
                .map(mapper::toResponse)
                .toList();

        return new ClosePeriodResponse(responses, coverage.warnings());
    }

    private PayrollPeriod requireClosableMonthly(UUID id) {
        PayrollPeriod period = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod not found: " + id));
        if (period.getFrequency() != PayrollPeriodFrequency.MONTHLY) {
            throw new DomainValidationException(List.of(new ValidationError(
                    "id", "MonthlyOnly",
                    "Only monthly pay schedules can be settled from this screen.",
                    Map.of("periodId", id, "frequency", period.getFrequency().name()))));
        }
        if (Boolean.TRUE.equals(period.getClosed())) {
            throw new ConflictException(
                    "PERIOD_CLOSED",
                    "Pay schedule '" + period.getName() + "' is already settled.",
                    Map.of("periodId", id, "periodName", period.getName()));
        }
        return period;
    }

    private void runOne(TransactionTemplate perItem,
                        PayrollPeriodCreateRequest request,
                        List<PayrollPeriodResponse> succeeded,
                        List<PayrollPeriodBatchCreationResult.FailedCreation> failed) {
        try {
            PayrollPeriod saved = perItem.execute(status -> createInternal(request));
            succeeded.add(mapper.toResponse(saved));
        } catch (DomainValidationException
                 | ConflictException
                 | DataIntegrityViolationException ex) {
            log.warn("Payroll period batch item rejected: name='{}', frequency={}, {} .. {} — {}",
                    request.name(), request.frequency(), request.startDate(),
                    request.endDate(), ex.getMessage());
            failed.add(new PayrollPeriodBatchCreationResult.FailedCreation(
                    request, toApiErrors(ex)));
        }
    }

    // ------------------------------------------------------------------
    // Create — internals
    // ------------------------------------------------------------------

    private PayrollPeriod createInternal(PayrollPeriodCreateRequest request) {
        validateCalendar(request);
        validateAgainstExisting(request, null);
        PayrollPeriod domain = PayrollPeriod.builder()
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .frequency(request.frequency())
                .closed(false)
                .build();
        return repository.save(domain);
    }

    /**
     * Enforces the shape rules per frequency:
     * MONTHLY must span between 28 and 31 days (inclusive); BI_WEEKLY must span
     * exactly 14 days. The start date itself is unconstrained for MONTHLY — it
     * does not have to be the 1st of a calendar month.
     */
    private void validateCalendar(PayrollPeriodCreateRequest request) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (!start.isBefore(end)) {
            throw new DomainValidationException(List.of(new ValidationError(
                    "startDate", "DateOrder",
                    "startDate must be before endDate", Map.of())));
        }
        switch (request.frequency()) {
            case MONTHLY -> {
                long spanDays = ChronoUnit.DAYS.between(start, end) + 1; // inclusive
                if (spanDays < 28 || spanDays > 31) {
                    LocalDate minEnd = start.plusDays(27);  // 28 days inclusive
                    LocalDate maxEnd = start.plusDays(30);  // 31 days inclusive
                    throw new DomainValidationException(List.of(new ValidationError(
                            "endDate", "MonthlySpan",
                            "A monthly pay schedule must span between 28 and 31 days",
                            Map.of("spanDays", spanDays,
                                    "minSpanDays", 28,
                                    "maxSpanDays", 31,
                                    "minEnd", minEnd.toString(),
                                    "maxEnd", maxEnd.toString()))));
                }
            }
            case BI_WEEKLY -> {
                LocalDate expectedEnd = start.plusDays(13);
                if (!expectedEnd.equals(end)) {
                    throw new DomainValidationException(List.of(new ValidationError(
                            "endDate", "BiWeeklySpan",
                            "A bi-weekly pay schedule must span exactly 14 days",
                            Map.of("expectedEnd", expectedEnd.toString()))));
                }
            }
        }
    }

    /**
     * Repository-dependent validation: duplicate name and overlapping date range for
     * the same frequency.
     *
     * <p>Overlap is {@code existing.startDate < new.endDate && new.startDate <
     * existing.endDate} — half-open on both sides, so adjacent periods do not overlap.
     * Monthly and bi-weekly may overlap each other; the check is per frequency.</p>
     *
     * @param excludingId when editing, the id of the period being edited (ignored by
     *                    both checks); {@code null} on create
     */
    private void validateAgainstExisting(PayrollPeriodCreateRequest request, UUID excludingId) {
        repository.findByName(request.name().trim()).ifPresent(existing -> {
            if (excludingId == null || !existing.getId().equals(excludingId)) {
                throw new ConflictException(
                        "PERIOD_NAME_CONFLICT",
                        "A pay schedule named '" + request.name().trim() + "' already exists.",
                        Map.of("field", "name", "conflictingPeriodId", existing.getId()));
            }
        });

        if (repository.existsOverlapping(
                request.frequency(), request.startDate(), request.endDate(), excludingId)) {
            throw new ConflictException(
                    "PERIOD_RANGE_CONFLICT",
                    "A " + label(request.frequency()) + " pay schedule already overlaps "
                            + request.startDate() + " .. " + request.endDate() + ".",
                    Map.of("field", "startDate",
                            "frequency", request.frequency().name(),
                            "startDate", request.startDate(),
                            "endDate", request.endDate()));
        }
    }

    private String label(PayrollPeriodFrequency frequency) {
        return frequency == PayrollPeriodFrequency.MONTHLY ? "monthly" : "bi-weekly";
    }

    private List<ApiError> toApiErrors(RuntimeException ex) {
        if (ex instanceof DomainValidationException dve) {
            List<ApiError> out = new ArrayList<>();
            for (ValidationError ve : dve.getErrors()) {
                out.add(new ApiError(
                        ve.code(),
                        ve.message(),
                        "ERROR",
                        ve.attributes() == null ? Map.of() : ve.attributes()));
            }
            return out;
        }
        if (ex instanceof ConflictException ce) {
            return List.of(new ApiError(
                    ce.getCode(),
                    ce.getMessage(),
                    "ERROR",
                    ce.getDetails() == null ? Map.of() : ce.getDetails()));
        }
        return List.of(new ApiError(
                "DATA_CONFLICT",
                "Request conflicts with existing data",
                "ERROR",
                Map.of()));
    }
}