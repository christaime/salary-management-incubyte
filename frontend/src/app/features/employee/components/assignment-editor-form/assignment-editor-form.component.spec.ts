import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { AssignmentEditorFormComponent } from './assignment-editor-form.component';
import {
  AssignmentEditorForm,
  createAssignmentEditorForm,
} from './assignment-form.model';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
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

describe('AssignmentEditorFormComponent', () => {
  let fixture: ComponentFixture<AssignmentEditorFormComponent>;
  let component: AssignmentEditorFormComponent;
  let form: FormGroup<AssignmentEditorForm>;

  function setup(
    options: {
      readOnly?: boolean;
      heading?: string | null;
    } = {},
  ): void {
    const countrySpy = jasmine.createSpyObj('CountryService', ['search']);
    countrySpy.search.and.returnValue(
      of(mockPage([{ id: 'c-fr', code: 'FR', name: 'France' }])),
    );
    const deptSpy = jasmine.createSpyObj('DepartmentService', ['search']);
    deptSpy.search.and.returnValue(
      of(mockPage([{ id: 'd-eng', code: 'ENG', name: 'Engineering' }])),
    );
    const roleSpy = jasmine.createSpyObj('RoleService', ['search']);
    roleSpy.search.and.returnValue(
      of(mockPage([{ id: 'r-cto', code: 'CTO', name: 'CTO' }])),
    );

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AssignmentEditorFormComponent, NoopAnimationsModule],
      providers: [
        { provide: CountryService, useValue: countrySpy },
        { provide: DepartmentService, useValue: deptSpy },
        { provide: RoleService, useValue: roleSpy },
      ],
    });

    fixture = TestBed.createComponent(AssignmentEditorFormComponent);
    component = fixture.componentInstance;
    form = createAssignmentEditorForm();
    fixture.componentRef.setInput('formGroup', form);
    fixture.componentRef.setInput('readOnly', options.readOnly ?? false);
    fixture.componentRef.setInput('heading', options.heading ?? null);
    fixture.detectChanges();
  }

  it('loads countries, departments, and roles', () => {
    setup();
    expect(component.countries().length).toBe(1);
    expect(component.departments().length).toBe(1);
    expect(component.roles().length).toBe(1);
  });

  it('renders all seven controls', () => {
    setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="placement-employment-type"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="placement-department"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="placement-role"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="placement-jurisdiction"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="placement-frequency"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="placement-start-date"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="placement-end-date"]')).not.toBeNull();
  });

  it('renders the heading when provided', () => {
    setup({ heading: 'New assignment' });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('New assignment');
  });

  it('omits the heading when null', () => {
    setup({ heading: null });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h3')).toBeNull();
  });

  it('shows the endBeforeStart error when endDate <= startDate', () => {
    setup();
    form.patchValue({ startDate: '2026-06-01', endDate: '2026-05-01' });
    form.updateValueAndValidity();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="placement-end-before-start-error"]')).not.toBeNull();
  });

  it('hides the endBeforeStart error when endDate > startDate', () => {
    setup();
    form.patchValue({ startDate: '2026-01-01', endDate: '2026-12-31' });
    form.updateValueAndValidity();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="placement-end-before-start-error"]')).toBeNull();
  });

  it('hides the endBeforeStart error when endDate is null', () => {
    setup();
    form.patchValue({ startDate: '2026-01-01', endDate: null });
    form.updateValueAndValidity();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="placement-end-before-start-error"]')).toBeNull();
  });
});
