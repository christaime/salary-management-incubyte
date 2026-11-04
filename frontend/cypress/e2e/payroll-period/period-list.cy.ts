describe('Payroll period — list page', () => {
  // ==================================================================
  // Load
  // ==================================================================

  it('loads the page with the default search parameters', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('page')).to.eq('0');
      expect(url.searchParams.get('size')).to.eq('20');
      expect(url.searchParams.get('sortBy')).to.eq('startDate');
      expect(url.searchParams.get('sortDirection')).to.eq('DESC');
      expect(url.searchParams.has('keyword')).to.be.false;
      expect(url.searchParams.has('frequency')).to.be.false;
      expect(url.searchParams.has('isClosed')).to.be.false;
      expect(url.searchParams.has('startDate')).to.be.false;
      expect(url.searchParams.has('endDate')).to.be.false;
      req.reply({ fixture: 'payroll-period/page-periods.json' });
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.get('[data-testid="payroll-period-list-page"]').should('exist');
    cy.get('[data-testid="payroll-period-list-title"]')
      .should('contain.text', 'Pay Schedules');
    cy.get('[data-testid="payroll-period-row-m-1"]').should('exist');
    cy.get('[data-testid="payroll-period-row-bw-1"]').should('exist');
  });

  // ==================================================================
  // Filters — one test per filter dimension, each asserting the query
  // ==================================================================

  it('sends keyword when the user types and clicks Search', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('keyword')).to.eq('May');
      expect(url.searchParams.has('frequency')).to.be.false;
      expect(url.searchParams.has('isClosed')).to.be.false;
      req.reply({ fixture: 'payroll-period/page-monthly-only.json' });
    }).as('search');

    cy.get('[data-testid="filter-keyword"]').type('May');
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');

    cy.get('[data-testid="payroll-period-row-m-1"]').should('exist');
    cy.get('[data-testid="payroll-period-row-bw-1"]').should('not.exist');
  });

  it('sends frequency=BI_WEEKLY when the user picks Bi-Weekly', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('frequency')).to.eq('BI_WEEKLY');
      expect(url.searchParams.has('keyword')).to.be.false;
      req.reply({ fixture: 'payroll-period/page-periods.json' });
    }).as('search');

    cy.get('[data-testid="filter-frequency"]').click();
    cy.get('mat-option').contains('Bi-weekly').click();
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');
  });

  it('sends frequency=MONTHLY when the user picks Monthly', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('frequency')).to.eq('MONTHLY');
      req.reply({ fixture: 'payroll-period/page-monthly-only.json' });
    }).as('search');

    cy.get('[data-testid="filter-frequency"]').click();
    cy.get('mat-option').contains('Monthly').click();
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');
  });

  it('sends isClosed=false when the user picks Open status', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('isClosed')).to.eq('false');
      req.reply({ fixture: 'payroll-period/page-periods.json' });
    }).as('search');

    cy.get('[data-testid="filter-status"]').click();
    cy.get('mat-option').contains('Open').click();
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');
  });

  it('sends isClosed=true when the user picks Closed status', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('isClosed')).to.eq('true');
      req.reply({ fixture: 'payroll-period/page-monthly-settled.json' });
    }).as('search');

    cy.get('[data-testid="filter-status"]').click();
    cy.get('mat-option').contains('Closed').click();
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');
  });

  it('sends startDate and endDate when the user picks a date range', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('startDate')).to.eq('2026-05-01');
      expect(url.searchParams.get('endDate')).to.eq('2026-05-31');
      req.reply({ fixture: 'payroll-period/page-periods.json' });
    }).as('search');

    cy.get('[data-testid="filter-start-date"]').type('2026-05-01');
    cy.get('[data-testid="filter-end-date"]').type('2026-05-31');
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');
  });

  it('combines all active filters in a single request', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.get('keyword')).to.eq('May');
      expect(url.searchParams.get('frequency')).to.eq('MONTHLY');
      expect(url.searchParams.get('isClosed')).to.eq('false');
      expect(url.searchParams.get('startDate')).to.eq('2026-05-01');
      expect(url.searchParams.get('endDate')).to.eq('2026-05-31');
      req.reply({ fixture: 'payroll-period/page-monthly-only.json' });
    }).as('search');

    cy.get('[data-testid="filter-keyword"]').type('May');
    cy.get('[data-testid="filter-frequency"]').click();
    cy.get('mat-option').contains('Monthly').click();
    cy.get('[data-testid="filter-status"]').click();
    cy.get('mat-option').contains('Open').click();
    cy.get('[data-testid="filter-start-date"]').type('2026-05-01');
    cy.get('[data-testid="filter-end-date"]').type('2026-05-31');
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@search');
  });

  it('resets every filter and re-searches with no filter params', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('initial');

    cy.visit('/payroll-periods');
    cy.wait('@initial');

    // Apply a filter first.
    cy.get('[data-testid="filter-keyword"]').type('May');
    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      expect(new URL(req.url).searchParams.get('keyword')).to.eq('May');
      req.reply({ fixture: 'payroll-period/page-monthly-only.json' });
    }).as('filtered');
    cy.get('[data-testid="filter-search"]').click();
    cy.wait('@filtered');

    // Now click Reset and assert the request carries no filter params.
    cy.intercept('GET', '**/api/v1/payroll-periods*', (req) => {
      const url = new URL(req.url);
      expect(url.searchParams.has('keyword')).to.be.false;
      expect(url.searchParams.has('frequency')).to.be.false;
      expect(url.searchParams.has('isClosed')).to.be.false;
      expect(url.searchParams.has('startDate')).to.be.false;
      expect(url.searchParams.has('endDate')).to.be.false;
      expect(url.searchParams.get('page')).to.eq('0');
      req.reply({ fixture: 'payroll-period/page-periods.json' });
    }).as('reset');

    cy.get('[data-testid="filter-reset"]').click();
    cy.wait('@reset');
  });

  // ==================================================================
  // Pagination
  // ==================================================================

  it('paginates: clicking next page sends page=1 with the same filters', () => {
    // 1. Load a fixture with more than one page.
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-periods-multi-page.json',
    }).as('first');

    cy.visit('/payroll-periods');
    cy.wait('@first').then(({ request }) => {
      const url = new URL(request.url);
      expect(url.searchParams.get('page')).to.eq('0');
      expect(url.searchParams.get('size')).to.eq('20');
    });

    // 2. The paginator must reflect "1 of 3 pages" before we click.
    cy.get('[data-testid="paginator"]')
      .should('exist')
      .and('contain.text', 'Page 1 / 3');

    // 3. Register the page-2 intercept and click.
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-page-2.json',
    }).as('second');

    cy.get('[data-testid="mat-paginator"] button[class*="paginator-navigation-next"]').click();

    cy.wait('@second').then(({ request }) => {
      const url = new URL(request.url);
      expect(url.searchParams.get('page')).to.eq('1');
      expect(url.searchParams.get('size')).to.eq('20');
    });

    // 4. Paginator now shows "2 of 3 pages".
    cy.get('[data-testid="paginator"]').should('contain.text', 'Page 2 / 3');
  });

  // ==================================================================
  // Empty state
  // ==================================================================

  it('shows the empty row and hides the paginator when there is no data', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-empty.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.get('[data-testid="table-empty"]').should('exist');
    cy.get('[data-testid="paginator"]').should('not.exist');
  });

  // ==================================================================
  // Delete
  // ==================================================================

  it('deletes a pay schedule from the row action', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('DELETE', '**/api/v1/payroll-periods/m-1', {
      statusCode: 204,
    }).as('delete');

    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-empty.json',
    }).as('searchAfterDelete');

    cy.get('[data-testid="row-delete-m-1"]').click();
    cy.get('[data-testid="confirm-dialog-confirm"]').click();

    cy.wait('@delete');
    cy.wait('@searchAfterDelete');

    cy.get('[data-testid="payroll-period-row-m-1"]').should('not.exist');
  });

  it('does not call DELETE when the confirm dialog is cancelled', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('DELETE', '**/api/v1/payroll-periods/m-1', {
      statusCode: 204,
    }).as('delete');

    cy.get('[data-testid="row-delete-m-1"]').click();
    cy.get('[data-testid="confirm-dialog-cancel"]').click();

    cy.get('@delete.all').should('have.length', 0);
    cy.get('[data-testid="payroll-period-row-m-1"]').should('exist');
  });

  it('shows the PERIOD_REFERENCED message when delete fails because the period is referenced', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('DELETE', '**/api/v1/payroll-periods/m-1', {
      statusCode: 409,
      body: {
        code: 'PERIOD_REFERENCED',
        message: 'Payroll period is referenced by payroll records',
        severity: 'ERROR',
        errors: [],
      },
    }).as('delete');

    cy.get('[data-testid="row-delete-m-1"]').click();
    cy.get('[data-testid="confirm-dialog-confirm"]').click();
    cy.wait('@delete');

    cy.get('[data-testid="payroll-period-list-error"]')
      .should('exist')
      .and('contain.text', 'used by payroll records');

    // Row still present because the reload wasn't triggered (error path).
    cy.get('[data-testid="payroll-period-row-m-1"]').should('exist');
  });

  // ==================================================================
  // Close (settle) — five cases
  // ==================================================================

  it('settle (clean): button is disabled until the checkbox is ticked', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('POST', '**/api/v1/payroll-periods/m-1/check-close', {
      fixture: 'payroll-period/check-close-clean.json',
    }).as('checkClose');

    cy.get('[data-testid="row-settle-m-1"]').click();
    cy.wait('@checkClose');

    cy.get('[data-testid="close-dialog-settle"]').should('be.disabled');
    cy.get('[data-testid="close-dialog-accept"] input[type="checkbox"]').check();
    cy.get('[data-testid="close-dialog-settle"]').should('be.enabled');

    // Close the dialog without settling.
    cy.get('[data-testid="close-dialog-cancel"]').click();
  });

  it('settle (clean): POST /close fires, snackbar shown, row shows Settled', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('POST', '**/api/v1/payroll-periods/m-1/check-close', {
      fixture: 'payroll-period/check-close-clean.json',
    }).as('checkClose');

    cy.intercept('POST', '**/api/v1/payroll-periods/m-1/close', {
      fixture: 'payroll-period/close-success.json',
    }).as('close');

    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-settled.json',
    }).as('searchAfterSettle');

    cy.get('[data-testid="row-settle-m-1"]').click();
    cy.wait('@checkClose');

    cy.get('[data-testid="close-dialog-periods"]').should('exist');
    cy.get('[data-testid="close-dialog-accept"] input[type="checkbox"]').check();
    cy.get('[data-testid="close-dialog-settle"]')
      .should('contain.text', 'Settle payroll')
      .click();

    cy.wait('@close').its('request.method').should('eq', 'POST');
    cy.get('[data-testid="close-dialog-done"]').should('exist');

    // Dialog shows the settled rows.
    cy.get('[data-testid="close-dialog-settled-periods"]').should('exist');
    cy.get('[data-testid="settled-period-m-1"]')
      .should('exist')
      .and('have.attr', 'data-closed', 'true');

    cy.get('[data-testid="close-dialog-close"]').click();
    cy.wait('@searchAfterSettle');

    cy.get('[data-testid="row-settled-m-1"]').should('exist');
  });

  it('settle (warnings): shows the warnings panel and the "Accept and settle" label', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('POST', '**/api/v1/payroll-periods/m-1/check-close', {
      fixture: 'payroll-period/check-close-with-warnings.json',
    }).as('checkClose');

    cy.intercept('POST', '**/api/v1/payroll-periods/m-1/close', {
      fixture: 'payroll-period/close-success.json',
    }).as('close');

    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-settled.json',
    }).as('searchAfterSettle');

    cy.get('[data-testid="row-settle-m-1"]').click();
    cy.wait('@checkClose');

    // Warnings panel present with the human-readable message.
    cy.get('[data-testid="close-dialog-warnings"]').should('exist');
    cy.get('[data-testid="warn-message-MISSING_PAYROLL_SUMMARY"]')
      .should('contain.text', 'no payroll summary');
    cy.get('[data-testid="warn-count-MISSING_PAYROLL_SUMMARY"]')
      .should('contain.text', '3');
    cy.get('[data-testid="warn-employee-e-1"]').should('exist');

    // The primary label is "Accept and settle" (warning path).
    cy.get('[data-testid="close-dialog-settle"]').should('be.disabled');
    cy.get('[data-testid="close-dialog-accept"] input[type="checkbox"]').check();
    cy.get('[data-testid="close-dialog-settle"]')
      .should('contain.text', 'Accept and settle')
      .click();

    cy.wait('@close');
    cy.get('[data-testid="close-dialog-done"]').should('exist');
  });

  it('settle (blocked): only Close is available, no Settle button', () => {
    cy.intercept('GET', '**/api/v1/payroll-periods*', {
      fixture: 'payroll-period/page-monthly-only.json',
    }).as('search');

    cy.visit('/payroll-periods');
    cy.wait('@search');

    cy.intercept('POST', '**/api/v1/payroll-periods/m-1/check-close', {
      statusCode: 409,
      fixture: 'payroll-period/check-close-blocked.json',
    }).as('checkCloseBlocked');

    cy.get('[data-testid="row-settle-m-1"]').click();
    cy.wait('@checkCloseBlocked');

    cy.get('[data-testid="close-dialog-blocked"]').should('exist');
    cy.get('[data-testid="close-dialog-blocker-list"]').should('exist');
    cy.get('[data-testid="blocker-message-UNVALIDATED_PAYROLL_SUMMARY"]')
      .should('contain.text', 'non-VALIDATED');
    cy.get('[data-testid="close-dialog-settle"]').should('not.exist');
    cy.get('[data-testid="close-dialog-close"]').click();
  });

});
