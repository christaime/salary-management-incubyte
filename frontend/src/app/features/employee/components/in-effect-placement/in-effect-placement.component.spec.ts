import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { InEffectPlacementComponent } from './in-effect-placement.component';
import {
  InEffectPlacementForm,
  createInEffectPlacementForm,
} from './in-effect-placement.component';
import { AssignmentEditorFormComponent } from '../assignment-editor-form/assignment-editor-form.component';
import { AssignmentPillComponent } from '../assignment-pill/assignment-pill.component';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import { EmployeeAssignmentDto } from '../../models';
import { FormGroup } from '@angular/forms';

const emptyPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: true,
};

function mockPage(content: any[]) {
  return { ...emptyPage, content, totalElements: content.length };
}

function makeAssignment(overrides: Partial<EmployeeAssignmentDto> = {}): EmployeeAssignmentDto {
  return {
    id: 'a-1',
    startDate: '2021-04-01',
    endDate: null,
    country: { id: 'c-us', code: 'US', name: 'United States' },
    department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
    role: { id: 'r-cto', code: 'CTO', name: 'CTO' },
    employmentType: 'FULL_TIME',
    payrollFrequency: 'MONTHLY',
    hasPayroll: false,
    ...overrides,
  };
}

describe('InEffectPlacementComponent', () => {
  let fixture: ComponentFixture<InEffectPlacementComponent>;
  let component: InEffectPlacementComponent;
  let form: FormGroup<InEffectPlacementForm>;

  function setup(
    options: {
      currentAssignment?: EmployeeAssignmentDto | null;
      hasPayroll?: boolean;
      disabled?: boolean;
    } = {},
  ): void {
    const countrySpy = jasmine.createSpyObj('CountryService', ['search']);
    countrySpy.search.and.returnValue(of(mockPage([])));
    const deptSpy = jasmine.createSpyObj('DepartmentService', ['search']);
    deptSpy.search.and.returnValue(of(mockPage([])));
    const roleSpy = jasmine.createSpyObj('RoleService', ['search']);
    roleSpy.search.and.returnValue(of(mockPage([])));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [InEffectPlacementComponent, NoopAnimationsModule],
      providers: [
        { provide: CountryService, useValue: countrySpy },
        { provide: DepartmentService, useValue: deptSpy },
        { provide: RoleService, useValue: roleSpy },
      ],
    });

    fixture = TestBed.createComponent(InEffectPlacementComponent);
    component = fixture.componentInstance;
    form = createInEffectPlacementForm();
    fixture.componentRef.setInput('formGroup', form);
    fixture.componentRef.setInput('currentAssignment', options.currentAssignment ?? null);
    fixture.componentRef.setInput('hasPayroll', options.hasPayroll ?? false);
    fixture.componentRef.setInput('disabled', options.disabled ?? false);
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------
  // Case A: create mode or !hasPayroll
  // ------------------------------------------------------------------

  it('shows only the editor form in create mode', () => {
    setup({ hasPayroll: false });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-assignment-editor-form')).not.toBeNull();
    expect(el.querySelector('app-assignment-pill')).toBeNull();
    expect(el.querySelector('[data-testid="new-assignment-block"]')).toBeNull();
  });

  it('shows only the editor form when hasPayroll is false in edit mode', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: false }), hasPayroll: false });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-assignment-editor-form')).not.toBeNull();
    expect(el.querySelector('app-assignment-pill')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Case B: hasPayroll
  // ------------------------------------------------------------------

  it('shows the pill and hides the editor form when hasPayroll is true', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-assignment-pill')).not.toBeNull();
    expect(el.querySelector('app-assignment-editor-form')).toBeNull();
    expect(el.querySelector('[data-testid="new-assignment-block"]')).toBeNull();
  });

  it('does not show the pill when currentAssignment is null even if hasPayroll is true', () => {
    setup({ currentAssignment: null, hasPayroll: true });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-assignment-pill')).toBeNull();
    expect(el.querySelector('app-assignment-editor-form')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // Toggle
  // ------------------------------------------------------------------

  it('starts with newAssignmentActive = false and next disabled', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });
    expect(component.newAssignmentActive()).toBeFalse();
    expect(form.controls.next.disabled).toBeTrue();
  });

  it('enables the new assignment form and shows it on toggle', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });

    component.onToggleNewAssignment();
    fixture.detectChanges();

    expect(component.newAssignmentActive()).toBeTrue();
    expect(form.controls.next.enabled).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="new-assignment-block"]'))
      .not.toBeNull();
  });

  it('disables the new assignment form on toggle-off but preserves its values', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });

    // Turn on, fill in a value, then turn off.
    component.onToggleNewAssignment();
    form.controls.next.patchValue({
      employmentType: 'CONTRACTOR',
      startDate: '2026-01-01',
    });

    component.onToggleNewAssignment();
    fixture.detectChanges();

    expect(component.newAssignmentActive()).toBeFalse();
    expect(form.controls.next.disabled).toBeTrue();
    // Values preserved.
    expect(form.controls.next.getRawValue().employmentType).toBe('CONTRACTOR');
    expect(form.controls.next.getRawValue().startDate).toBe('2026-01-01');
  });

  it('does not toggle when disabled', () => {
    setup({
      currentAssignment: makeAssignment({ hasPayroll: true }),
      hasPayroll: true,
      disabled: true,
    });

    component.onToggleNewAssignment();
    expect(component.newAssignmentActive()).toBeFalse();
    expect(form.controls.next.disabled).toBeTrue();
  });

  // ------------------------------------------------------------------
  // endDate required while adding
  // ------------------------------------------------------------------

  it('sets endRequiredWhenAdding when next is enabled and current.endDate is empty', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });

    component.onToggleNewAssignment();
    form.updateValueAndValidity();

    expect(form.hasError('endRequiredWhenAdding')).toBeTrue();
  });

  it('clears endRequiredWhenAdding when the user sets current.endDate', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });

    component.onToggleNewAssignment();
    form.controls.current.patchValue({ endDate: '2026-03-31' });
    form.updateValueAndValidity();

    expect(form.hasError('endRequiredWhenAdding')).toBeFalse();
  });

  it('does not require endDate when next is disabled', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });

    form.updateValueAndValidity();
    expect(form.hasError('endRequiredWhenAdding')).toBeFalse();
  });

  it('clears the requirement when the user toggles off', () => {
    setup({ currentAssignment: makeAssignment({ hasPayroll: true }), hasPayroll: true });

    component.onToggleNewAssignment();
    expect(form.hasError('endRequiredWhenAdding')).toBeTrue();

    component.onToggleNewAssignment();
    expect(form.hasError('endRequiredWhenAdding')).toBeFalse();
  });
});
