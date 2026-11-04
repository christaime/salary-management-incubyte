describe('Payroll period — editor page', () => {
  // ==================================================================
  // New mode — no cascade
  // ==================================================================

  it('creates a new monthly pay schedule (no cascade)', () => {
    cy.intercept('POST', '**/api/v1/payroll-periods', {
      statusCode: 201,
      fixture: 'payroll-period/period-monthly.json',
    }).as('create');

    cy.visit('/payroll-periods/new');

    cy.get('[data-testid="payroll-period-editor-title"]')
      .should('contain.text', 'New pay schedule');

    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-end-date"]').should('have.value', '2026-05-31');

    cy.get('[data-testid="form-submit"]').click();

    cy.wait('@create').its('request.body').should('deep.equal', {
      name: 'May 2026',
      startDate: '2026-05-01',
      endDate: '2026-05-31',
      frequency: 'MONTHLY',
    });

    cy.url().should('match', /\/payroll-periods$/);
  });

  it('keeps the form disabled until the name is filled', () => {
    cy.visit('/payroll-periods/new');

    // Default state: startDate and endDate are pre-filled by the page,
    // so only `name` is missing. The submit is disabled.
    cy.get('[data-testid="form-submit"]').should('be.disabled');

    cy.get('[data-testid="form-name"]').type('May 2026');

    // Now all required fields have values → submit is enabled.
    cy.get('[data-testid="form-submit"]').should('be.enabled');
  });

  // ==================================================================
  // New mode — cascade
  // ==================================================================

  it('renders the cascade preview with the server-suggested names', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub.json',
    }).as('generateSub');

    cy.visit('/payroll-periods/new');

    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();

    cy.wait('@generateSub');

    cy.get('[data-testid="form-cascade-preview"]').should('exist');
    // New rows are inputs — assert on their values, not the container's text.
    cy.get('[data-testid="form-cascade-name-0"]').should('have.value', 'BW1 May 2026');
    cy.get('[data-testid="form-cascade-name-1"]').should('have.value', 'BW2 May 2026');
    // Existing rows render as text; with `exist: []` in the fixture there are none.
    cy.get('[data-testid="form-cascade-preview"]').should('contain.text', '2 items');
  });

  it('renames a sub-period input without touching the other row', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub.json',
    }).as('generateSub');

    cy.visit('/payroll-periods/new');
    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();
    cy.wait('@generateSub');

    cy.get('[data-testid="form-cascade-name-0"]').clear().type('BW1 May 2026 renamed');

    cy.get('[data-testid="form-cascade-name-0"]').should('have.value', 'BW1 May 2026 renamed');
    cy.get('[data-testid="form-cascade-name-1"]').should('have.value', 'BW2 May 2026');
  });

  it('unchecks the cascade when the user edits the start date', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub.json',
    }).as('generateSub');

    cy.visit('/payroll-periods/new');
    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();
    cy.wait('@generateSub');

    cy.get('[data-testid="form-start-date"]').clear().type('2026-06-01');

    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').should('not.be.checked');
    cy.get('[data-testid="form-cascade-preview"]').should('not.exist');
  });

  it('shows the empty branch when the preview returns no items', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub-empty.json',
    }).as('generateSub');

    cy.visit('/payroll-periods/new');
    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();
    cy.wait('@generateSub');

    cy.get('[data-testid="form-cascade-preview-empty"]').should('exist');
  });

  it('creates a new monthly pay schedule with the bi-weekly cascade', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub.json',
    }).as('generateSub');

    cy.intercept('POST', '**/api/v1/payroll-periods/batch', {
      statusCode: 201,
      fixture: 'payroll-period/batch-all-succeeded.json',
    }).as('createBatch');

    cy.visit('/payroll-periods/new');

    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();
    cy.wait('@generateSub');

    cy.get('[data-testid="form-submit"]').click();

    cy.wait('@createBatch').its('request.body').should('have.length', 3);
    cy.url().should('match', /\/payroll-periods$/);
  });

  it('sends renamed sub-periods in the batch payload', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub.json',
    }).as('generateSub');

    cy.intercept('POST', '**/api/v1/payroll-periods/batch', {
      statusCode: 201,
      fixture: 'payroll-period/batch-all-succeeded.json',
    }).as('createBatch');

    cy.visit('/payroll-periods/new');
    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();
    cy.wait('@generateSub');

    cy.get('[data-testid="form-cascade-name-0"]').clear().type('BW1 May 2026 renamed');
    cy.get('[data-testid="form-cascade-name-1"]').clear().type('BW2 May 2026 renamed');

    cy.get('[data-testid="form-submit"]').click();

    cy.wait('@createBatch').then(({ request }) => {
      const body = request.body as Array<{ name: string; frequency: string }>;
      expect(body).to.have.length(3);
      expect(body[0].frequency).to.eq('MONTHLY');
      expect(body[1].name).to.eq('BW1 May 2026 renamed');
      expect(body[1].frequency).to.eq('BI_WEEKLY');
      expect(body[2].name).to.eq('BW2 May 2026 renamed');
      expect(body[2].frequency).to.eq('BI_WEEKLY');
    });
    cy.url().should('match', /\/payroll-periods$/);
  });

  it('shows the partial-batch summary on a mixed batch result', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/generate-sub*', {
      fixture: 'payroll-period/generate-sub.json',
    }).as('generateSub');

    cy.intercept('POST', '**/api/v1/payroll-periods/batch', {
      statusCode: 200,
      fixture: 'payroll-period/batch-partial-failure.json',
    }).as('createBatch');

    cy.visit('/payroll-periods/new');
    cy.get('[data-testid="form-name"]').type('May 2026');
    cy.get('[data-testid="form-start-date"]').clear().type('2026-05-01');
    cy.get('[data-testid="form-cascade"] input[type="checkbox"]').check();
    cy.wait('@generateSub');
    cy.get('[data-testid="form-submit"]').click();
    cy.wait('@createBatch');

    cy.get('[data-testid="payroll-period-editor-partial"]').should('exist');
    cy.get('[data-testid="payroll-period-editor-partial"]')
      .should('contain.text', '1 created, 1 failed');
    cy.get('[data-testid="payroll-period-editor-partial"]')
      .should('contain.text', 'BW2 May 2026');
    // The failure line carries the date range and the server error message.
    cy.get('[data-testid="payroll-period-editor-partial"]')
      .should('contain.text', '2026-05-15 → 2026-05-28');
    cy.get('[data-testid="payroll-period-editor-partial"]')
      .should('contain.text', 'already overlaps');

    // Still on the editor page (not navigated away).
    cy.url().should('match', /\/payroll-periods\/new$/);
  });

  // ==================================================================
  // Edit mode — happy path
  // ==================================================================

  it('preloads an existing pay schedule and saves the name change', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/m-1', {
      fixture: 'payroll-period/period-monthly.json',
    }).as('getById');

    cy.intercept('PUT', '**/api/v1/payroll-periods/m-1', {
      fixture: 'payroll-period/period-monthly.json',
    }).as('update');

    cy.visit('/payroll-periods/m-1/edit');
    cy.wait('@getById');

    cy.get('[data-testid="form-name"]').should('have.value', 'May 2026');
    cy.get('[data-testid="form-start-date"]').should('have.value', '2026-05-01');

    cy.get('[data-testid="form-name"]').clear().type('May 2026 renamed');
    cy.get('[data-testid="form-submit"]').click();

    cy.wait('@update').its('request.body').should('deep.include', {
      name: 'May 2026 renamed',
    });
    cy.url().should('match', /\/payroll-periods$/);
  });

  // ==================================================================
  // Edit mode — referenced (name-only)
  // ==================================================================

  it('locks the form when the period is referenced', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/m-1', {
      fixture: 'payroll-period/period-monthly-referenced.json',
    }).as('getById');

    cy.visit('/payroll-periods/m-1/edit');
    cy.wait('@getById');

    cy.get('[data-testid="payroll-period-editor-name-only-notice"]')
      .should('be.visible')
      .and('contain.text', 'only its name can be changed');

    cy.get('[data-testid="form-frequency"]').should('have.attr', 'aria-disabled', 'true');
    cy.get('[data-testid="form-start-date"]').should('be.disabled');
    cy.get('[data-testid="form-end-date"]').should('be.disabled');
    cy.get('[data-testid="form-name"]').should('be.enabled');
  });

  it('dismisses the amber notice without unlocking the form', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/m-1', {
      fixture: 'payroll-period/period-monthly-referenced.json',
    }).as('getById');

    cy.visit('/payroll-periods/m-1/edit');
    cy.wait('@getById');

    cy.get('[data-testid="payroll-period-editor-name-only-notice-dismiss"]').click();
    cy.get('[data-testid="payroll-period-editor-name-only-notice"]').should('not.exist');

    cy.get('[data-testid="form-start-date"]').should('be.disabled');
    cy.get('[data-testid="form-name"]').should('be.enabled');
  });

  it('saves the rename for a referenced period and navigates away', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/m-1', {
      fixture: 'payroll-period/period-monthly-referenced.json',
    }).as('getById');

    cy.intercept('PUT', '**/api/v1/payroll-periods/m-1', {
      statusCode: 200,
      body: {
        id: 'm-1',
        name: 'May 2026 renamed',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
        frequency: 'MONTHLY',
        closed: false,
        referenced: true,
      },
    }).as('update');

    cy.visit('/payroll-periods/m-1/edit');
    cy.wait('@getById');

    cy.get('[data-testid="form-name"]').clear().type('May 2026 renamed');
    cy.get('[data-testid="form-submit"]').click();

    cy.wait('@update').its('request.body').should('deep.equal', {
      name: 'May 2026 renamed',
      startDate: '2026-05-01',
      endDate: '2026-05-31',
      frequency: 'MONTHLY',
    });
    cy.url().should('match', /\/payroll-periods$/);
  });

  it('shows the amber notice when the save returns PERIOD_REFERENCED_NAME_ONLY (race guard)', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/m-1', {
      fixture: 'payroll-period/period-monthly.json',
    }).as('getById');

    cy.intercept('PUT', '**/api/v1/payroll-periods/m-1', {
      statusCode: 409,
      fixture: 'payroll-period/error-referenced-name-only.json',
    }).as('update');

    cy.visit('/payroll-periods/m-1/edit');
    cy.wait('@getById');

    cy.get('[data-testid="form-name"]').clear().type('May 2026 renamed');
    cy.get('[data-testid="form-submit"]').click();
    cy.wait('@update');

    cy.get('[data-testid="payroll-period-editor-name-only-notice"]').should('exist');
    cy.get('[data-testid="form-start-date"]').should('be.disabled');
    cy.get('[data-testid="form-name"]').should('be.enabled');
  });

  // ==================================================================
  // Edit mode — load errors
  // ==================================================================

  it('shows the not-found state when GET returns NOT_FOUND', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/missing', {
      statusCode: 404,
      fixture: 'payroll-period/error-not-found.json',
    }).as('getById');

    cy.visit('/payroll-periods/missing/edit');
    cy.wait('@getById');

    cy.get('[data-testid="payroll-period-editor-not-found"]').should('exist');
    cy.get('[data-testid="payroll-period-form"]').should('not.exist');

    cy.contains('button', 'Back to list').click();
    cy.url().should('match', /\/payroll-periods$/);
  });

  it('shows the error state when GET fails for a non-NOT_FOUND reason', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods/m-1', {
      statusCode: 500,
      fixture: 'payroll-period/error-network.json',
    }).as('getById');

    cy.visit('/payroll-periods/m-1/edit');
    cy.wait('@getById');

    cy.get('[data-testid="payroll-period-editor-load-error"]').should('exist');
    cy.get('[data-testid="payroll-period-form"]').should('not.exist');
  });
});
