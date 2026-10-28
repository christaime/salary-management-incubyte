describe('Payroll summary list page', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/countries*',       { fixture: 'payroll-summary/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*',     { fixture: 'payroll-summary/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*',           { fixture: 'payroll-summary/roles.json' }).as('getRoles');
    cy.intercept('GET', '**/api/v1/payroll-periods*', { fixture: 'payroll-summary/payroll-periods.json' }).as('getPeriods');
  });

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------
  function visitList(): void {
    cy.visit('/payroll-summaries');
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');
    cy.wait('@getPeriods');
  }

  function pickYear(picker: 'filter-start-period' | 'filter-end-period', year: number): void {
    cy.intercept('GET', '**/api/v1/payroll-periods*').as('getPeriodsOnYear');

    cy.get(`[data-testid="${picker}"]`)
      .find('[data-testid="period-picker-year-trigger"]')
      .click();

    cy.get(`[data-testid="period-picker-year-${year}"]`)
      .should('be.visible')
      .click();

    cy.wait('@getPeriodsOnYear');
  }

  function closeOpenPicker(picker: 'filter-start-period' | 'filter-end-period'): void {
    // The dropdown panel is rendered via @if — it is removed from the DOM
    // when closed, not merely hidden. Use `period-picker-results-count`
    // (always inside the panel) as the presence marker.
    const panel = cy
      .get(`[data-testid="${picker}"]`)
      .find('[data-testid="period-picker-results-count"]');

    panel.then(($panel) => {
      if ($panel.length === 0) {
        // Already closed — nothing to do.
        return;
      }

      cy.get(`[data-testid="${picker}"]`)
        .find('[data-testid="period-picker-search-input"]')
        .should('be.visible')
        .click();

      // The panel is removed by the @if when isPeriodDropdownOpen() flips false.
      cy.get(`[data-testid="${picker}"]`)
        .find('[data-testid="period-picker-results-count"]')
        .should('not.exist');
    });
  }

  function pickYearOnly(picker: 'filter-start-period' | 'filter-end-period', year: number): void {
    pickYear(picker, year);
    closeOpenPicker(picker);
  }

  function pickPeriod(
    picker: 'filter-start-period' | 'filter-end-period',
    year: number,
    periodId: string,
  ): void {
    pickYear(picker, year);

    cy.get(`[data-testid="period-picker-result-${periodId}"]`).click();
  }

  // ------------------------------------------------------------------
  // 1. Initial state
  // ------------------------------------------------------------------

  it('shows the idle state on first load and does not fetch', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();

    cy.get('[data-testid="payroll-summary-list-idle"]').should('be.visible');
    cy.get('[data-testid="payroll-summary-table"]').should('not.exist');
    cy.get('@searchPayrollSummaries.all').should('have.length', 0);
  });

  it('loads periods for the bulk-validate selector', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-empty.json',
    }).as('searchPayrollSummaries');

    visitList();

    cy.get('[data-testid="bulk-validate-period-select"] option')
      .should('have.length.at.least', 1);
  });

  // ------------------------------------------------------------------
  // 2. Filter → search
  // ------------------------------------------------------------------

  it('rule 1: start year only → full calendar year', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();

    cy.wait('@searchPayrollSummaries').then(({ request }) => {
      expect(request.query['rangeStart']).to.eq('2026-01-01');
      expect(request.query['rangeEnd']).to.eq('2026-12-31');
      expect(request.query['page']).to.eq('0');
      expect(request.query['size']).to.eq('20');
    });

    cy.get('[data-testid="payroll-summary-table"]').should('be.visible');
    cy.get('[data-testid="payroll-row-s-1"]').should('be.visible');
    cy.get('[data-testid="payroll-row-s-2"]').should('be.visible');
  });

  it('rule 2: start period only → period boundaries', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickPeriod('filter-start-period', 2026, 'p-jan-2026');
    cy.get('[data-testid="filter-apply"]').click();

    cy.wait('@searchPayrollSummaries').then(({ request }) => {
      expect(request.query['rangeStart']).to.eq('2026-01-01');
      expect(request.query['rangeEnd']).to.eq('2026-01-31');
    });
  });

  it('rule 3: start period + end period → period.startDate to endPeriod.endDate', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickPeriod('filter-start-period', 2026, 'p-jan-2026');
    pickPeriod('filter-end-period', 2026, 'p-mar-2026');
    cy.get('[data-testid="filter-apply"]').click();

    cy.wait('@searchPayrollSummaries').then(({ request }) => {
      expect(request.query['rangeStart']).to.eq('2026-01-01');
      expect(request.query['rangeEnd']).to.eq('2026-03-31');
    });
  });

  it('rule 4: start period + end year → period.startDate to Y-12-31', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickPeriod('filter-start-period', 2025, 'p-dec-2025');
    pickYearOnly('filter-end-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();

    cy.wait('@searchPayrollSummaries').then(({ request }) => {
      expect(request.query['rangeStart']).to.eq('2025-12-01');
      expect(request.query['rangeEnd']).to.eq('2026-12-31');
    });
  });

  it('forwards the other filters (status, frequency, matricule, multi-selects)', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);

    // Status
    cy.get('[data-testid="filter-status"]').click();
    cy.get('mat-option').contains('Draft').click();

    // Frequency
    cy.get('[data-testid="filter-payroll-frequency"]').click();
    cy.get('mat-option').contains('Monthly').click();

    // Matricule
    cy.get('[data-testid="filter-matricule"]').type('EMP-0001');

    // Multi-selects — ESC to the trigger, not <body>.
    cy.get('[data-testid="filter-employment-type"]').click();
    cy.get('mat-option').contains('Full time').click();
    cy.get('mat-option').contains('Part time').click();
    cy.get('[data-testid="filter-employment-type"]').type('{esc}');

    cy.get('[data-testid="filter-country"]').click();
    cy.get('mat-option').contains('France').click();
    cy.get('mat-option').contains('Germany').click();
    cy.get('[data-testid="filter-country"]').type('{esc}');

    cy.get('[data-testid="filter-department"]').click();
    cy.get('mat-option').contains('Engineering').click();
    cy.get('[data-testid="filter-department"]').type('{esc}');

    cy.get('[data-testid="filter-role"]').click();
    cy.get('mat-option').contains('Software Engineer').click();
    cy.get('[data-testid="filter-role"]').type('{esc}');

    cy.get('[data-testid="filter-apply"]').click();

    cy.wait('@searchPayrollSummaries').then(({ request }) => {
      expect(request.query['status']).to.eq('DRAFT');
      expect(request.query['payrollFrequency']).to.eq('MONTHLY');
      expect(request.query['employeeMatricule']).to.eq('EMP-0001');
      expect(request.query['anyEmploymentTypes']).to.eq('FULL_TIME,PART_TIME');
      expect(request.query['anyCountryIds']).to.eq('c-fr,c-de');
      expect(request.query['anyDepartmentIds']).to.eq('d-eng');
      expect(request.query['anyRoleIds']).to.eq('r-swe');
    });
  });

  it('Reset clears the form and returns to idle without fetching', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="filter-reset"]').click();

    cy.get('[data-testid="payroll-summary-list-idle"]').should('be.visible');
    cy.get('@searchPayrollSummaries.all').should('have.length', 1);
  });

  // ------------------------------------------------------------------
  // 3. Rendering
  // ------------------------------------------------------------------

  it('renders the table rows on a successful response', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="payroll-row-s-1"]').should('be.visible');
    cy.get('[data-testid="row-employee-name-s-1"]').should('contain', 'Ada Lovelace');
    cy.get('[data-testid="row-total-s-1"]')
      .should('have.attr', 'data-amount', '1350')
      .and('have.attr', 'data-currency', 'EUR');

    cy.get('[data-testid="payroll-row-s-2"]').should('be.visible');
    cy.get('[data-testid="status-badge-draft"]').should('be.visible');
    cy.get('[data-testid="status-badge-validated"]').should('be.visible');
  });

  it('shows the empty row when the response has no lines', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-empty.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="table-empty"]').should('be.visible');
    cy.get('[data-testid^="payroll-row-"]').should('not.exist');
  });

  it('shows the error banner when the search fails', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      statusCode: 500,
      body: { code: 'INTERNAL_ERROR', message: 'Search failed', severity: 'ERROR' },
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="payroll-summary-list-error"]')
      .should('be.visible')
      .and('contain', 'Search failed');
  });

  it('hides the action buttons on VALIDATED rows', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="payroll-row-s-1"]')
      .find('[data-testid="row-edit-s-1"]')
      .should('be.visible');

    cy.get('[data-testid="payroll-row-s-2"]')
      .find('[data-testid="row-edit-s-2"]')
      .should('not.exist');

    cy.get('[data-testid="payroll-row-s-2"]')
      .should('contain', 'Final');
  });

  // ------------------------------------------------------------------
  // 4. Navigation
  // ------------------------------------------------------------------

  it('navigates to the detail page on row click', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="payroll-row-s-1"]').click();
    cy.url().should('include', '/payroll-summaries/s-1');
  });

  it('navigates to the editor on Edit', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="row-edit-s-1"]').click();
    cy.url().should('include', '/payroll-summaries/s-1/edit');
  });

  it('navigates to the create page on New', () => {
    visitList();
    cy.get('[data-testid="payroll-summary-new"]').click();
    cy.url().should('include', '/payroll-summaries/new');
  });

  // ------------------------------------------------------------------
  // 5. Row actions
  // ------------------------------------------------------------------

  it('does not call delete when the confirm dialog is cancelled', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('DELETE', '**/api/v1/payroll-summaries/s-1', {
      statusCode: 204,
    }).as('delete');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="row-delete-s-1"]').click();
    cy.get('[data-testid="confirm-dialog"]').should('be.visible');
    cy.get('[data-testid="confirm-dialog-cancel"]').click();

    cy.get('@delete.all').should('have.length', 0);
    cy.get('[data-testid="payroll-row-s-1"]').should('be.visible');
  });

  it('deletes the row and reloads when confirmed', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('DELETE', '**/api/v1/payroll-summaries/s-1', {
      statusCode: 204,
    }).as('delete');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-after-delete.json',
    }).as('searchAfterDelete');

    cy.get('[data-testid="row-delete-s-1"]').click();
    cy.get('[data-testid="confirm-dialog-confirm"]').click();

    cy.wait('@delete');
    cy.wait('@searchAfterDelete');

    cy.get('[data-testid="payroll-row-s-1"]').should('not.exist');
    cy.get('[data-testid="payroll-row-s-2"]').should('be.visible');
  });

  it('does not call validate when the confirm dialog is cancelled', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('POST', '**/api/v1/payroll-summaries/s-1/validate', {
      statusCode: 200,
      fixture: 'payroll-summary/summary-detail-validated.json',
    }).as('validate');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="row-validate-s-1"]').click();
    cy.get('[data-testid="confirm-dialog"]').should('be.visible');
    cy.get('[data-testid="confirm-dialog-cancel"]').click();

    cy.get('@validate.all').should('have.length', 0);
  });

  it('validates the row and reloads when confirmed', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('POST', '**/api/v1/payroll-summaries/s-1/validate', {
      statusCode: 200,
      fixture: 'payroll-summary/summary-detail-validated.json',
    }).as('validate');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchAfterValidate');

    cy.get('[data-testid="row-validate-s-1"]').click();
    cy.get('[data-testid="confirm-dialog-confirm"]').click();

    cy.wait('@validate');
    cy.wait('@searchAfterValidate');
  });

  // ------------------------------------------------------------------
  // 6. Bulk validate
  // ------------------------------------------------------------------

  it('disables the bulk-validate button until a period is selected', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-empty.json',
    }).as('searchPayrollSummaries');

    visitList();
    cy.get('[data-testid="payroll-summary-bulk-validate"]').should('be.disabled');

    cy.get('[data-testid="bulk-validate-period-select"]').select('p-jan-2026');
    cy.get('[data-testid="payroll-summary-bulk-validate"]').should('be.enabled');
  });

  it('runs a successful bulk validate and shows the result counts', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('POST', '**/api/v1/payroll-summaries/validate-all', {
      statusCode: 200,
      fixture: 'payroll-summary/validate-all-success.json',
    }).as('validateAll');

    visitList();
    cy.get('[data-testid="bulk-validate-period-select"]').select('p-jan-2026');
    cy.get('[data-testid="payroll-summary-bulk-validate"]').click();

    cy.get('[data-testid="bulk-validate-dialog"]').should('be.visible');
    cy.get('[data-testid="bulk-validate-confirm"]').click();

    cy.wait('@validateAll').then(({ request }) => {
      expect(request.body.periodId).to.eq('p-jan-2026');
    });

    cy.get('[data-testid="bulk-result-total"]').should('have.attr', 'data-value', '5');
    cy.get('[data-testid="bulk-result-validated"]').should('have.attr', 'data-value', '5');
    cy.get('[data-testid="bulk-result-failed"]').should('have.attr', 'data-value', '0');
    cy.get('[data-testid="bulk-validate-dialog"]').should('contain', 'All drafts were validated successfully');
  });

  it('shows the failure table on a partial-failure bulk validate', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('POST', '**/api/v1/payroll-summaries/validate-all', {
      statusCode: 200,
      fixture: 'payroll-summary/validate-all-partial-failure.json',
    }).as('validateAll');

    visitList();
    cy.get('[data-testid="bulk-validate-period-select"]').select('p-jan-2026');
    cy.get('[data-testid="payroll-summary-bulk-validate"]').click();
    cy.get('[data-testid="bulk-validate-confirm"]').click();
    cy.wait('@validateAll');

    cy.get('[data-testid="bulk-result-failed"]').should('have.attr', 'data-value', '2');
    cy.get('[data-testid="bulk-failure-s-a"]').should('contain', 'ASSIGNMENT_NOT_IN_EFFECT');
    cy.get('[data-testid="bulk-failure-s-b"]').should('contain', 'PERIOD_CLOSED');
  });

  it('allows retrying the bulk validate after a failure', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('POST', '**/api/v1/payroll-summaries/validate-all', {
      statusCode: 200,
      fixture: 'payroll-summary/validate-all-partial-failure.json',
    }).as('validateAll');

    visitList();
    cy.get('[data-testid="bulk-validate-period-select"]').select('p-jan-2026');
    cy.get('[data-testid="payroll-summary-bulk-validate"]').click();
    cy.get('[data-testid="bulk-validate-confirm"]').click();
    cy.wait('@validateAll');

    cy.get('[data-testid="bulk-validate-again"]').click();
    cy.wait('@validateAll');
  });

  it('does not call validate-all when the dialog is cancelled', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
      fixture: 'payroll-summary/summary-list-page-1.json',
    }).as('searchPayrollSummaries');
    cy.intercept('POST', '**/api/v1/payroll-summaries/validate-all', {
      statusCode: 200,
      fixture: 'payroll-summary/validate-all-success.json',
    }).as('validateAll');

    visitList();
    cy.get('[data-testid="bulk-validate-period-select"]').select('p-jan-2026');
    cy.get('[data-testid="payroll-summary-bulk-validate"]').click();
    cy.get('[data-testid="bulk-validate-dialog"]').should('be.visible');
    cy.get('[data-testid="bulk-validate-cancel"]').click();

    cy.get('@validateAll.all').should('have.length', 0);
  });

  // ------------------------------------------------------------------
  // 7. Pagination
  // ------------------------------------------------------------------

  it('re-fetches with page=1 when the paginator advances', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries*', {
       fixture: 'payroll-summary/summary-list-multi-page.json',
    }).as('searchPayrollSummaries');

    visitList();
    pickYearOnly('filter-start-period', 2026);
    cy.get('[data-testid="filter-apply"]').click();
    cy.wait('@searchPayrollSummaries');

    cy.get('[data-testid="paginator"]')
      .find('button[aria-label="Next page"]')
      .click({ force: true });

    cy.wait('@searchPayrollSummaries').then(({ request }) => {
      expect(request.query['page']).to.eq('1');
    });
  });
});
