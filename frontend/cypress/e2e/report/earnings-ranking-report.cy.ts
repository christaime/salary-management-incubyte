describe('Earnings Ranking Report Page', () => {
  beforeEach(() => {
    // Reference data — reuse the shared aggregate-report fixtures
    cy.intercept('GET', '**/api/v1/countries*',        { fixture: 'report/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*',      { fixture: 'report/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*',            { fixture: 'report/roles.json' }).as('getRoles');
    cy.intercept('GET', '**/api/v1/payroll-periods*',  { fixture: 'report/payroll-periods.json' }).as('getPeriods');

    cy.visit('/reports/earnings-ranking');

    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');
  });

  // Helper — pick the period from the single picker used by the ranking form
  const selectPeriod = (periodId: string) => {
    cy.get('[data-testid="period-picker"]')
      .find('[data-testid="period-picker-year-trigger"]')
      .click();

    cy.get('[data-testid="period-picker-year-2026"]').click();
    cy.wait('@getPeriods');

    cy.get(`[data-testid="period-picker-result-${periodId}"]`).click();
  };

  // Helper — pick the group-by from the groupKey mat-select
  const selectGroupBy = (label: string) => {
    cy.get('[data-testid="group-key"]').click();
    cy.get('mat-option').contains(label).click();
  };

  // ------------------------------------------------------------------
  // CASE 1 — Data error → blocker banner, no report
  // ------------------------------------------------------------------

  it('should render the blocker banner when the backend rejects the report', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', {
      statusCode: 400,
      body: {
        code: 'REPORT_BLOCKED',
        message: 'Report could not be generated',
        errors: [
          {
            code: 'MISSING_FX_RATE',
            message: 'Missing FX rate for XAF',
            severity: 'BLOCKER',
            details: { currency: 'XAF' },
          },
        ],
        timestamp: '2026-10-26T10:00:00Z',
      },
    }).as('generateBlocked');

    selectPeriod('p-bad');

    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateBlocked');

    cy.get('[data-testid="blocker-banner-wrapper"]').should('be.visible');
    cy.get('[data-testid="blocker-banner"]')
      .should('contain', 'Report Attention Required')
      .and('contain', 'Missing FX rate for XAF');

    cy.get('[data-testid="blocker-message-MISSING_FX_RATE"]').should('be.visible');

    cy.get('app-position-context').should('not.exist');
    cy.get('app-earner-spectrum').should('not.exist');
  });

  // ------------------------------------------------------------------
  // CASE 2 — Correct data → full report renders, values match the fixture
  // ------------------------------------------------------------------

  it('should render the full report when the backend returns valid data', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', {
      fixture: 'report/ranking-report-success.json',
    }).as('generateReport');

    selectPeriod('p-jan');
    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport').then(({ response }) => {
      const body = response!.body;

      // Metadata strip
      cy.contains(body.period.name).should('be.visible');
      cy.contains(body.referenceCurrency).should('be.visible');
      cy.contains('GLOBAL').should('be.visible');
      cy.contains(`${body.positionContext.employeeCount} employees in scope`)
        .should('be.visible');

      // Position context — 7 markers
      cy.get('[data-testid^="marker-P"]')
        .filter((_, el) =>
          /^marker-P\d+$/.test(el.getAttribute('data-testid') ?? ''),
        )
        .should('have.length', 7);

      // P50 highlighted
      cy.get('[data-testid="marker-P50"]')
        .should('have.attr', 'data-highlighted', 'true');

      // Top 5 rows
      cy.get('[data-testid="top-row"]').should('have.length', body.tops.length);

      const top = body.tops[0];
      cy.get(`[data-testid="top-name-${top.employee.id}"]`)
        .should('contain', top.employee.name);
      // 25,624.98 × 12 = 307,499.76 → rendered as $307,500 (0 decimals)
      cy.get(`[data-testid="top-annual-${top.employee.id}"]`)
        .should('contain', '$307,500');
      cy.get(`[data-testid="top-monthly-${top.employee.id}"]`)
        .should('contain', '$25,624.98');
      cy.get(`[data-testid="top-percentile-${top.employee.id}"]`)
        .should('contain', 'P100.00');

      // Bottom 5 rows
      cy.get('[data-testid="bottom-row"]').should('have.length', body.bottoms.length);

      const bottom = body.bottoms[0];
      cy.get(`[data-testid="bottom-name-${bottom.employee.id}"]`)
        .should('contain', bottom.employee.name);
      cy.get(`[data-testid="bottom-percentile-${bottom.employee.id}"]`)
        .should('contain', 'P0.00');
    });
  });

  // ------------------------------------------------------------------
  // CASE 3 — Success with warnings → warning banner + full report
  // ------------------------------------------------------------------

  it('should render the warning banner when the backend returns warnings with 200', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', {
      fixture: 'report/ranking-report-warning.json',
    }).as('generateWarning');

    selectPeriod('p-jan');
    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateWarning');

    cy.get('[data-testid="warning-banner-wrapper"]').should('be.visible');
    cy.get('[data-testid="warning-banner"]')
      .should('contain', 'Data Quality Warnings')
      .and('contain', '3 employees with active assignments have no payroll summary');

    // Report body still renders
    cy.get('app-position-context').should('exist');
    cy.get('app-earner-spectrum').should('exist');
  });

  // ------------------------------------------------------------------
  // CASE 4 — Ranking per Country
  // ------------------------------------------------------------------

  it('should generate a ranking report filtered by country', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', (req) => {
      expect(req.body.groupKey).to.eq('COUNTRY');
      expect(req.body.countryId).to.eq('cm');
      expect(req.body.departmentId).to.eq(null);
      expect(req.body.roleId).to.eq(null);
      expect(req.body.employmentType).to.eq(null);
      req.reply({ fixture: 'report/ranking-report-success.json' });
    }).as('generateReport');

    selectPeriod('p-jan');
    selectGroupBy('Per Country');

    cy.get('[data-testid="dimension-country"]').click();
    cy.get('mat-option').contains('Cameroon').click();

    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport');

    cy.get('app-position-context').should('exist');
    cy.get('app-earner-spectrum').should('exist');
  });

  // ------------------------------------------------------------------
  // CASE 5 — Ranking per Department
  // ------------------------------------------------------------------

  it('should generate a ranking report filtered by department', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', (req) => {
      expect(req.body.groupKey).to.eq('DEPARTMENT');
      expect(req.body.departmentId).to.eq('eng');
      expect(req.body.countryId).to.eq(null);
      expect(req.body.roleId).to.eq(null);
      expect(req.body.employmentType).to.eq(null);
      req.reply({ fixture: 'report/ranking-report-success.json' });
    }).as('generateReport');

    selectPeriod('p-jan');
    selectGroupBy('Per Department');

    cy.get('[data-testid="dimension-department"]').click();
    cy.get('mat-option').contains('Engineering').click();

    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport');

    cy.get('app-earner-spectrum').should('exist');
  });

  // ------------------------------------------------------------------
  // CASE 6 — Ranking per Role
  // ------------------------------------------------------------------

  it('should generate a ranking report filtered by role', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', (req) => {
      expect(req.body.groupKey).to.eq('ROLE');
      expect(req.body.roleId).to.eq('dev');
      expect(req.body.countryId).to.eq(null);
      expect(req.body.departmentId).to.eq(null);
      expect(req.body.employmentType).to.eq(null);
      req.reply({ fixture: 'report/ranking-report-success.json' });
    }).as('generateReport');

    selectPeriod('p-jan');
    selectGroupBy('Per Role');

    cy.get('[data-testid="dimension-role"]').click();
    cy.get('mat-option').contains('Developer').click();

    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport');

    cy.get('app-earner-spectrum').should('exist');
  });

  // ------------------------------------------------------------------
  // CASE 7 — Ranking per Employment Type
  // ------------------------------------------------------------------

  it('should generate a ranking report filtered by employment type', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', (req) => {
      expect(req.body.groupKey).to.eq('EMPLOYMENT_TYPE');
      expect(req.body.employmentType).to.eq('FULL_TIME');
      expect(req.body.countryId).to.eq(null);
      expect(req.body.departmentId).to.eq(null);
      expect(req.body.roleId).to.eq(null);
      req.reply({ fixture: 'report/ranking-report-success.json' });
    }).as('generateReport');

    selectPeriod('p-jan');
    selectGroupBy('Per Employment Type');

    cy.get('[data-testid="dimension-employment-type"]').click();
    cy.get('mat-option').contains('Full time').click();

    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport');

    cy.get('app-earner-spectrum').should('exist');
  });

  // ------------------------------------------------------------------
  // CASE 9 — Reset
  // ------------------------------------------------------------------

  it('should reset the page to the empty state on Reset', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', {
      fixture: 'report/ranking-report-success.json',
    }).as('generateReport');

    selectPeriod('p-jan');
    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport');

    cy.get('app-earner-spectrum').should('exist');

    cy.get('[data-testid="btn-reset"]').click();

    cy.get('app-earner-spectrum').should('not.exist');
    cy.get('app-position-context').should('not.exist');
    cy.contains('Pick a payroll period').should('be.visible');
  });

  it('should render the "no earners in scope" message when the backend returns no lines', () => {
    cy.intercept('POST', '**/api/v1/reports/rankings', {
      fixture: 'report/ranking-report-empty.json',
    }).as('generateEmpty');

    selectPeriod('p-jan');
    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateEmpty');

    cy.get('[data-testid="ranking-empty-state"]')
      .should('be.visible')
      .and('contain', 'No earners in scope')
      .and('contain', 'EMPLOYMENT_TYPE');

    // The full report must not render
    cy.get('app-position-context').should('not.exist');
    cy.get('app-earner-spectrum').should('not.exist');
  });
});
