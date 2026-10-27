describe('Employee editor page', () => {
  beforeEach(() => {
    // Register the reference-data intercepts globally so every test can
    // assert on `.all` even when the request is expected NOT to fire.
    cy.intercept('GET', '**/api/v1/countries*',   { fixture: 'report/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*', { fixture: 'report/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*',       { fixture: 'report/roles.json' }).as('getRoles');
  });

  // ==================================================================
  // Case 1 — Create: fill the form, submit, assert payload
  // ==================================================================

  it('creates a new employee and sends the correct payload', () => {
    cy.intercept('POST', '**/api/v1/employees', {
      statusCode: 201,
      fixture: 'employees/employee-detail-e-1.json',
    }).as('createEmployee');

    cy.visit('/employees/new');
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');

    // Identity
    cy.get('[data-testid="employee-identity-matricule"]').type('EMP-999999');
    cy.get('[data-testid="employee-identity-full-name"]').type('Test Person');
    cy.get('[data-testid="employee-identity-email"]').type('test.person@acme.test');
    cy.get('[data-testid="employee-identity-dob"]').type('1990-01-01');
    cy.get('[data-testid="employee-identity-country"]').click();
    cy.get('mat-option').contains('Cameroon').click();

    // Placement (create mode → editable form is rendered)
    cy.get('[data-testid="placement-employment-type"]').click();
    cy.get('mat-option').contains('Full time').click();

    cy.get('[data-testid="placement-department"]').click();
    cy.get('mat-option').contains('Engineering').click();

    cy.get('[data-testid="placement-role"]').click();
    cy.get('mat-option').contains('Developer').click();

    cy.get('[data-testid="placement-jurisdiction"]').click();
    cy.get('mat-option').contains('France').click();

    cy.get('[data-testid="placement-frequency"]').click();
    cy.get('mat-option').contains('Monthly').click();

    cy.get('[data-testid="placement-start-date"]').type('2026-01-01');

    cy.get('[data-testid="employee-editor-submit"]')
      .should('be.enabled')
      .click();

    cy.wait('@createEmployee').then(({ request }) => {
      const body = request.body;
      expect(body.id).to.be.null;
      expect(body.matricule).to.eq('EMP-999999');
      expect(body.name).to.eq('Test Person');
      expect(body.email).to.eq('test.person@acme.test');
      expect(body.birthDate).to.eq('1990-01-01');
      expect(body.originCountryId).to.eq('cm');
      expect(body.toClose).to.be.null;
      expect(body.assignmentInEffect.id).to.be.null;
      expect(body.assignmentInEffect.countryId).to.eq('fr');
      expect(body.assignmentInEffect.departmentId).to.eq('eng');
      expect(body.assignmentInEffect.roleId).to.eq('dev');
      expect(body.assignmentInEffect.employmentType).to.eq('FULL_TIME');
      expect(body.assignmentInEffect.payrollFrequency).to.eq('MONTHLY');
      expect(body.assignmentInEffect.startDate).to.eq('2026-01-01');
      expect(body.assignmentInEffect.endDate).to.be.null;
    });

    cy.url().should('include', '/employees');
  });

  // ==================================================================
  // Case 2 — Invalid input: errors surface in the form
  // ==================================================================

  it('shows validation errors for each invalid field', () => {
    cy.visit('/employees/new');
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');

    // Matricule too long (> 100)
    cy.get('[data-testid="employee-identity-matricule"]')
      .type('A'.repeat(101))
      .blur();
    cy.get('[data-testid="employee-identity-matricule-error"]')
      .should('be.visible')
      .and('contain', 'Matricule must be at most 100 characters.');

    // Full name too long (> 100)
    cy.get('[data-testid="employee-identity-full-name"]')
      .type('B'.repeat(101))
      .blur();
    cy.get('[data-testid="employee-identity-full-name-error"]')
      .should('be.visible')
      .and('contain', 'Full name must be at most 100 characters.');

    // Email wrong format
    cy.get('[data-testid="employee-identity-email"]')
      .type('not-an-email')
      .blur();
    cy.get('[data-testid="employee-identity-email-error"]')
      .should('be.visible')
      .and('contain', 'Email format is invalid.');

    // Email too long (> 254)
    cy.get('[data-testid="employee-identity-email"]')
      .clear()
      .type(`${'a'.repeat(246)}@test.com`)
      .blur();
    cy.get('[data-testid="employee-identity-email-error"]')
      .should('be.visible')
      .and('contain', 'Email must be at most 254 characters.');

    // Birthdate in the future
    cy.get('[data-testid="employee-identity-dob"]')
      .type('2099-01-01')
      .blur();
    cy.get('[data-testid="employee-identity-dob-error"]')
      .should('be.visible')
      .and('contain', 'Date of birth cannot be in the future.');

    // Submit stays disabled
    cy.get('[data-testid="employee-editor-submit"]').should('be.disabled');
  });

  // ==================================================================
  // Case 3 — Edit, hasPayroll = true, close only (no new assignment)
  // ==================================================================

  it('closes an assignment that has payroll without creating a new one', () => {
    cy.intercept('GET', '**/api/v1/employees/e-1', {
      fixture: 'employees/employee-detail-has-payroll.json',
    }).as('getEmployee');

    cy.intercept('PUT', '**/api/v1/employees/e-1', {
      statusCode: 200,
      fixture: 'employees/employee-detail-has-payroll.json',
    }).as('updateEmployee');

    cy.visit('/employees/e-1');
    cy.wait('@getEmployee');
    cy.wait('@getCountries');

    // hasPayroll = true → pill replaces the editor form.
    // The editor never mounts, so its fetches never fire.
    cy.get('@getDepartments.all').should('have.length', 0);
    cy.get('@getRoles.all').should('have.length', 0);

    // Pill is shown, editor form is not
    cy.get('[data-testid="assignment-pill"]').should('be.visible');
    cy.get('[data-testid="placement-employment-type"]').should('not.exist');

    // Set the end date on the pill
    cy.get('[data-testid="assignment-pill-end-date"]').type('2026-03-31');

    cy.get('[data-testid="employee-editor-submit"]')
      .should('be.enabled')
      .click();

    cy.wait('@updateEmployee').then(({ request }) => {
      const body = request.body;

      expect(body.id).to.eq('e-1');
      expect(body.matricule).to.eq('EMP-000001');

      // In-place update of the existing assignment
      expect(body.assignmentInEffect.id).to.eq('a-1');
      expect(body.assignmentInEffect.endDate).to.eq('2026-03-31');
      expect(body.assignmentInEffect.startDate).to.eq('2021-04-01');
      expect(body.assignmentInEffect.countryId).to.eq('fr');
      expect(body.assignmentInEffect.departmentId).to.eq('eng');
      expect(body.assignmentInEffect.roleId).to.eq('dev');

      // No new assignment → nothing to close separately
      expect(body.toClose).to.be.null;
    });
  });

  // ==================================================================
  // Case 4 — Edit, hasPayroll = true, close + replace (new assignment)
  // ==================================================================

  it('closes the payroll-locked assignment and creates a new one', () => {
    cy.intercept('GET', '**/api/v1/employees/e-1', {
      fixture: 'employees/employee-detail-has-payroll.json',
    }).as('getEmployee');

    cy.intercept('PUT', '**/api/v1/employees/e-1', {
      statusCode: 200,
      fixture: 'employees/employee-detail-has-payroll.json',
    }).as('updateEmployee');

    cy.visit('/employees/e-1');
    cy.wait('@getEmployee');
    cy.wait('@getCountries');

    // Before toggling: pill only, editor not mounted → dept/role never fetched
    cy.get('@getDepartments.all').should('have.length', 0);
    cy.get('@getRoles.all').should('have.length', 0);

    cy.get('[data-testid="assignment-pill"]').should('be.visible');
    cy.get('[data-testid="assignment-pill-toggle-new"]')
      .should('contain', 'New assignment')
      .click();

    // The new-assignment editor mounts → its fetches fire NOW
    cy.wait('@getDepartments');
    cy.wait('@getRoles');

    cy.get('[data-testid="new-assignment-block"]').should('be.visible');

    // End date on the pill becomes required
    cy.get('[data-testid="assignment-pill-end-required-hint"]').should('be.visible');
    cy.get('[data-testid="assignment-pill-end-date"]').type('2026-03-31');
    cy.get('[data-testid="assignment-pill-end-required-hint"]').should('not.exist');

    // Fill the new assignment (scoped to new-assignment-block)
    cy.get('[data-testid="new-assignment-block"]')
      .find('[data-testid="placement-employment-type"]')
      .click();
    cy.get('mat-option').contains('Contractor').click();

    cy.get('[data-testid="new-assignment-block"]')
      .find('[data-testid="placement-department"]')
      .click();
    cy.get('mat-option').contains('Engineering').click();

    cy.get('[data-testid="new-assignment-block"]')
      .find('[data-testid="placement-role"]')
      .click();
    cy.get('mat-option').contains('Developer').click();

    cy.get('[data-testid="new-assignment-block"]')
      .find('[data-testid="placement-jurisdiction"]')
      .click();
    cy.get('mat-option').contains('Cameroon').click();

    cy.get('[data-testid="new-assignment-block"]')
      .find('[data-testid="placement-frequency"]')
      .click();
    cy.get('mat-option').contains('Bi-weekly').click();

    cy.get('[data-testid="new-assignment-block"]')
      .find('[data-testid="placement-start-date"]')
      .type('2026-04-01');

    cy.get('[data-testid="employee-editor-submit"]')
      .should('be.enabled')
      .click();

    cy.wait('@updateEmployee').then(({ request }) => {
      const body = request.body;

      expect(body.id).to.eq('e-1');

      // New assignment (id: null) with the values filled above
      expect(body.assignmentInEffect.id).to.be.null;
      expect(body.assignmentInEffect.employmentType).to.eq('CONTRACTOR');
      expect(body.assignmentInEffect.departmentId).to.eq('eng');
      expect(body.assignmentInEffect.roleId).to.eq('dev');
      expect(body.assignmentInEffect.countryId).to.eq('cm');
      expect(body.assignmentInEffect.payrollFrequency).to.eq('BI_WEEKLY');
      expect(body.assignmentInEffect.startDate).to.eq('2026-04-01');

      // Previous assignment closed at the endDate set on the pill
      expect(body.toClose).to.deep.eq({ id: 'a-1', endDate: '2026-03-31' });
    });
  });

  // ==================================================================
  // Case 5 — Edit, hasPayroll = false, in-place update
  // ==================================================================

  it('updates an assignment in place when it has no payroll', () => {
    cy.intercept('GET', '**/api/v1/employees/e-2', {
      fixture: 'employees/employee-detail-no-payroll.json',
    }).as('getEmployee');

    cy.intercept('PUT', '**/api/v1/employees/e-2', {
      statusCode: 200,
      fixture: 'employees/employee-detail-no-payroll.json',
    }).as('updateEmployee');

    cy.visit('/employees/e-2');
    cy.wait('@getEmployee');
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');

    // No pill (hasPayroll = false) → editable form is rendered on load
    cy.get('[data-testid="assignment-pill"]').should('not.exist');
    cy.get('[data-testid="placement-employment-type"]').should('be.visible');

    // Loaded values from the fixture should be present
    cy.get('[data-testid="employee-identity-matricule"]').should('have.value', 'EMP-000002');

    // Change the role
    cy.get('[data-testid="placement-role"]').click();
    cy.get('mat-option').contains('Developer').click();

    cy.get('[data-testid="employee-editor-submit"]')
      .should('be.enabled')
      .click();

    cy.wait('@updateEmployee').then(({ request }) => {
      const body = request.body;

      expect(body.id).to.eq('e-2');

      // Same assignment id → in-place update, not a new one
      expect(body.assignmentInEffect.id).to.eq('a-2');
      expect(body.assignmentInEffect.roleId).to.eq('dev');
      expect(body.assignmentInEffect.departmentId).to.eq('eng');
      expect(body.assignmentInEffect.countryId).to.eq('cm');
      expect(body.assignmentInEffect.employmentType).to.eq('CONTRACTOR');
      expect(body.assignmentInEffect.payrollFrequency).to.eq('BI_WEEKLY');
      expect(body.assignmentInEffect.startDate).to.eq('2020-11-15');

      // Nothing to close
      expect(body.toClose).to.be.null;
    });
  });
});
