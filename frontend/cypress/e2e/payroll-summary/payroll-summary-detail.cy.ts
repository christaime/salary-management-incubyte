describe('Payroll summary detail page', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/countries*',       { fixture: 'payroll-summary/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*',     { fixture: 'payroll-summary/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*',           { fixture: 'payroll-summary/roles.json' }).as('getRoles');
    cy.intercept('GET', '**/api/v1/payroll-periods*', { fixture: 'payroll-summary/payroll-periods.json' }).as('getPeriods');
  });

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  function visitDetail(fixture = 'payroll-summary/summary-detail-draft.json'): void {
    cy.intercept('GET', '**/api/v1/payroll-summaries/s-1', { fixture }).as('getPayrollSummary');
    cy.visit('/payroll-summaries/s-1');
    cy.wait('@getPayrollSummary');
  }

  // ------------------------------------------------------------------
  // 1. Load and rendering
  // ------------------------------------------------------------------

  it('loads the detail on visit and renders the header', () => {
    visitDetail();

    cy.get('[data-testid="payroll-detail-title"]').should('contain', 'Ada Lovelace');
    cy.get('[data-testid="payroll-detail-page"]').should('contain', 'EMP-0001');
    cy.get('[data-testid="status-badge-draft"]').should('be.visible');
  });

  it('renders the placement card with all fields', () => {
    visitDetail();

    cy.get('[data-testid="payroll-detail-placement"]')
      .should('contain', 'France')
      .and('contain', 'FR')
      .and('contain', 'Engineering')
      .and('contain', 'Software Engineer')
      .and('contain', 'Full time')
      .and('contain', '2025-10-01')
      .and('contain', 'Open-ended');
  });

  it('renders the amounts card with all five rows', () => {
    visitDetail();

    cy.get('[data-testid="amount-base"]')
      .should('have.attr', 'data-value', '1000')
      .and('contain', '€1,000.00');
    cy.get('[data-testid="amount-variable"]')
      .should('have.attr', 'data-value', '100')
      .and('contain', '€100.00');
    cy.get('[data-testid="amount-allowances"]')
      .should('have.attr', 'data-value', '50')
      .and('contain', '€50.00');
    cy.get('[data-testid="amount-employer-taxes"]')
      .should('have.attr', 'data-value', '200')
      .and('contain', '€200.00');
    cy.get('[data-testid="amount-total"]')
      .should('have.attr', 'data-value', '1350')
      .and('contain', '€1,350.00');
  });

  it('shows the has-payroll flag', () => {
    visitDetail();
    cy.get('[data-testid="payroll-detail-has-payroll"]').should('contain', 'Yes');
  });

  it('shows the mismatch warning when total does not equal the sum', () => {
    visitDetail('payroll-summary/summary-detail-mismatch.json');
    cy.get('[data-testid="total-mismatch-warning"]').should('be.visible');
  });

  it('does not show the mismatch warning when total matches the sum', () => {
    visitDetail();
    cy.get('[data-testid="total-mismatch-warning"]').should('not.exist');
  });

  // ------------------------------------------------------------------
  // 2. DRAFT actions
  // ------------------------------------------------------------------

  it('renders Edit, Delete, Validate on a DRAFT summary', () => {
    visitDetail();

    cy.get('[data-testid="payroll-detail-edit"]').should('be.visible');
    cy.get('[data-testid="payroll-detail-delete"]').should('be.visible');
    cy.get('[data-testid="payroll-detail-validate"]').should('be.visible');
    cy.get('[data-testid="payroll-detail-final-note"]').should('not.exist');
  });

  it('navigates back to the list', () => {
    visitDetail();
    cy.get('[data-testid="payroll-detail-back"]').click();
    cy.url().should('match', /\/payroll-summaries$/);
  });

  it('navigates to the editor on Edit', () => {
    visitDetail();
    cy.get('[data-testid="payroll-detail-edit"]').click();
    cy.url().should('include', '/payroll-summaries/s-1/edit');
  });

  it('does not call the validate endpoint when the dialog is cancelled', () => {
    cy.intercept('POST', '**/api/v1/payroll-summaries/s-1/validate', {
      statusCode: 200,
      fixture: 'payroll-summary/summary-detail-validated.json',
    }).as('validate');

    visitDetail();
    cy.get('[data-testid="payroll-detail-validate"]').click();

    cy.get('[data-testid="confirm-dialog"]').should('be.visible');
    cy.get('[data-testid="confirm-dialog-cancel"]').click();

    cy.get('@validate.all').should('have.length', 0);
    cy.get('[data-testid="status-badge-draft"]').should('be.visible');
  });

  it('validates the summary and flips the badge on confirm', () => {
    cy.intercept('POST', '**/api/v1/payroll-summaries/s-1/validate', {
      statusCode: 200,
      fixture: 'payroll-summary/summary-detail-validated.json',
    }).as('validate');

    visitDetail();
    cy.get('[data-testid="payroll-detail-validate"]').click();

    cy.get('[data-testid="confirm-dialog-confirm"]').click();
    cy.wait('@validate');

    cy.get('[data-testid="status-badge-validated"]').should('be.visible');
    cy.get('[data-testid="status-badge-draft"]').should('not.exist');
    cy.get('[data-testid="payroll-detail-validate"]').should('not.exist');
  });

  it('does not call the delete endpoint when the dialog is cancelled', () => {
    cy.intercept('DELETE', '**/api/v1/payroll-summaries/s-1', {
      statusCode: 204,
    }).as('delete');

    visitDetail();
    cy.get('[data-testid="payroll-detail-delete"]').click();

    cy.get('[data-testid="confirm-dialog"]').should('be.visible');
    cy.get('[data-testid="confirm-dialog-cancel"]').click();

    cy.get('@delete.all').should('have.length', 0);
    cy.url().should('include', '/payroll-summaries/s-1');
  });

  it('deletes the summary and returns to the list on confirm', () => {
    cy.intercept('DELETE', '**/api/v1/payroll-summaries/s-1', {
      statusCode: 204,
    }).as('delete');

    visitDetail();
    cy.get('[data-testid="payroll-detail-delete"]').click();
    cy.get('[data-testid="confirm-dialog-confirm"]').click();
    cy.wait('@delete');

    cy.url().should('match', /\/payroll-summaries$/);
  });

  // ------------------------------------------------------------------
  // 3. VALIDATED state
  // ------------------------------------------------------------------

  it('renders no action buttons on a VALIDATED summary', () => {
    visitDetail('payroll-summary/summary-detail-validated.json');

    cy.get('[data-testid="status-badge-validated"]').should('be.visible');
    cy.get('[data-testid="payroll-detail-edit"]').should('not.exist');
    cy.get('[data-testid="payroll-detail-delete"]').should('not.exist');
    cy.get('[data-testid="payroll-detail-validate"]').should('not.exist');
    cy.get('[data-testid="payroll-detail-final-note"]')
      .should('be.visible')
      .and('contain', 'final');
  });

  // ------------------------------------------------------------------
  // 4. Error states
  // ------------------------------------------------------------------

  it('shows the error banner when the summary cannot be loaded (404)', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries/s-1', {
      statusCode: 404,
      body: {
        code: 'NOT_FOUND',
        message: 'Payroll summary not found',
        severity: 'ERROR',
      },
    }).as('getPayrollSummary');

    cy.visit('/payroll-summaries/s-1');
    cy.wait('@getPayrollSummary');

    cy.get('[data-testid="payroll-detail-error"]')
      .should('be.visible')
      .and('contain', 'Payroll summary not found');

    // The back button remains usable so the user can leave.
    cy.get('[data-testid="payroll-detail-back"]').should('not.be.disabled');
  });

  it('shows the error banner on a 500', () => {
    cy.intercept('GET', '**/api/v1/payroll-summaries/s-1', {
      statusCode: 500,
      body: {
        code: 'INTERNAL_ERROR',
        message: 'Server error',
        severity: 'ERROR',
      },
    }).as('getPayrollSummary');

    cy.visit('/payroll-summaries/s-1');
    cy.wait('@getPayrollSummary');

    cy.get('[data-testid="payroll-detail-error"]').should('be.visible');
  });
});
