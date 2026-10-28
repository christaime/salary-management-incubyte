import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class SidebarService {
  readonly isOpen = signal<boolean>(true);
  readonly isCollapsed = signal<boolean>(false);

  toggleOpen() {
    this.isOpen.update((v) => !v);
  }

  toggleCollapse() {
    this.isCollapsed.update((v) => !v);
  }
}
