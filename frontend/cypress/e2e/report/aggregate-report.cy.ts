describe('Aggregate Report Page', () => {
  beforeEach(() => {
    // 1. Setup Reference Data Intercepts
    cy.intercept('GET', '**/api/v1/countries*', { fixture: 'report/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*', { fixture: 'report/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*', { fixture: 'report/roles.json' }).as('getRoles');
    cy.intercept('GET', '**/api/v1/payroll-periods*', { fixture: 'report/payroll-periods.json' }).as('getPeriods');

    // 2. Visit the page
    cy.visit('/reports/aggregate');

    // 3. Wait for the initial reference data load to finish
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');
  });

  // Helper function to select a period from a specific picker
  const selectPeriod = (pickerType: 'start' | 'end', periodId: string) => {
    cy.get(`[data-testid="${pickerType}-period"]`)
      .should('be.visible')
      .find('[data-testid="period-picker-year-trigger"]')
      .should('be.visible')
      .click();

    cy.get('[data-testid="period-picker-year-2026"]')
      .should('be.visible')
      .click();

    cy.wait('@getPeriods');

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
  it('should send coherent HTTP params for the initial generate', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      fixture: 'report/report-global.json',
    }).as('generateReport');

    selectPeriod('start', 'p-jan');

    cy.get('[data-testid="btn-customize"]').click();

    // Countries — pick two, then close via ESC
    cy.get('[data-testid="filter-countries"]').click();
    cy.get('mat-option').contains('Cameroon').click();
    cy.get('mat-option').contains('France').click();
    cy.get('[data-testid="filter-countries"]').type('{esc}');

    // Departments — pick one, then close via ESC
    cy.get('[data-testid="btn-customize"]').click();
    cy.get('[data-testid="filter-departments"]').should('be.visible');
    cy.get('[data-testid="filter-departments"]').click();
    cy.get('mat-option').contains('Engineering').click();

    cy.get('[data-testid="btn-drawer-apply"]').click();
    cy.get('[data-testid="btn-generate"]').click();

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
  });


  it('should send the COUNTRY groupKey when toggling the report kind', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', (req) => {
      if (req.body.groupKeys.includes('COUNTRY')) {
        req.reply({ fixture: 'report/report-by-country.json' });
      } else {
        req.reply({ fixture: 'report/report-global.json' });
      }
    }).as('generateReport');

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="toggle-btn-COUNTRY"]').click();
    cy.wait('@generateReport').then((interception) => {
      expect(interception.request.body.groupKeys).to.deep.eq(['COUNTRY']);
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

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="aggregate-results-total-amount"]')
      .should('be.visible')
      .and('have.attr', 'data-value', '55531.31');

    cy.get('[data-testid="cadence-card-monthly"]').should('not.exist');
    cy.get('[data-testid="cadence-card-biweekly"]')
      .should('be.visible')
      .and('have.class', 'lg:col-span-2');

    cy.get('[data-testid="cadence-biweekly-total"]').should('have.attr', 'data-value', '55531.31');
    cy.get('[data-testid="cadence-biweekly-min"]').should('have.attr', 'data-value', '1083.91');
    cy.get('[data-testid="cadence-biweekly-max"]').should('have.attr', 'data-value', '5710.43');
    cy.get('[data-testid="cadence-biweekly-avg"]').should('have.attr', 'data-value', '2644.35');
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

    cy.get('[data-testid="cadence-card-monthly"]')
      .should('be.visible')
      .and('have.class', 'lg:col-span-2');

    cy.get('[data-testid="cadence-card-biweekly"]').should('not.exist');

    cy.get('[data-testid="cadence-monthly-total"]').should('have.attr', 'data-value', '150000.5');
    cy.get('[data-testid="cadence-monthly-min"]').should('have.attr', 'data-value', '1000');
    cy.get('[data-testid="cadence-monthly-max"]').should('have.attr', 'data-value', '9000');
    cy.get('[data-testid="cadence-monthly-avg"]').should('have.attr', 'data-value', '3571');
  });

  /**
   * CASE 6: Statistics rendering (Both Monthly and Bi-Weekly exist)
   */
  it('should show both cadence cards side-by-side when both stats exist', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', {
      statusCode: 200,
      fixture: 'report/report-stats-both.json'
    }).as('generateReport');

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="cadence-card-monthly"]').should('be.visible');
    cy.get('[data-testid="cadence-card-biweekly"]').should('be.visible');

    cy.get('[data-testid="cadence-card-monthly"]').should('not.have.class', 'lg:col-span-2');
    cy.get('[data-testid="cadence-card-biweekly"]').should('not.have.class', 'lg:col-span-2');

    cy.get('[data-testid="cadence-monthly-total"]').should('have.attr', 'data-value', '150000.5');
    cy.get('[data-testid="cadence-monthly-avg"]').should('have.attr', 'data-value', '3571');

    cy.get('[data-testid="cadence-biweekly-total"]').should('have.attr', 'data-value', '55531.31');
    cy.get('[data-testid="cadence-biweekly-avg"]').should('have.attr', 'data-value', '2644.35');
  });

  /**
   * CASE 7: Department Report + Donut Coherence
   */
  it('should render the department report with accurate data and a coherent donut', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', (req) => {
      if (req.body.groupKeys.includes('DEPARTMENT')) {
        req.reply({ fixture: 'report/report-by-department.json' });
      } else {
        req.reply({ fixture: 'report/report-global.json' });
      }
    }).as('generateReport');

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="toggle-btn-DEPARTMENT"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="aggregate-results-title"]').should('contain', 'Payroll Cost per Department');

    // Check accurate data in Tiered Bar List
    cy.get('[data-testid="tiered-bar-row-ADVERTIS"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-ADVERTIS"]').should('have.attr', 'data-value', '379187.04');
    cy.get('[data-testid="tiered-bar-row-employees-ADVERTIS"]').should('have.attr', 'data-value', '143');

    cy.get('[data-testid="tiered-bar-row-CONTENT"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-CONTENT"]').should('have.attr', 'data-value', '378328.01');
    cy.get('[data-testid="tiered-bar-row-employees-CONTENT"]').should('have.attr', 'data-value', '142');

    // Check Donut Coherence
    cy.get('[data-testid="tiered-bar-row-ADVERTIS"]').click();

    cy.get('[data-testid="donut-title"]').should('contain', 'Advertising Sales');
    cy.get('[data-testid="donut-total-amount"]').should('have.attr', 'data-value', '379187.04');

    cy.get('[data-testid="donut-val-base"]').should('have.attr', 'data-value', '283257.28');
    cy.get('[data-testid="donut-val-variable"]').should('have.attr', 'data-value', '36230.92');
    cy.get('[data-testid="donut-val-allowances"]').should('have.attr', 'data-value', '17430.81');
    cy.get('[data-testid="donut-val-employerTaxes"]').should('have.attr', 'data-value', '42268.03');
  });

  /**
   * CASE 8: Role Report + Donut Coherence
   */
  it('should render the role report with accurate data and a coherent donut', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', (req) => {
      if (req.body.groupKeys.includes('ROLE')) {
        req.reply({ fixture: 'report/report-by-role.json' });
      } else {
        req.reply({ fixture: 'report/report-global.json' });
      }
    }).as('generateReport');

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="toggle-btn-ROLE"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="aggregate-results-title"]').should('contain', 'Payroll Cost per Role');

    // Check accurate data in Tiered Bar List
    cy.get('[data-testid="tiered-bar-row-A&R"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-A&R"]').should('have.attr', 'data-value', '295021.8');

    cy.get('[data-testid="tiered-bar-row-SALES"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-SALES"]').should('have.attr', 'data-value', '283936.67');

    // Check Donut Coherence
    cy.get('[data-testid="tiered-bar-row-A&R"]').click();

    cy.get('[data-testid="donut-title"]').should('contain', 'A&R Manager');
    cy.get('[data-testid="donut-total-amount"]').should('have.attr', 'data-value', '295021.8');

    cy.get('[data-testid="donut-val-base"]').should('have.attr', 'data-value', '221945.65');
    cy.get('[data-testid="donut-val-variable"]').should('have.attr', 'data-value', '28909.73');
    cy.get('[data-testid="donut-val-allowances"]').should('have.attr', 'data-value', '11041.19');
    cy.get('[data-testid="donut-val-employerTaxes"]').should('have.attr', 'data-value', '33125.23');
  });

  /**
   * CASE 9: Employment Type Report + Donut Coherence
   */
  it('should render the employment type report with accurate data and a coherent donut', () => {
    cy.intercept('POST', '**/api/v1/reports/aggregate', (req) => {
      if (req.body.groupKeys.includes('EMPLOYMENT_TYPE')) {
        req.reply({ fixture: 'report/report-by-employment-type.json' });
      } else {
        req.reply({ fixture: 'report/report-global.json' });
      }
    }).as('generateReport');

    selectPeriod('start', 'p-jan');
    cy.get('[data-testid="btn-generate"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="toggle-btn-EMPLOYMENT_TYPE"]').should('be.visible').click();
    cy.wait('@generateReport');

    cy.get('[data-testid="aggregate-results-title"]').should('contain', 'Payroll Cost per Employment Type');

    // Check accurate data in Tiered Bar List
    cy.get('[data-testid="tiered-bar-row-PART_TIME"]').should('be.visible');
    cy.get('[data-testid="tiered-bar-row-amount-PART_TIME"]').should('have.attr', 'data-value', '3990252.35');
    cy.get('[data-testid="tiered-bar-row-employees-PART_TIME"]').should('have.attr', 'data-value', '1428');

    // Check Donut Coherence
    cy.get('[data-testid="tiered-bar-row-PART_TIME"]').click();

    cy.get('[data-testid="donut-title"]').should('contain', 'Part time');
    cy.get('[data-testid="donut-total-amount"]').should('have.attr', 'data-value', '3990252.35');

    cy.get('[data-testid="donut-val-base"]').should('have.attr', 'data-value', '2989864.59');
    cy.get('[data-testid="donut-val-variable"]').should('have.attr', 'data-value', '370071.16');
    cy.get('[data-testid="donut-val-allowances"]').should('have.attr', 'data-value', '182079.53');
    cy.get('[data-testid="donut-val-employerTaxes"]').should('have.attr', 'data-value', '448237.07');
  });
});
