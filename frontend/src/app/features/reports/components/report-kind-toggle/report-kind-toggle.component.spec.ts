import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ReportKindToggleComponent } from './report-kind-toggle.component';

describe('ReportKindToggleComponent', () => {
  let component: ReportKindToggleComponent;
  let fixture: ComponentFixture<ReportKindToggleComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportKindToggleComponent],
      providers: [provideAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportKindToggleComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('selected', 'TOTAL');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit kindChange when selection changes', () => {
    spyOn(component.kindChange, 'emit');
    component.onSelectionChange('COUNTRY');
    expect(component.kindChange.emit).toHaveBeenCalledWith('COUNTRY');
  });
});
