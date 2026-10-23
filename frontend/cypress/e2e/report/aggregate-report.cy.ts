describe('Aggregate Report Page', () => {
  beforeEach(() => {
    // 1. Setup Reference Data Intercepts
    cy.intercept('GET', '**/api/v1/countries*', { fixture: 'report/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*', { fixture: 'report/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*', { fixture: 'report/roles.json' }).as('getRoles');
    cy.intercept('GET', '**/api/v1/payroll-periods*', { fixture: 'report/payroll-periods.json' }).as('getPeriods');

    // 2. Visit the page
    cy.visit('/reports/aggregate');

    // 3. Wait for the initial reference data load to finish before starting the test
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');
  });

  // Helper function to select a period from a specific picker
  const selectPeriod = (pickerType: 'start' | 'end', periodId: string) => {
    // 1. Scope to the specific picker
    cy.get(`[data-testid="${pickerType}-period"]`)
      .should('be.visible')
      // 2. Find the year trigger inside this picker
      .find('[data-testid="period-picker-year-trigger"]')
      .should('be.visible')
      .click();

    // 3. Select 2026 (popover renders globally in an overlay)
    cy.get('[data-testid="period-picker-year-2026"]')
      .should('be.visible')
      .click();

    // 4. Wait for the period list to load (triggered by year selection)
    cy.wait('@getPeriods');

    // 5. Click the specific period result
    cy.get(`[data-testid="period-picker-result-${periodId}"]`)
      .should('be.visible')
      .click();
  };

  /**
   * CASE 1: Valid Period, All 5 Toggles Render Correctly
   */
  it('should generate a report and render all 5 toggle views correctly', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', (req) => {
      const groupKeys = req.body.groupKeys;

      if (groupKeys.length === 0) {
        req.reply({ fixture: 'report/report-global.json' });
      } else if (groupKeys.includes('COUNTRY')) {
        req.reply({ fixture: 'report/report-by-country.json' });
      } else {
        req.reply({
          kind: 'AGGREGATE', groupKeys: groupKeys, referenceCurrency: 'USD', lines: [], warnings: []
        });
      }
    }).as('generateReport');

    selectPeriod('start', 'p-jan');

    cy.get('[data-testid="btn-generate"]')
      .should('be.visible')
      .click();

    cy.wait('@generateReport');

    // --- Assert Total View (Default) ---
    cy.get('[data-testid="aggregate-results-total-amount"]')
      .should('be.visible')
      .and('have.attr', 'data-value', '150000.5');
    cy.get('[data-testid="aggregate-results-total-employees"]')
      .should('be.visible')
      .and('have.attr', 'data-value', '42');

    // --- Assert Per Country View ---
    cy.get('[data-testid="toggle-btn-COUNTRY"]')
      .should('be.visible')
      .and('not.have.class', 'mat-button-toggle-disabled')
      .click();

    cy.wait('@generateReport');

    cy.get('[data-testid="aggregate-results-title"]')
      .should('be.visible')
      .and('contain', 'Payroll Cost per Country');

    cy.get('[data-testid="tiered-bar-row-CM"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-CM"]').should('have.attr', 'data-value', '100000');

    cy.get('[data-testid="tiered-bar-row-FR"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-FR"]').should('have.attr', 'data-value', '50000.5');

    // Click a row to open the donut
    cy.get('[data-testid="tiered-bar-row-CM"]').click();
    cy.get('[data-testid="donut-title"]').should('contain', 'Cameroon');
    cy.get('[data-testid="donut-total-amount"]').should('have.attr', 'data-value', '100000');
  });

  /**
   * CASE 2: HTTP 200 with Warnings (MISSING_PAYROLL_SUMMARY)
   */
  it('should show the warning banner for MISSING_PAYROLL_SUMMARY (HTTP 200)', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 200,
      fixture: 'report/report-missing-payroll-summary.json'
    }).as('generateWarningReport');

    selectPeriod('start', 'p-jan');

    cy.get('[data-testid="btn-generate"]')
      .should('be.visible')
      .click();

    cy.wait('@generateWarningReport');

    cy.get('[data-testid="warning-banner"]').should('be.visible').and('contain', 'Data Quality Warnings');
    cy.get('[data-testid="blocker-banner"]').should('not.exist');
    cy.get('[data-testid="warning-banner"]').should('contain', 'Some employees are missing a payroll summary.');
    cy.get('[data-testid="warn-count-MISSING_PAYROLL_SUMMARY"]').should('be.visible').and('have.attr', 'data-value', '2');
  });

  /**
   * CASE 2.1: HTTP 400 (Blockers) - MISSING_FX_RATE
   */
  it('should show a blocker for MISSING_FX_RATE (HTTP 400)', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 400,
      fixture: 'report/report-missing-fx-rate.json'
    }).as('generateBlockedReport');

    selectPeriod('start', 'p-bad');

    cy.get('[data-testid="btn-generate"]')
      .should('be.visible')
      .click();

    cy.wait('@generateBlockedReport');

    cy.get('[data-testid="blocker-banner"]').should('be.visible').and('contain', 'Report Attention Required');
    cy.get('[data-testid="warning-banner"]').should('not.exist');
    cy.get('[data-testid="blocker-message-MISSING_FX_RATE"]').should('be.visible').and('contain', 'Missing FX rate');
    cy.get('[data-testid="blocker-banner"]').find('.font-mono.font-bold').should('contain', 'XOF');
  });

  /**
   * CASE 2.2: HTTP 400 (Blockers) - MISSING_REFERENCE_CURRENCY
   */
  it('should show a blocker for MISSING_REFERENCE_CURRENCY (HTTP 400)', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 400,
      fixture: 'report/report-missing-ref-currency.json'
    }).as('generateBlockedReport');

    selectPeriod('start', 'p-bad');

    cy.get('[data-testid="btn-generate"]')
      .should('be.visible')
      .click();

    cy.wait('@generateBlockedReport');

    cy.get('[data-testid="blocker-banner"]').should('be.visible');
    cy.get('[data-testid="warning-banner"]').should('not.exist');
    cy.get('[data-testid="blocker-message-MISSING_REFERENCE_CURRENCY"]').should('be.visible').and('contain', 'Reference currency is not defined');
  });

  /**
   * CASE 2.3: HTTP 400 (Blockers) - UNVALIDATED_PAYROLL_SUMMARY (Employees)
   */
  it('should show a blocker with employee list for UNVALIDATED_PAYROLL_SUMMARY (HTTP 400)', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 400,
      fixture: 'report/report-unvalidated-payroll.json'
    }).as('generateBlockedReport');

    selectPeriod('start', 'p-bad');

    cy.get('[data-testid="btn-generate"]')
      .should('be.visible')
      .click();

    cy.wait('@generateBlockedReport');

    cy.get('[data-testid="blocker-banner"]').should('be.visible');
    cy.get('[data-testid="warning-banner"]').should('not.exist');
    cy.get('[data-testid="blocker-employee-emp-1"]').should('be.visible').and('contain', 'EMP001');
    cy.get('[data-testid="blocker-more-UNVALIDATED_PAYROLL_SUMMARY"]').should('be.visible').and('contain', 'more not shown');
  });

  /**
   * CASE 3: Multiple Criteria -> Verify HTTP Params
   */
  it('should send coherent HTTP params based on form criteria', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', { fixture: 'report/report-global.json' }).as('generateReport');

    selectPeriod('start', 'p-jan');

    // Open the drawer (Data is already loaded in beforeEach, no need to wait)
    cy.get('[data-testid="btn-customize"]').should('be.visible').click();

    cy.get('[data-testid="filter-countries"]').should('be.visible').click();
    cy.get('mat-option').contains('Cameroon').should('be.visible').click();
    cy.get('mat-option').contains('France').should('be.visible').click();
    cy.get('.cdk-overlay-backdrop').click({ force: true });

    cy.get('[data-testid="filter-departments"]').should('be.visible').click();
    cy.get('mat-option').contains('Engineering').should('be.visible').click();
    cy.get('.cdk-overlay-backdrop').click({ force: true });

    cy.get('[data-testid="btn-drawer-apply"]').should('be.visible').click();

    cy.get('[data-testid="btn-generate"]').should('be.visible').click();

    cy.wait('@generateReport').then((interception) => {
      const body = interception.request.body;
      expect(body.dateRange.startDate).to.eq('2026-01-01');
      expect(body.dateRange.endDate).to.eq('2026-01-31');
      expect(body.groupKeys).to.deep.eq([]);
      expect(body.anyCountryIds).to.deep.eq(['cm', 'fr']);
      expect(body.anyDepartmentIds).to.deep.eq(['eng']);
      expect(body.anyRoleIds).to.be.null;
      expect(body.anyEmploymentTypes).to.be.null;
    });

    cy.get('[data-testid="toggle-btn-COUNTRY"]').should('be.visible').click();
    cy.wait('@generateReport').then((interception) => {
      const body = interception.request.body;
      expect(body.groupKeys).to.deep.eq(['COUNTRY']);
    });
  });

  /**
   * CASE 4: Statistics rendering (Bi-Weekly only)
   */
  it('should show only the Bi-Weekly cadence card when monthly stats are null', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 200,
      fixture: 'report/report-stats-biweekly-only.json'
    }).as('generateReport');

    // Select a period (the exact one doesn't matter for this test, but we need a valid one)
    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    // 1. The total amount should be visible (confirms the total view is rendered)
    cy.get('[data-testid="aggregate-results-total-amount"]')
      .should('be.visible')
      .and('have.attr', 'data-value', '55531.31');

    // 2. The Monthly card should NOT be in the DOM
    cy.get('[data-testid="cadence-card-monthly"]').should('not.exist');

    // 3. The Bi-Weekly card SHOULD be in the DOM
    cy.get('[data-testid="cadence-card-biweekly"]').should('be.visible');

    // 4. The Bi-Weekly card should span the full width (the grid col-span-2)
    // We check the class on the Bi-Weekly card's parent div
    cy.get('[data-testid="cadence-card-biweekly"]')
      .parent()
      .should('have.class', 'lg:col-span-2');
  });

  /**
   * CASE 5: Statistics rendering (Monthly only)
   */
  it('should show only the Monthly cadence card when biWeekly stats are null', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 200,
      fixture: 'report/report-stats-monthly-only.json'
    }).as('generateReport');

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    // 1. The Monthly card should be in the DOM
    cy.get('[data-testid="cadence-card-monthly"]').should('be.visible');

    // 2. The Bi-Weekly card should NOT be in the DOM
    cy.get('[data-testid="cadence-card-biweekly"]').should('not.exist');

    // 3. The Monthly card should span the full width
    cy.get('[data-testid="cadence-card-monthly"]')
      .parent()
      .should('have.class', 'lg:col-span-2');
  });
});
