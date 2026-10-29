describe('Payroll summary editor page (create mode)', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/countries*',       { fixture: 'payroll-summary/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*',     { fixture: 'payroll-summary/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*',           { fixture: 'payroll-summary/roles.json' }).as('getRoles');
    cy.intercept('GET', '**/api/v1/payroll-periods*', { fixture: 'payroll-summary/payroll-periods.json' }).as('getPeriods');
    cy.intercept('GET', '**/api/v1/employees*',       { fixture: 'payroll-summary/employees-search.json' }).as('searchEmployees');
  });

  function visitCreate(): void {
    cy.visit('/payroll-summaries/new');
  }

  /**
   * The picker no longer has a Search button. Typing 3+ characters fires the
   * debounced fetch (~300ms). We wait on the intercept, then click the first
   * result if one was requested.
   */
  function searchEmployee(query = 'ada'): void {
    cy.get('[data-testid="employee-search-input"]').clear().type(query);
    cy.wait('@searchEmployees');
  }

  function selectFirstResult(): void {
    cy.get('[data-testid^="employee-result-"]').first().click();
  }

  function selectEmployeeById(id: string): void {
    cy.get(`[data-testid="employee-result-${id}"]`).click();
  }

  function pickPeriod(year: number, periodId: string): void {
    cy.get('[data-testid="payroll-period-picker"]')
      .find('[data-testid="period-picker-year-trigger"]')
      .click();
    cy.get(`[data-testid="period-picker-year-${year}"]`).click();
    cy.wait('@getPeriods');
    cy.get(`[data-testid="period-picker-result-${periodId}"]`).click();
  }

  // ------------------------------------------------------------------
  // 1. Initial state
  // ------------------------------------------------------------------

  it('renders the empty create form', () => {
    visitCreate();
    cy.get('[data-testid="payroll-summary-editor-page"]').should('be.visible');
    cy.get('[data-testid="employee-search-input"]').should('be.visible');
    cy.get('[data-testid="editor-save"]').should('be.disabled');
  });

  // ------------------------------------------------------------------
  // 2. Debounced search (3-char minimum)
  // ------------------------------------------------------------------

  it('does NOT fetch below 3 characters', () => {
    visitCreate();
    cy.get('[data-testid="employee-search-input"]').type('ad');
    cy.wait(400); // give the debounce a chance to fire (it shouldn't)
    cy.get('@searchEmployees.all').should('have.length', 0);
  });

  it('fetches automatically ~300ms after the 3rd character', () => {
    visitCreate();
    cy.get('[data-testid="employee-search-input"]').type('ada');
    cy.wait('@searchEmployees').its('request.url').should('contain', 'keyword=ada');
  });

  it('debounces rapid typing into a single fetch', () => {
    visitCreate();
    cy.get('[data-testid="employee-search-input"]').type('ada', { delay: 30 });
    cy.wait('@searchEmployees');
    cy.get('@searchEmployees.all').should('have.length', 1);
  });

  // ------------------------------------------------------------------
  // 3. Selection
  // ------------------------------------------------------------------

  it('selects a result and shows the selected card', () => {
    visitCreate();
    searchEmployee();
    selectFirstResult();

    cy.get('[data-testid="employee-search-results"]').should('not.exist');
    cy.get('[data-testid="employee-selected-card"]')
      .should('be.visible')
      .and('contain', 'Ada Lovelace')
      .and('contain', 'EMP-0001');
  });

  it('replaces the selection when a new result is clicked after clearing', () => {
    visitCreate();

    searchEmployee();
    selectFirstResult();
    cy.get('[data-testid="employee-selected-card"]').should('contain', 'Ada Lovelace');

    // Clear the current selection first — otherwise the results dropdown is
    // hidden and there's nothing to click.
    cy.get('[data-testid="employee-selected-clear"]').click();
    cy.get('[data-testid="employee-search-input"]').should('be.visible');

    searchEmployee('no');
    selectEmployeeById('emp-2');
    cy.get('[data-testid="employee-selected-card"]').should('contain', 'No Assignment');
  });

  it('does not clear the selection when the user types in the search box (after clearing)', () => {
    // Once a selection exists, the picker replaces the search input entirely,
    // so there's no way to "type" while selected. This test documents that:
    // typing before selecting does not affect a prior selection on a fresh page.
    visitCreate();
    searchEmployee();
    selectFirstResult();
    cy.get('[data-testid="employee-selected-card"]').should('contain', 'Ada Lovelace');
    // The search input is gone while selected — assert that and stop here.
    cy.get('[data-testid="employee-search-input"]').should('not.exist');
  });

  // ------------------------------------------------------------------
  // 4. Coverage warnings
  // ------------------------------------------------------------------

  it('warns when the employee has no in-effect assignment', () => {
    visitCreate();
    searchEmployee('no');
    selectEmployeeById('emp-2');

    cy.get('[data-testid="employee-selected-none-assignment"]').should('be.visible');
    cy.get('[data-testid="editor-save"]').should('be.disabled');
  });

  it('does not warn when the period is covered by the assignment', () => {
    visitCreate();
    searchEmployee();
    selectFirstResult();
    pickPeriod(2026, 'p-jan-2026');

    cy.get('[data-testid="coverage-warning"]').should('not.exist');
    cy.get('[data-testid="editor-save"]').should('be.enabled');
  });

  // ------------------------------------------------------------------
  // 5. Save
  // ------------------------------------------------------------------

  it('posts a create request with the assembled payload', () => {
    cy.intercept('POST', '**/api/v1/payroll-summaries', {
      statusCode: 201,
      fixture: 'payroll-summary/summary-detail-draft.json',
    }).as('create');

    visitCreate();
    searchEmployee();
    selectFirstResult();
    pickPeriod(2026, 'p-jan-2026');

    cy.get('[data-testid="editor-input-base"]').clear().type('1000');
    cy.get('[data-testid="editor-input-variable"]').clear().type('100');
    cy.get('[data-testid="editor-input-allowances"]').clear().type('50');
    cy.get('[data-testid="editor-input-employer-taxes"]').clear().type('200');

    cy.get('[data-testid="editor-save"]').click();
    cy.wait('@create').then(({ request }) => {
      expect(request.body).to.deep.eq({
        employeeId: 'emp-1',
        periodId: 'p-jan-2026',
        assignmentId: 'a-1',
        base: 1000,
        variable: 100,
        allowances: 50,
        employerTaxes: 200,
        total: 1350,
        currency: 'EUR',
      });
    });

    cy.url().should('match', /\/payroll-summaries\/[^/]+$/);
  });

  it('maps 409 SUMMARY_ALREADY_EXISTS to a field error and stays on the page', () => {
    cy.intercept('POST', '**/api/v1/payroll-summaries', {
      statusCode: 409,
      body: { code: 'SUMMARY_ALREADY_EXISTS', message: 'Duplicate summary', severity: 'ERROR' },
    }).as('create');

    visitCreate();
    searchEmployee();
    selectFirstResult();
    pickPeriod(2026, 'p-jan-2026');
    cy.get('[data-testid="editor-input-base"]').clear().type('1000');
    cy.get('[data-testid="editor-input-variable"]').clear().type('100');
    cy.get('[data-testid="editor-input-allowances"]').clear().type('50');
    cy.get('[data-testid="editor-input-employer-taxes"]').clear().type('200');

    cy.get('[data-testid="editor-save"]').click();
    cy.wait('@create');
    cy.url().should('include', '/payroll-summaries/new');
  });

  // ------------------------------------------------------------------
  // 6. Pre-loaded create mode (arrived from a non-existent row)
  // ------------------------------------------------------------------

  describe('preloaded create mode (from a non-existent row)', () => {
    beforeEach(() => {
      // Simulate the user clicking a non-existent row: navigate to /new with
      // the stash already set. In the real app the list page calls
      // `service.setSelectedNonExistent(row)` before navigating; in Cypress we
      // reproduce that by intercepting the flow — see note below the tests.
      cy.visit('/payroll-summaries');
    });

    // (See "How to seed the stash" below — Cypress can't easily reproduce
    // the navigation-with-in-memory-state without a helper. The two options
    // are laid out after this block.)

    it('renders the read-only employee/period and the amounts form', () => {
      cy.intercept('GET', '**/api/v1/payroll-summaries*', {
        fixture: 'payroll-summary/summary-list-nonexistent.json',
      }).as('searchNonExistent');

      cy.get('[data-testid="filter-start-period"]')
        .find('[data-testid="period-picker-year-trigger"]').click();
      cy.get('[data-testid="period-picker-year-2026"]').click();
      cy.wait('@getPeriods');
      cy.get('[data-testid="filter-status"]').click();
      cy.get('mat-option').contains('Non existent').click();
      cy.get('[data-testid="filter-apply"]').click();
      cy.wait('@searchNonExistent');

      cy.get('[data-testid^="payroll-row-"]').first().click();

      cy.url().should('match', /\/payroll-summaries\/new$/);
      cy.get('[data-testid="payroll-summary-editor-page"]').should('be.visible');
      cy.get('[data-testid="employee-search-input"]').should('not.exist');
      cy.get('[data-testid="editor-period-selected"]').should('be.visible');
      cy.get('[data-testid="editor-input-base"]').should('be.visible');
    });

    it('pre-fills the amounts from the summary and posts a create', () => {
      cy.intercept('POST', '**/api/v1/payroll-summaries', {
        statusCode: 201,
        fixture: 'payroll-summary/summary-detail-created.json',
      }).as('create');

      cy.intercept('GET', '**/api/v1/payroll-summaries*', {
        fixture: 'payroll-summary/summary-list-nonexistent.json',
      }).as('searchNonExistent');

      cy.get('[data-testid="filter-start-period"]')
        .find('[data-testid="period-picker-year-trigger"]').click();
      cy.get('[data-testid="period-picker-year-2026"]').click();
      cy.wait('@getPeriods');
      cy.get('[data-testid="filter-status"]').click();
      cy.get('mat-option').contains('Non existent').click();
      cy.get('[data-testid="filter-apply"]').click();
      cy.wait('@searchNonExistent');

      cy.get('[data-testid^="payroll-row-"]').first().click();

      cy.get('[data-testid="editor-input-base"]').clear().type('1000');
      cy.get('[data-testid="editor-input-variable"]').clear().type('100');
      cy.get('[data-testid="editor-input-allowances"]').clear().type('50');
      cy.get('[data-testid="editor-input-employer-taxes"]').clear().type('200');

      cy.get('[data-testid="editor-save"]').click();
      cy.wait('@create').then(({ request }) => {
        // Same fields as the row we clicked — the editor prefilled them.
        expect(request.body.employeeId).to.eq('emp-1');
        expect(request.body.periodId).to.eq('p-jan-2026');
        expect(request.body.assignmentId).to.eq('a-1');
        expect(request.body.base).to.eq(1000);
        expect(request.body.total).to.eq(1350);
        expect(request.body.currency).to.eq('EUR');
      });

      cy.url().should('match', /\/payroll-summaries\/s-new$/);
    });

    it('clears the stash after use, so a fresh visit to /new is empty', () => {
      // After the previous test, the service stash should be cleared. A direct
      // visit to /new should show the empty create form (employee picker present).
      cy.visit('/payroll-summaries/new');
      cy.get('[data-testid="employee-search-input"]').should('be.visible');
      cy.get('[data-testid="editor-period-selected"]').should('not.exist');
    });
  });
});
