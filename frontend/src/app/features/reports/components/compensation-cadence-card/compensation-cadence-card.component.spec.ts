import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { CompensationCadenceCardComponent } from './compensation-cadence-card.component';
import { StatisticsDto } from '../../models/statistics.model';

describe('CompensationCadenceCardComponent', () => {
  let component: CompensationCadenceCardComponent;
  let fixture: ComponentFixture<CompensationCadenceCardComponent>;

  const mockStatistics: StatisticsDto = {
    monthly: {
      employeeCount: 80,
      periodCount: 12,
      min: { currency: 'USD', amount: 1000 },
      max: { currency: 'USD', amount: 5000 },
      average: { currency: 'USD', amount: 3000 },
      total: { currency: 'USD', amount: 240000 },
    },
    biWeekly: {
      employeeCount: 20,
      periodCount: 26,
      min: { currency: 'USD', amount: 500 },
      max: { currency: 'USD', amount: 2500 },
      average: { currency: 'USD', amount: 1500 },
      total: { currency: 'USD', amount: 30000 },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompensationCadenceCardComponent],
      providers: [provideAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(CompensationCadenceCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('data', mockStatistics);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute total employees and ratios correctly', () => {
    expect(component.totalEmployees()).toBe(100);
    expect(component.monthlyRatio()).toBe(80);
    expect(component.biWeeklyRatio()).toBe(20);
  });

  it('should calculate monthly spread and annualized values', () => {
    expect(component.monthlySpread()).toBe(4000);
    expect(component.monthlyAvgPosition()).toBe(50); // (3000 - 1000) / 4000 = 50%
    expect(component.monthlyAnnualized()).toBe(36000); // 3000 * 12
  });

  it('should calculate bi-weekly equivalent and annualized values', () => {
    expect(component.biWeeklySpread()).toBe(2000);
    expect(component.biWeeklyAvgPosition()).toBe(50); // (1500 - 500) / 2000 = 50%
    expect(component.biWeeklyAnnualized()).toBe(39000); // 1500 * 26
    expect(component.biWeeklyMonthlyEquivalent()).toBe(3250); // (1500 * 26) / 12
  });
});
