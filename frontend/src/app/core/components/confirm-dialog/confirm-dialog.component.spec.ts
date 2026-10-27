import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent, boolean>>;
  let data: ConfirmDialogData;

  function setup(overrides: Partial<ConfirmDialogData> = {}): ConfirmDialogComponent {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    data = {
      title: 'Delete employee',
      message: 'This will permanently delete the employee.',
      ...overrides,
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });

    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('renders title and message', () => {
    const component = setup();
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Delete employee');
    expect(text).toContain('This will permanently delete the employee.');
  });

  it('uses default labels when not provided', () => {
    const component = setup();
    expect(component.confirmLabel).toBe('Confirm');
    expect(component.cancelLabel).toBe('Cancel');
    expect(component.danger).toBeTrue();
  });

  it('uses provided labels', () => {
    const component = setup({ confirmLabel: 'Delete', cancelLabel: 'Keep', danger: false });
    expect(component.confirmLabel).toBe('Delete');
    expect(component.cancelLabel).toBe('Keep');
    expect(component.danger).toBeFalse();
  });

  it('closes with true on confirm', () => {
    const component = setup();
    component.onConfirm();
    expect(dialogRef.close).toHaveBeenCalledOnceWith(true);
  });

  it('closes with false on cancel', () => {
    const component = setup();
    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledOnceWith(false);
  });
});
