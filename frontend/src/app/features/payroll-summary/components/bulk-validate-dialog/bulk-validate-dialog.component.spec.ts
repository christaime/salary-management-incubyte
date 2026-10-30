import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import {
  BulkValidateDialogComponent,
  BulkValidateDialogData,
} from './bulk-validate-dialog.component';
import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { ValidateAllResult } from '../../models/payroll-summary-request.model';

describe('BulkValidateDialogComponent', () => {
  let fixture: ComponentFixture<BulkValidateDialogComponent>;
  let component: BulkValidateDialogComponent;
  let service: jasmine.SpyObj<PayrollSummaryService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<BulkValidateDialogComponent, boolean>>;

  function setup(data: Partial<BulkValidateDialogData> = {}): void {
    service = jasmine.createSpyObj('PayrollSummaryService', ['validateAll']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BulkValidateDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: PayrollSummaryService, useValue: service },
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            rangeStart: '2026-01-01',
            rangeEnd: '2026-01-31',
            periodName: 'January 2026',
            ...data,
          } as BulkValidateDialogData,
        },
      ],
    });

    fixture = TestBed.createComponent(BulkValidateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------
  // Confirm phase
  // ------------------------------------------------------------------

  it('starts in the confirm phase and renders the confirm buttons', () => {
    setup();
    expect(component.phase()).toBe('confirm');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="bulk-validate-confirm"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="bulk-validate-cancel"]')).not.toBeNull();
    expect(el.textContent).toContain('January 2026');
  });

  it('renders the filter chips when provided', () => {
    setup({ filterChips: ['Country: France', 'Type: Full-time'] });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Country: France');
    expect(el.textContent).toContain('Type: Full-time');
  });

  it('does not call the service until Confirm is clicked', () => {
    setup();
    expect(service.validateAll).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------
  // Cancel before running
  // ------------------------------------------------------------------

  it('closes with false when Cancel is clicked before running', () => {
    setup();
    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });

  // ------------------------------------------------------------------
  // Running phase
  // ------------------------------------------------------------------

  it('calls the service with the dialog data range on Confirm', () => {
    setup();
    service.validateAll.and.returnValue(
      of({ total: 0, validated: 0, failed: 0, failures: [] }),
    );

    component.onConfirm();

    expect(service.validateAll).toHaveBeenCalledWith(
      jasmine.objectContaining({
        rangeStart: '2026-01-01',
        rangeEnd: '2026-01-31',
      }),
    );
  });

  it('forwards every filter from the dialog data to validateAll', () => {
    setup({
      rangeStart: '2026-03-01',
      rangeEnd: '2026-03-31',
      anyCountryIds: ['c-fr', 'c-de'],
      anyDepartmentIds: ['d-eng'],
      anyRoleIds: ['r-swe'],
      anyEmploymentTypes: ['FULL_TIME'],
      payrollFrequency: 'MONTHLY',
    });
    service.validateAll.and.returnValue(
      of({ total: 0, validated: 0, failed: 0, failures: [] }),
    );

    component.onConfirm();

    expect(service.validateAll).toHaveBeenCalledWith({
      rangeStart: '2026-03-01',
      rangeEnd: '2026-03-31',
      anyCountryIds: ['c-fr', 'c-de'],
      anyDepartmentIds: ['d-eng'],
      anyRoleIds: ['r-swe'],
      anyEmploymentTypes: ['FULL_TIME'],
      payrollFrequency: 'MONTHLY',
    });
  });

  // ------------------------------------------------------------------
  // Done phase — success
  // ------------------------------------------------------------------

  it('renders the result counts when the call succeeds', () => {
    setup();
    service.validateAll.and.returnValue(
      of({ total: 15, validated: 15, failed: 0, failures: [] }),
    );

    component.onConfirm();
    fixture.detectChanges();

    expect(component.phase()).toBe('done');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="bulk-result-total"]')?.getAttribute('data-value'))
      .toBe('15');
    expect(el.querySelector('[data-testid="bulk-result-validated"]')?.getAttribute('data-value'))
      .toBe('15');
    expect(el.querySelector('[data-testid="bulk-result-failed"]')?.getAttribute('data-value'))
      .toBe('0');
    expect(el.textContent).toContain('All drafts were validated successfully');
  });

  // ------------------------------------------------------------------
  // Done phase — partial failures
  // ------------------------------------------------------------------

  it('renders the failure table when some rows fail', () => {
    setup();
    service.validateAll.and.returnValue(
      of({
        total: 15,
        validated: 13,
        failed: 2,
        failures: [
          { summaryId: 's-a', code: 'ASSIGNMENT_NOT_IN_EFFECT', message: 'Not in effect' },
          { summaryId: 's-b', code: 'PERIOD_CLOSED', message: 'Period closed' },
        ],
      }),
    );

    component.onConfirm();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="bulk-result-failed"]')?.getAttribute('data-value'))
      .toBe('2');

    expect(el.querySelector('[data-testid="bulk-failure-s-a"]')?.textContent)
      .toContain('ASSIGNMENT_NOT_IN_EFFECT');
    expect(el.querySelector('[data-testid="bulk-failure-s-a"]')?.textContent)
      .toContain('Not in effect');

    expect(el.querySelector('[data-testid="bulk-failure-s-b"]')?.textContent)
      .toContain('PERIOD_CLOSED');

    expect(el.querySelector('[data-testid="bulk-validate-close"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="bulk-validate-again"]')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // Error phase
  // ------------------------------------------------------------------

  it('shows an error phase when the call fails', () => {
    setup();
    service.validateAll.and.returnValue(
      throwError(() => ({ message: 'Server error' })),
    );

    component.onConfirm();
    fixture.detectChanges();

    expect(component.phase()).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('Server error');
  });

  it('can retry after an error', () => {
    setup();
    service.validateAll.and.returnValues(
      throwError(() => ({ message: 'Server error' })),
      of({ total: 5, validated: 5, failed: 0, failures: [] }),
    );

    component.onConfirm();
    fixture.detectChanges();
    expect(component.phase()).toBe('error');

    component.onConfirm();
    fixture.detectChanges();
    expect(component.phase()).toBe('done');
  });

  // ------------------------------------------------------------------
  // Close after done
  // ------------------------------------------------------------------

  it('closes with true when Close is clicked after a successful run', () => {
    setup();
    service.validateAll.and.returnValue(
      of({ total: 5, validated: 5, failed: 0, failures: [] }),
    );

    component.onConfirm();
    component.onCancel();

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });
});
