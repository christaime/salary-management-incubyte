import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class SidebarService {
  readonly isOpen = signal<boolean>(false);
  readonly isCollapsed = signal<boolean>(true);

  toggleOpen() {
    this.isOpen.update((v) => !v);
  }

  toggleCollapse() {
    this.isCollapsed.update((v) => !v);
  }
}
