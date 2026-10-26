import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastComponent } from './core/ui/toast/toast.component';
import { HeaderComponent } from './layout/header/header.component';
import { SidebarComponent } from './layout/sidebar/sidebar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent , HeaderComponent, SidebarComponent],
  styles:[`.layout-body {
             display: flex;
             min-height: calc(100vh - 60px);
           }

           .main-content {
             flex: 1;
             padding: 1.5rem;
             background-color: #f9fafb;
           }`],
  template: `
    <app-header />
    <div class="layout-body">
      <app-sidebar />
      <main class="main-content">
       <router-outlet />
      </main>
    </div>
    <app-toast />
  `,
})
export class AppComponent {}
