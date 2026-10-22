import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ReportKindToggleComponent } from './report-kind-toggle.component';

describe('ReportKindToggleComponent', () => {
  let fixture: ComponentFixture<ReportKindToggleComponent>;
  let component: ReportKindToggleComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportKindToggleComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportKindToggleComponent);
    fixture.componentRef.setInput('selected', 'TOTAL');
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('emits kindChange when selection changes', () => {
    let emitted: unknown;
    component.kindChange.subscribe((k) => (emitted = k));
    component.onSelectionChange('COUNTRY');
    expect(emitted).toBe('COUNTRY');
  });

  it('renders 5 toggle buttons', () => {
    const el = fixture.nativeElement as HTMLElement;
    const buttons = el.querySelectorAll('mat-button-toggle');
    expect(buttons.length).toBe(5);
  });
});
