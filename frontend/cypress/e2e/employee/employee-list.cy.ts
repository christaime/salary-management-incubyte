describe('Employee list page', () => {
  beforeEach(() => {
    // Shared reference data (already used by other specs)
    cy.intercept('GET', '**/api/v1/countries*',   { fixture: 'report/countries.json' }).as('getCountries');
    cy.intercept('GET', '**/api/v1/departments*', { fixture: 'report/departments.json' }).as('getDepartments');
    cy.intercept('GET', '**/api/v1/roles*',       { fixture: 'report/roles.json' }).as('getRoles');
  });

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  function visitList(): void {
    cy.visit('/employees');
    cy.wait('@getCountries');
    cy.wait('@getDepartments');
    cy.wait('@getRoles');
  }

  function openJurisdictionSelect(): void {
    cy.get('[data-testid="filter-jurisdiction"]').click();
  }

  function openDepartmentSelect(): void {
    cy.get('[data-testid="filter-department"]').click();
  }

  function openRoleSelect(): void {
    cy.get('[data-testid="filter-role"]').click();
  }

  function openEmploymentTypeSelect(): void {
    cy.get('[data-testid="filter-employment-type"]').click();
  }

  // ------------------------------------------------------------------
  // Initial load
  // ------------------------------------------------------------------

  it('loads the default page and renders the rows', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees').then((interception) => {
      // No filters were set; page 0 and size 10 must be sent
      expect(interception.request.query['page']).to.eq('0');
      expect(interception.request.query['size']).to.eq('10');
      expect(interception.request.query['keyword']).to.be.undefined;
      expect(interception.request.query['anyAssignmentCountryIds']).to.be.undefined;
      expect(interception.request.query['anyDepartmentIds']).to.be.undefined;
      expect(interception.request.query['anyRoleIds']).to.be.undefined;
      expect(interception.request.query['anyEmploymentTypes']).to.be.undefined;
    });

    cy.get('[data-testid="table-row"]').should('have.length', 2);
    cy.get('[data-testid="row-name-e-1"]').should('contain', 'Alexandre Vance');
    cy.get('[data-testid="row-name-e-2"]').should('contain', 'Elena Rostova');
  });

  // ------------------------------------------------------------------
  // Filtering: keyword
  // ------------------------------------------------------------------

  it('sends the keyword and reloads from page 0', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    // Second request — after typing
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployeesFiltered');

    cy.get('[data-testid="filter-keyword"]').type('vance');
    cy.wait('@getEmployeesFiltered').then((interception) => {
      expect(interception.request.query['keyword']).to.eq('vance');
      expect(interception.request.query['page']).to.eq('0');
    });
  });

  // ------------------------------------------------------------------
  // Filtering: jurisdiction
  // ------------------------------------------------------------------

  it('sends anyAssignmentCountryIds when a jurisdiction is selected', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-filtered-fr.json',
    }).as('getEmployeesFiltered');

    openJurisdictionSelect();
    cy.get('mat-option').contains('France').click();

    cy.wait('@getEmployeesFiltered').then((interception) => {
      expect(interception.request.query['anyAssignmentCountryIds']).to.eq('fr');
    });

    // Renders the filtered row
    cy.get('[data-testid="table-row"]').should('have.length', 1);
    cy.get('[data-testid="row-name-e-1"]').should('contain', 'Alexandre Vance');
  });

  // ------------------------------------------------------------------
  // Filtering: department
  // ------------------------------------------------------------------

  it('sends anyDepartmentIds when a department is selected', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployeesFiltered');

    openDepartmentSelect();
    cy.get('mat-option').contains('Engineering').click();

    cy.wait('@getEmployeesFiltered').then((interception) => {
      expect(interception.request.query['anyDepartmentIds']).to.eq('eng');
    });
  });

  // ------------------------------------------------------------------
  // Filtering: role
  // ------------------------------------------------------------------

  it('sends anyRoleIds when a role is selected', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployeesFiltered');

    openRoleSelect();
    cy.get('mat-option').contains('Developer').click();

    cy.wait('@getEmployeesFiltered').then((interception) => {
      expect(interception.request.query['anyRoleIds']).to.eq('dev');
    });
  });

  // ------------------------------------------------------------------
  // Filtering: employment type
  // ------------------------------------------------------------------

  it('sends anyEmploymentTypes when an employment type is selected', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployeesFiltered');

    openEmploymentTypeSelect();
    cy.get('mat-option').contains('Full time').click();

    cy.wait('@getEmployeesFiltered').then((interception) => {
      expect(interception.request.query['anyEmploymentTypes']).to.eq('FULL_TIME');
    });
  });

  // ------------------------------------------------------------------
  // Empty state
  // ------------------------------------------------------------------

  it('renders the empty state when no employees match', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-empty.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.get('[data-testid="table-empty"]').should('be.visible');
    cy.get('[data-testid="table-row"]').should('have.length', 0);
  });

  // ------------------------------------------------------------------
  // Edit action → navigates to editor
  // ------------------------------------------------------------------

  it('navigates to the editor when the edit button is clicked', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    // Editor page: fetches the employee by id
    cy.intercept('GET', '**/api/v1/employees/e-1', {
      fixture: 'employees/employee-detail-e-1.json',
    }).as('getEmployeeDetail');

    cy.get('[data-testid="row-edit-e-1"]').click();
    cy.wait('@getEmployeeDetail');

    // URL
    cy.url().should('include', '/employees/e-1');

    // The editor loaded the values into the form
    cy.get('[data-testid="employee-editor-title"]').should('contain', 'Edit employee');
    cy.get('[data-testid="employee-identity-matricule"]').should('have.value', 'EMP-000001');
    cy.get('[data-testid="employee-identity-full-name"]').should('have.value', 'Alexandre Vance');
    cy.get('[data-testid="employee-identity-email"]').should('have.value', 'a.vance@acme.test');
  });

  // ------------------------------------------------------------------
  // Delete: dialog → cancel
  // ------------------------------------------------------------------

  it('opens the confirm dialog on delete and does nothing when cancelled', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.intercept('DELETE', '**/api/v1/employees/e-1', {
      statusCode: 204,
    }).as('deleteEmployee');

    cy.get('[data-testid="row-delete-e-1"]').click();

    // Dialog shown
    cy.get('[data-testid="confirm-dialog"]').should('be.visible');
    cy.get('[data-testid="confirm-dialog-title"]').should('contain', 'Delete employee');
    cy.get('[data-testid="confirm-dialog-message"]')
      .should('contain', 'Alexandre Vance')
      .and('contain', 'EMP-000001');

    // Cancel
    cy.get('[data-testid="confirm-dialog-cancel"]').click();

    // Dialog closed, no request
    cy.get('[data-testid="confirm-dialog"]').should('not.exist');
    cy.get('@deleteEmployee.all').should('have.length', 0);
  });

  // ------------------------------------------------------------------
  // Delete: dialog → confirm
  // ------------------------------------------------------------------

  it('sends the delete request when the dialog is confirmed and reloads', () => {
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-page-1.json',
    }).as('getEmployees');

    visitList();
    cy.wait('@getEmployees');

    cy.intercept('DELETE', '**/api/v1/employees/e-1', {
      statusCode: 204,
    }).as('deleteEmployee');

    // After the delete, the page reloads the list. Return the list without e-1.
    cy.intercept('GET', '**/api/v1/employees*', {
      fixture: 'employees/employee-list-after-delete.json',
    }).as('getEmployeesAfterDelete');

    cy.get('[data-testid="row-delete-e-1"]').click();

    cy.get('[data-testid="confirm-dialog"]').should('be.visible');
    cy.get('[data-testid="confirm-dialog-confirm"]').click();

    cy.wait('@deleteEmployee').its('request.method').should('eq', 'DELETE');
    cy.wait('@getEmployeesAfterDelete');

    // Only the surviving row remains.
    cy.get('[data-testid="table-row"]').should('have.length', 1);
    cy.get('[data-testid="row-name-e-2"]').should('contain', 'Elena Rostova');
    cy.get('[data-testid="row-name-e-1"]').should('not.exist');
  });
});
