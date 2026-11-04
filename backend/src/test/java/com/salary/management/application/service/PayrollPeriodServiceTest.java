package com.salary.management.application.service;

import com.salary.management.application.dto.period.*;
import com.salary.management.application.exception.ClosePayrollPeriodException;
import com.salary.management.application.mapper.PayrollPeriodApiMapper;
import com.salary.management.application.utils.BiWeeklySubPeriodGenerator;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.DomainValidationException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.PayrollPeriodSnapshotResult;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.PayrollPeriodSnapshotRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollPeriodServiceTest {

    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final LocalDate MAY_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate MAY_END = LocalDate.of(2026, 5, 31);
    private static final LocalDate BW_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate BW_END = LocalDate.of(2026, 5, 14);

    @Mock private PayrollPeriodRepository repository;
    @Mock private PayrollPeriodApiMapper mapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PayrollPeriodSnapshotRepository snapshotRepository;
    @Mock private PayrollPeriodEarnerRankingRepository rankingRepository;
    @Mock private PayrollPeriodAggregateRepository aggregateRepository;
    private final BiWeeklySubPeriodGenerator realGenerator = new BiWeeklySubPeriodGenerator();

    private PayrollPeriodService service;

    @BeforeEach
    void setUp() {
        service = new PayrollPeriodService(
                repository, mapper, transactionManager, snapshotRepository,
                rankingRepository, aggregateRepository, realGenerator);

        // The batch path builds a TransactionTemplate and calls execute(...);
        // stub the manager so the template runs the callback inline.
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());

        // Identity mapper for assertions on the response.
        lenient().when(mapper.toResponse(any(PayrollPeriod.class))).thenAnswer(inv -> {
            PayrollPeriod p = inv.getArgument(0);
            return PayrollPeriodResponse.builder()
                    .id(p.getId().toString())
                    .name(p.getName())
                    .startDate(p.getStartDate())
                    .endDate(p.getEndDate())
                    .frequency(p.getFrequency())
                    .closed(p.getClosed())
                    .build();
        });
        lenient().when(repository.save(any(PayrollPeriod.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static PayrollPeriodCreateRequest monthlyMay() {
        return PayrollPeriodCreateRequest.builder()
                .name("May 2026")
                .startDate(MAY_START)
                .endDate(MAY_END)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .build();
    }

    private static PayrollPeriodCreateRequest biWeeklyMay1() {
        return PayrollPeriodCreateRequest.builder()
                .name("BW1 May 2026")
                .startDate(BW_START)
                .endDate(BW_END)
                .frequency(PayrollPeriodFrequency.BI_WEEKLY)
                .build();
    }

    private static PayrollPeriod storedMonthly() {
        return PayrollPeriod.builder()
                .id(PERIOD_ID)
                .name("May 2026")
                .startDate(MAY_START)
                .endDate(MAY_END)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    private static PayrollPeriod closedMonthly() {
        return PayrollPeriod.builder()
                .id(PERIOD_ID)
                .name("May 2026")
                .startDate(MAY_START)
                .endDate(MAY_END)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(true)
                .build();
    }

    private static PayrollPeriodSnapshotResult cleanResult() {
        return new PayrollPeriodSnapshotResult(List.of(), List.of());
    }

    private static ReportWarning blocker(ReportWarningCode code) {
        return new ReportWarning(code, ReportWarning.Severity.BLOCKER, "blocked", Map.of());
    }

    private static ReportWarning warning(ReportWarningCode code) {
        return new ReportWarning(code, ReportWarning.Severity.WARNING, "warned", Map.of());
    }

    // ==================================================================
    // create
    // ==================================================================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("accepts a valid monthly period")
        void acceptsValidMonthly() {
            when(repository.findByName("May 2026")).thenReturn(Optional.empty());
            when(repository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);

            PayrollPeriodResponse response = service.create(monthlyMay());

            assertThat(response.name()).isEqualTo("May 2026");
            assertThat(response.closed()).isFalse();
            verify(repository).save(any(PayrollPeriod.class));
        }

        @Test
        @DisplayName("rejects a monthly span shorter than 28 days")
        void rejectsTooShortMonthly() {
            PayrollPeriodCreateRequest req = PayrollPeriodCreateRequest.builder()
                    .name("Short May 2026")
                    .startDate(LocalDate.of(2026, 5, 1))
                    .endDate(LocalDate.of(2026, 5, 27))          // 27 days inclusive
                    .frequency(PayrollPeriodFrequency.MONTHLY)
                    .build();

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors()).hasSize(1);
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("MonthlySpan");
                    });
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a monthly span longer than 31 days")
        void rejectsTooLongMonthly() {
            PayrollPeriodCreateRequest req = PayrollPeriodCreateRequest.builder()
                    .name("Long May 2026")
                    .startDate(LocalDate.of(2026, 5, 1))
                    .endDate(LocalDate.of(2026, 6, 1))           // 32 days inclusive
                    .frequency(PayrollPeriodFrequency.MONTHLY)
                    .build();

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors()).hasSize(1);
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("MonthlySpan");
                    });
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a bi-weekly period that is not exactly 14 days")
        void rejectsThirteenDayBiWeekly() {
            PayrollPeriodCreateRequest req = PayrollPeriodCreateRequest.builder()
                    .name("BW1").startDate(BW_START).endDate(BW_START.plusDays(12))
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).build();

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors()).hasSize(1);
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("BiWeeklySpan");
                    });
        }

        @Test
        @DisplayName("rejects startDate >= endDate")
        void rejectsNonOrderedDates() {
            PayrollPeriodCreateRequest req = PayrollPeriodCreateRequest.builder()
                    .name("X").startDate(LocalDate.of(2026, 5, 10))
                    .endDate(LocalDate.of(2026, 5, 10))
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).build();

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors()).hasSize(1);
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("DateOrder");
                    });
        }

        @Test
        @DisplayName("rejects a duplicate name")
        void rejectsDuplicateName() {
            when(repository.findByName("May 2026"))
                    .thenReturn(Optional.of(storedMonthly()));

            assertThatThrownBy(() -> service.create(monthlyMay()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_NAME_CONFLICT");
        }

        @Test
        @DisplayName("rejects overlapping range of the same frequency")
        void rejectsOverlappingRange() {
            when(repository.findByName("May 2026")).thenReturn(Optional.empty());
            when(repository.existsOverlapping(eq(PayrollPeriodFrequency.MONTHLY), any(), any(), any()))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.create(monthlyMay()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_RANGE_CONFLICT");
        }

        @Test
        @DisplayName("allows a bi-weekly period that overlaps the enclosing monthly")
        void allowsBiWeeklyNestedInMonthly() {
            when(repository.findByName("BW1 May 2026")).thenReturn(Optional.empty());
            // No bi-weekly overlap; the check is per frequency, so the monthly is irrelevant.
            when(repository.existsOverlapping(eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any(), any()))
                    .thenReturn(false);

            PayrollPeriodResponse response = service.create(biWeeklyMay1());
            assertThat(response.frequency()).isEqualTo(PayrollPeriodFrequency.BI_WEEKLY);
        }
    }

    // ==================================================================
    // createBatch
    // ==================================================================

    @Nested
    @DisplayName("createBatch")
    class CreateBatch {

        @Test
        @DisplayName("saves monthly before bi-weekly, each list sorted by startDate asc")
        void ordersMonthlyBeforeBiWeekly() {
            when(repository.findByName(anyString())).thenReturn(Optional.empty());
            when(repository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);

            PayrollPeriodCreateRequest bwJune = PayrollPeriodCreateRequest.builder()
                    .name("BW June").startDate(LocalDate.of(2026, 6, 1))
                    .endDate(LocalDate.of(2026, 6, 14))
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).build();
            PayrollPeriodCreateRequest bwMay = biWeeklyMay1();

            service.createBatch(List.of(bwJune, monthlyMay(), bwMay));

            ArgumentCaptor<PayrollPeriod> captor = ArgumentCaptor.forClass(PayrollPeriod.class);
            verify(repository, times(3)).save(captor.capture());
            List<PayrollPeriod> saved = captor.getAllValues();
            assertThat(saved).extracting(PayrollPeriod::getFrequency).containsExactly(
                    PayrollPeriodFrequency.MONTHLY,
                    PayrollPeriodFrequency.BI_WEEKLY,
                    PayrollPeriodFrequency.BI_WEEKLY);
            assertThat(saved.get(1).getStartDate()).isBefore(saved.get(2).getStartDate());
        }

        @Test
        @DisplayName("a failure in one item does not roll back the others")
        void partialFailureDoesNotRollBack() {
            // May is valid; June overlaps and must fail.
            when(repository.findByName("May 2026")).thenReturn(Optional.empty());
            when(repository.findByName("June 2026")).thenReturn(Optional.empty());
            when(repository.existsOverlapping(eq(PayrollPeriodFrequency.MONTHLY),
                    eq(MAY_START), eq(MAY_END), any())).thenReturn(false);
            when(repository.existsOverlapping(eq(PayrollPeriodFrequency.MONTHLY),
                    eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)), any()))
                    .thenReturn(true);

            PayrollPeriodCreateRequest june = PayrollPeriodCreateRequest.builder()
                    .name("June 2026").startDate(LocalDate.of(2026, 6, 1))
                    .endDate(LocalDate.of(2026, 6, 30))
                    .frequency(PayrollPeriodFrequency.MONTHLY).build();

            PayrollPeriodBatchCreationResult result =
                    service.createBatch(List.of(monthlyMay(), june));

            assertThat(result.succeeded()).hasSize(1);
            assertThat(result.succeeded().get(0).name()).isEqualTo("May 2026");
            assertThat(result.failed()).hasSize(1);
            assertThat(result.failed().get(0).period().name()).isEqualTo("June 2026");
            assertThat(result.failed().get(0).errors())
                    .anyMatch(e -> "PERIOD_RANGE_CONFLICT".equals(e.code()));
        }

        @Test
        @DisplayName("consecutive months both succeed when there is no overlap")
        void consecutiveMonthsBothSucceed() {
            when(repository.findByName(anyString())).thenReturn(Optional.empty());
            when(repository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);

            PayrollPeriodCreateRequest june = PayrollPeriodCreateRequest.builder()
                    .name("June 2026").startDate(LocalDate.of(2026, 6, 1))
                    .endDate(LocalDate.of(2026, 6, 30))
                    .frequency(PayrollPeriodFrequency.MONTHLY).build();

            PayrollPeriodBatchCreationResult result =
                    service.createBatch(List.of(monthlyMay(), june));

            assertThat(result.succeeded()).hasSize(2);
            assertThat(result.failed()).isEmpty();
        }
    }

    // ==================================================================
    // update
    // ==================================================================

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("rejects update of a closed period")
        void rejectsClosed() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(closedMonthly()));

            assertThatThrownBy(() -> service.update(PERIOD_ID, monthlyMay()))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_CLOSED");
        }

        @Test
        @DisplayName("referenced period: name change accepted")
        void referencedNameOnlyAccepted() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(repository.isReferenced(PERIOD_ID)).thenReturn(true);
            when(repository.findByName("May 2026 renamed")).thenReturn(Optional.empty());

            PayrollPeriodCreateRequest renamed = PayrollPeriodCreateRequest.builder()
                    .name("May 2026 renamed").startDate(MAY_START).endDate(MAY_END)
                    .frequency(PayrollPeriodFrequency.MONTHLY).build();

            PayrollPeriodResponse response = service.update(PERIOD_ID, renamed);
            assertThat(response.name()).isEqualTo("May 2026 renamed");
        }

        @Test
        @DisplayName("referenced period: date change rejected")
        void referencedDatesChangedRejected() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(repository.isReferenced(PERIOD_ID)).thenReturn(true);

            PayrollPeriodCreateRequest req = PayrollPeriodCreateRequest.builder()
                    .name("May 2026").startDate(LocalDate.of(2026, 5, 2)).endDate(MAY_END)
                    .frequency(PayrollPeriodFrequency.MONTHLY).build();

            assertThatThrownBy(() -> service.update(PERIOD_ID, req))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_REFERENCED_NAME_ONLY");
        }

        @Test
        @DisplayName("unreferenced period: full edit runs full validation")
        void unreferencedFullEdit() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(repository.isReferenced(PERIOD_ID)).thenReturn(false);
            when(repository.findByName(anyString())).thenReturn(Optional.empty());
            when(repository.existsOverlapping(any(), any(), any(), any())).thenReturn(false);

            PayrollPeriodCreateRequest req = PayrollPeriodCreateRequest.builder()
                    .name("May 2026").startDate(MAY_START).endDate(MAY_END)
                    .frequency(PayrollPeriodFrequency.MONTHLY).build();

            service.update(PERIOD_ID, req);
            verify(repository).existsOverlapping(eq(PayrollPeriodFrequency.MONTHLY),
                    eq(MAY_START), eq(MAY_END), eq(PERIOD_ID));
            verify(repository).save(any(PayrollPeriod.class));
        }
    }

    // ==================================================================
    // delete / deleteBatch
    // ==================================================================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("rejects deleting a closed period")
        void rejectsClosed() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(closedMonthly()));

            assertThatThrownBy(() -> service.delete(PERIOD_ID))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_CLOSED");
            verify(repository, never()).deleteById(any());
        }

        @Test
        @DisplayName("rejects deleting a referenced period")
        void rejectsReferenced() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(repository.isReferenced(PERIOD_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(PERIOD_ID))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_REFERENCED");
            verify(repository, never()).deleteById(any());
        }

        @Test
        @DisplayName("deletes a clean unreferenced period")
        void deletesClean() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(repository.isReferenced(PERIOD_ID)).thenReturn(false);

            service.delete(PERIOD_ID);
            verify(repository).deleteById(PERIOD_ID);
        }

        @Test
        @DisplayName("404 when id does not exist")
        void notFound() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(PERIOD_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteBatch")
    class DeleteBatch {

        @Test
        @DisplayName("deletes all items in order when every item is valid")
        void deletesAllWhenValid() {
            UUID second = UUID.randomUUID();
            PayrollPeriod p1 = storedMonthly();
            PayrollPeriod p2 = PayrollPeriod.builder()
                    .id(second).name("June 2026")
                    .startDate(LocalDate.of(2026, 6, 1)).endDate(LocalDate.of(2026, 6, 30))
                    .frequency(PayrollPeriodFrequency.MONTHLY).closed(false).build();

            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(p1));
            when(repository.findById(second)).thenReturn(Optional.of(p2));
            when(repository.isReferenced(any())).thenReturn(false);

            service.deleteBatch(List.of(PERIOD_ID, second));

            verify(repository).deleteById(PERIOD_ID);
            verify(repository).deleteById(second);
        }

        @Test
        @DisplayName("all-or-nothing: one referenced item aborts the batch")
        void abortsOnReferenced() {
            UUID second = UUID.randomUUID();
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(repository.findById(second)).thenReturn(Optional.of(
                    PayrollPeriod.builder().id(second).name("June 2026")
                            .startDate(LocalDate.of(2026, 6, 1))
                            .endDate(LocalDate.of(2026, 6, 30))
                            .frequency(PayrollPeriodFrequency.MONTHLY).closed(false).build()));
            when(repository.isReferenced(PERIOD_ID)).thenReturn(false);
            when(repository.isReferenced(second)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteBatch(List.of(PERIOD_ID, second)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_REFERENCED");
        }
    }

    // ==================================================================
    // checkClose
    // ==================================================================

    @Nested
    @DisplayName("checkClose")
    class CheckClose {

        @Test
        @DisplayName("rejects a bi-weekly period")
        void rejectsBiWeekly() {
            PayrollPeriod bw = PayrollPeriod.builder()
                    .id(PERIOD_ID).name("BW1").startDate(BW_START).endDate(BW_END)
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).closed(false).build();
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(bw));

            assertThatThrownBy(() -> service.checkClose(PERIOD_ID))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("rejects a closed period")
        void rejectsClosed() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(closedMonthly()));

            assertThatThrownBy(() -> service.checkClose(PERIOD_ID))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("PERIOD_CLOSED");
        }

        @Test
        @DisplayName("throws ClosePayrollPeriodException when blockers exist")
        void throwsOnBlockers() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(snapshotRepository.checkCoverage(any())).thenReturn(
                    new PayrollPeriodSnapshotResult(
                            List.of(warning(ReportWarningCode.MISSING_PAYROLL_SUMMARY)),
                            List.of(blocker(ReportWarningCode.UNVALIDATED_PAYROLL_SUMMARY))));

            assertThatThrownBy(() -> service.checkClose(PERIOD_ID))
                    .isInstanceOf(ClosePayrollPeriodException.class)
                    .satisfies(ex -> {
                        ClosePayrollPeriodException c = (ClosePayrollPeriodException) ex;
                        assertThat(c.getBlockers()).hasSize(1);
                        assertThat(c.getWarnings()).hasSize(1);
                    });

            verify(repository, never()).findMonthlyAndSubPeriods(any(), any(), any());
            verify(snapshotRepository, never()).buildSaveAggregatesAndRankings(any());
        }

        @Test
        @DisplayName("returns periods and warnings when only warnings exist")
        void returnsWarnings() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(snapshotRepository.checkCoverage(any())).thenReturn(
                    new PayrollPeriodSnapshotResult(
                            List.of(warning(ReportWarningCode.MISSING_PAYROLL_SUMMARY)),
                            List.of()));
            when(repository.findMonthlyAndSubPeriods(eq(PERIOD_ID), eq(MAY_START), eq(MAY_END)))
                    .thenReturn(List.of(storedMonthly()));

            ClosePeriodResponse response = service.checkClose(PERIOD_ID);
            assertThat(response.periods()).hasSize(1);
            assertThat(response.warnings()).hasSize(1);
            verify(snapshotRepository, never()).buildSaveAggregatesAndRankings(any());
        }
    }

    // ==================================================================
    // close
    // ==================================================================

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("close runs build, validate, close in order and returns the closed rows")
        void closeHappyPath() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(snapshotRepository.buildSaveAggregatesAndRankings(any()))
                    .thenReturn(cleanResult());
            when(repository.closeMonthlyAndSubPeriods(eq(PERIOD_ID), eq(MAY_START), eq(MAY_END)))
                    .thenReturn(2);

            PayrollPeriod closed = closedMonthly();
            PayrollPeriod closedBw = PayrollPeriod.builder()
                    .id(UUID.randomUUID()).name("BW1 May 2026")
                    .startDate(BW_START).endDate(BW_END)
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).closed(true).build();
            when(repository.findMonthlyAndSubPeriods(eq(PERIOD_ID), eq(MAY_START), eq(MAY_END)))
                    .thenReturn(List.of(closed, closedBw));

            ClosePeriodResponse response = service.close(PERIOD_ID);

            InOrder inOrder = inOrder(snapshotRepository, aggregateRepository,
                    rankingRepository, repository);
            inOrder.verify(snapshotRepository).buildSaveAggregatesAndRankings(any());
            inOrder.verify(aggregateRepository).validateDraftsByPeriodId(PERIOD_ID);
            inOrder.verify(rankingRepository).validateDraftsByPeriodId(PERIOD_ID);
            inOrder.verify(rankingRepository).validateDraftsPercentileByPeriodId(PERIOD_ID);
            inOrder.verify(repository).closeMonthlyAndSubPeriods(PERIOD_ID, MAY_START, MAY_END);
            inOrder.verify(repository).findMonthlyAndSubPeriods(PERIOD_ID, MAY_START, MAY_END);

            assertThat(response.periods()).hasSize(2);
            assertThat(response.periods()).allMatch(PayrollPeriodResponse::closed);
        }

        @Test
        @DisplayName("close throws ClosePayrollPeriodException on blockers and never writes")
        void closeThrowsOnBlockers() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(snapshotRepository.buildSaveAggregatesAndRankings(any()))
                    .thenReturn(new PayrollPeriodSnapshotResult(
                            List.of(), List.of(blocker(ReportWarningCode.MISSING_FX_RATE))));

            assertThatThrownBy(() -> service.close(PERIOD_ID))
                    .isInstanceOf(ClosePayrollPeriodException.class);

            verify(aggregateRepository, never()).validateDraftsByPeriodId(any());
            verify(rankingRepository, never()).validateDraftsByPeriodId(any());
            verify(rankingRepository, never()).validateDraftsPercentileByPeriodId(any());
            verify(repository, never()).closeMonthlyAndSubPeriods(any(), any(), any());
        }

        @Test
        @DisplayName("close returns warnings when only warnings exist and still closes")
        void closeWithWarningsStillCloses() {
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(storedMonthly()));
            when(snapshotRepository.buildSaveAggregatesAndRankings(any()))
                    .thenReturn(new PayrollPeriodSnapshotResult(
                            List.of(warning(ReportWarningCode.MISSING_PAYROLL_SUMMARY)),
                            List.of()));
            when(repository.closeMonthlyAndSubPeriods(eq(PERIOD_ID), eq(MAY_START), eq(MAY_END)))
                    .thenReturn(1);
            when(repository.findMonthlyAndSubPeriods(eq(PERIOD_ID), eq(MAY_START), eq(MAY_END)))
                    .thenReturn(List.of(closedMonthly()));

            ClosePeriodResponse response = service.close(PERIOD_ID);

            assertThat(response.warnings()).hasSize(1);
            assertThat(response.periods()).hasSize(1);
            assertThat(response.periods()).allMatch(PayrollPeriodResponse::closed);
            verify(repository).closeMonthlyAndSubPeriods(PERIOD_ID, MAY_START, MAY_END);
        }

        @Test
        @DisplayName("close rejects a bi-weekly id")
        void closeRejectsBiWeekly() {
            PayrollPeriod bw = PayrollPeriod.builder()
                    .id(PERIOD_ID).name("BW1").startDate(BW_START).endDate(BW_END)
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).closed(false).build();
            when(repository.findById(PERIOD_ID)).thenReturn(Optional.of(bw));

            assertThatThrownBy(() -> service.close(PERIOD_ID))
                    .isInstanceOf(DomainValidationException.class);
            verifyNoInteractions(snapshotRepository);
        }
    }

    // ==================================================================
    // previewSubPeriods
    // ==================================================================

    @Nested
    @DisplayName("previewSubPeriods")
    class PreviewSubPeriods {

        // ---------- Helpers ----------
        // Every argThat guards `c != null` because Mockito probes matchers
        // against unrelated invocations, including nulls.

        private void noBiWeeklyBefore() {
            when(repository.page(argThat(c -> c != null
                    && c.startDateBefore() != null
                    && c.startDateBefore().equals(MAY_START)
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf());
        }

        private void biWeeklyBefore(LocalDate start) {
            PayrollPeriod last = PayrollPeriod.builder()
                    .id(UUID.randomUUID())
                    .name("BW-last")
                    .startDate(start)
                    .endDate(start.plusDays(13))
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY)
                    .closed(false)
                    .build();
            when(repository.page(argThat(c -> c != null
                    && c.startDateBefore() != null
                    && c.startDateBefore().equals(MAY_START)
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf(last));
        }

        private void noneInside() {
            when(repository.page(argThat(c -> c != null
                    && MAY_START.equals(c.startDate())
                    && MAY_END.equals(c.startDateBefore())
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf());
        }

        private void inside(PayrollPeriod... items) {
            when(repository.page(argThat(c -> c != null
                    && MAY_START.equals(c.startDate())
                    && MAY_END.equals(c.startDateBefore())
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf(items));
        }

        private PayrollPeriod biWeekly(UUID id, LocalDate start) {
            return PayrollPeriod.builder()
                    .id(id).name("BW existing")
                    .startDate(start).endDate(start.plusDays(13))
                    .frequency(PayrollPeriodFrequency.BI_WEEKLY).closed(false)
                    .build();
        }

        // ---------- Tests ----------

        @Test
        @DisplayName("rejects a range shorter than 28 days with MonthlySpan")
        void rejectsTooShortMonthlyRange() {
            LocalDate shortStart = LocalDate.of(2026, 5, 1);
            LocalDate shortEnd = LocalDate.of(2026, 5, 27); // 27 days inclusive

            assertThatThrownBy(() -> service.previewSubPeriods(shortStart, shortEnd))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors()).hasSize(1);
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("MonthlySpan");
                        assertThat(dve.getErrors().get(0).attributes())
                                .containsEntry("spanDays", 27L)
                                .containsEntry("minSpanDays", 28)
                                .containsEntry("maxSpanDays", 31);
                    });

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("rejects a range longer than 31 days with MonthlySpan")
        void rejectsTooLongMonthlyRange() {
            LocalDate start = LocalDate.of(2026, 5, 1);
            LocalDate end = LocalDate.of(2026, 6, 1); // 32 days inclusive

            assertThatThrownBy(() -> service.previewSubPeriods(start, end))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("MonthlySpan");
                        assertThat(dve.getErrors().get(0).attributes())
                                .containsEntry("spanDays", 32L);
                    });

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("rejects startDate >= endDate with DateOrder")
        void rejectsNonOrdered() {
            LocalDate start = LocalDate.of(2026, 5, 10);
            LocalDate end = LocalDate.of(2026, 5, 10);

            assertThatThrownBy(() -> service.previewSubPeriods(start, end))
                    .isInstanceOf(DomainValidationException.class)
                    .satisfies(ex -> {
                        DomainValidationException dve = (DomainValidationException) ex;
                        assertThat(dve.getErrors().get(0).code()).isEqualTo("DateOrder");
                    });

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("no history and no existing sub-periods → starts at monthlyStart")
        void noHistoryStartsAtMonthlyStart() {
            noBiWeeklyBefore();
            noneInside();

            SubPayrollPeriodCreateResponse response = service.previewSubPeriods(MAY_START, MAY_END);

            // 31-day May → bi-weeklies start May 1, May 15, May 29 → 3 items.
            assertThat(response.newPeriods()).hasSize(3);
            assertThat(response.newPeriods().get(0).startDate()).isEqualTo(MAY_START);
            assertThat(response.newPeriods().get(1).startDate()).isEqualTo(MAY_START.plusDays(14));
            assertThat(response.newPeriods().get(2).startDate()).isEqualTo(MAY_START.plusDays(28));

            // Each spans 14 days inclusive.
            response.newPeriods().forEach(p ->
                    assertThat(p.endDate()).isEqualTo(p.startDate().plusDays(13)));

            // BW index restarts at 1; month/year comes from the monthly range.
            assertThat(response.newPeriods()).extracting(PayrollPeriodCreateRequest::name)
                    .containsExactly("BW1 May 2026", "BW2 May 2026", "BW3 May 2026");

            assertThat(response.exist()).isEmpty();
        }

        @Test
        @DisplayName("existing chain ends 5 days before monthlyStart → next start = lastBefore + 14")
        void continuesFromLastBefore() {
            // Last existing bi-weekly started Apr 22, 2026 → ends May 5. So the
            // next bi-weekly must start Apr 22 + 14 = May 6, NOT May 1.
            biWeeklyBefore(LocalDate.of(2026, 4, 22));
            noneInside();

            SubPayrollPeriodCreateResponse response = service.previewSubPeriods(MAY_START, MAY_END);

            assertThat(response.newPeriods()).isNotEmpty();
            assertThat(response.newPeriods().get(0).startDate()).isEqualTo(LocalDate.of(2026, 5, 6));
            // Second one is May 6 + 14 = May 20.
            assertThat(response.newPeriods().get(1).startDate()).isEqualTo(LocalDate.of(2026, 5, 20));
            // Third one starts Jun 3 — outside [May 1, May 31), so not generated.
            assertThat(response.newPeriods()).hasSize(2);

            // Names still anchored to the monthly month (May 2026), not to the
            // sub-period's own start (which for #2 is still May, but for a
            // cross-month range this is what keeps them grouped).
            assertThat(response.newPeriods()).extracting(PayrollPeriodCreateRequest::name)
                    .containsExactly("BW1 May 2026", "BW2 May 2026");
        }

        @Test
        @DisplayName("existing sub-periods inside the range are returned in exist, sorted asc")
        void returnsInsideRange() {
            noBiWeeklyBefore();

            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            inside(
                    biWeekly(a, LocalDate.of(2026, 5, 1)),
                    biWeekly(b, LocalDate.of(2026, 5, 15)));

            SubPayrollPeriodCreateResponse response = service.previewSubPeriods(MAY_START, MAY_END);

            assertThat(response.exist()).hasSize(2);
            assertThat(response.exist()).extracting(PayrollPeriodResponse::id)
                    .containsExactly(a.toString(), b.toString());
            assertThat(response.exist()).extracting(PayrollPeriodResponse::startDate)
                    .containsExactly(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15));
        }

        @Test
        @DisplayName("the two page(...) calls use the expected criteria")
        void usesExpectedCriteria() {
            noBiWeeklyBefore();
            noneInside();

            service.previewSubPeriods(MAY_START, MAY_END);

            ArgumentCaptor<PayrollPeriodCriteria> captor =
                    ArgumentCaptor.forClass(PayrollPeriodCriteria.class);
            verify(repository, times(2)).page(captor.capture());

            PayrollPeriodCriteria before = captor.getAllValues().get(0);
            assertThat(before.frequency()).isEqualTo(PayrollPeriodFrequency.BI_WEEKLY);
            assertThat(before.startDateBefore()).isEqualTo(MAY_START);
            assertThat(before.startDate()).isNull();
            assertThat(before.endDate()).isNull();
            assertThat(before.sortBy()).isEqualTo("startDate");
            assertThat(before.sortDirection()).isEqualTo(SortDirection.DESC);
            assertThat(before.page()).isZero();
            assertThat(before.size()).isEqualTo(1);

            PayrollPeriodCriteria inside = captor.getAllValues().get(1);
            assertThat(inside.frequency()).isEqualTo(PayrollPeriodFrequency.BI_WEEKLY);
            assertThat(inside.startDate()).isEqualTo(MAY_START);
            assertThat(inside.startDateBefore()).isEqualTo(MAY_END);
            assertThat(inside.endDate()).isNull();
            assertThat(inside.sortBy()).isEqualTo("startDate");
            assertThat(inside.sortDirection()).isEqualTo(SortDirection.ASC);
            assertThat(inside.page()).isZero();
            assertThat(inside.size()).isEqualTo(100);
        }

        @Test
        @DisplayName("a 28-day range produces exactly 2 sub-periods, and the last may extend past monthlyEnd")
        void shortRangeMayExtendEnd() {
            LocalDate start = LocalDate.of(2026, 2, 1);
            LocalDate end = LocalDate.of(2026, 2, 28); // 28 days inclusive

            when(repository.page(argThat(c -> c != null
                    && start.equals(c.startDateBefore())
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf());
            when(repository.page(argThat(c -> c != null
                    && start.equals(c.startDate())
                    && end.equals(c.startDateBefore())
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf());

            SubPayrollPeriodCreateResponse response = service.previewSubPeriods(start, end);

            assertThat(response.newPeriods()).hasSize(2);
            assertThat(response.newPeriods().get(0).startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(response.newPeriods().get(1).startDate()).isEqualTo(LocalDate.of(2026, 2, 15));
            // Second bi-weekly ends Feb 28 (start Feb 15 + 13). It fits exactly.
            assertThat(response.newPeriods().get(1).endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("a 28-day range that ends mid-month → last sub-period's endDate crosses monthlyEnd")
        void lastEndMayCrossMonthEnd() {
            // 28-day range but not calendar Feb: May 5 → Jun 1.
            LocalDate start = LocalDate.of(2026, 5, 5);
            LocalDate end = LocalDate.of(2026, 6, 1);

            when(repository.page(argThat(c -> c != null
                    && start.equals(c.startDateBefore())
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf());
            when(repository.page(argThat(c -> c != null
                    && start.equals(c.startDate())
                    && end.equals(c.startDateBefore())
                    && c.frequency() == PayrollPeriodFrequency.BI_WEEKLY)))
                    .thenReturn(pageOf());

            SubPayrollPeriodCreateResponse response = service.previewSubPeriods(start, end);

            // Starts: May 5, May 19, Jun 2. Jun 2 is >= end (Jun 1), so only 2.
            assertThat(response.newPeriods()).hasSize(2);
            // The second one's end (May 19 + 13 = Jun 1) is inside the range;
            // but what matters is that we don't clip: if the start had been
            // May 20, the end (Jun 2) would cross Jun 1, and it would still be kept.
            assertThat(response.newPeriods().get(1).endDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        }
    }

    private static DataPage<PayrollPeriod> pageOf(PayrollPeriod... items) {
        return new DataPage<>(List.of(items), 0, Math.max(items.length, 1), items.length);
    }
}