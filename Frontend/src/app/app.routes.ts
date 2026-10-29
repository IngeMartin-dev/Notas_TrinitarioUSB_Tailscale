import { Routes } from '@angular/router';
import { roleGuard, ADMIN_ONLY, TEACHER_OR_DIRECTOR, DIRECTOR_OR_ADMIN, GRADES_VISIBLE_TO, BOLETINES_VISIBLE_TO, ANY_KNOWN_ROLE, PARENT_ONLY } from './guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login').then(m => m.Login)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard),
    canActivate: [roleGuard(ANY_KNOWN_ROLE)]
  },
  {
    path: 'students',
    loadComponent: () => import('./students/students').then(m => m.Students),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'grades',
    loadComponent: () => import('./grades/grades').then(m => m.Grades),
    canActivate: [roleGuard(GRADES_VISIBLE_TO)]
  },
  {
    path: 'mis-notas',
    loadComponent: () => import('./mis-notas/mis-notas').then(m => m.MisNotas),
    canActivate: [roleGuard(PARENT_ONLY)]
  },
  {
    path: 'boletines',
    loadComponent: () => import('./boletines/boletines').then(m => m.Boletines),
    canActivate: [roleGuard(BOLETINES_VISIBLE_TO)]
  },
  {
    path: 'mis-boletines',
    loadComponent: () => import('./mis-boletines/mis-boletines').then(m => m.MisBoletines),
    canActivate: [roleGuard(PARENT_ONLY)]
  },
  {
    path: 'boletines-generados',
    loadComponent: () => import('./boletines-generados/boletines-generados').then(m => m.BoletinesGenerados),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'promociones',
    loadComponent: () => import('./promociones/promociones').then(m => m.Promociones),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'recoveries',
    loadComponent: () => import('./recoveries/recoveries').then(m => m.Recoveries),
    canActivate: [roleGuard(TEACHER_OR_DIRECTOR)]
  },
  {
    path: 'subjects',
    loadComponent: () => import('./subjects/subjects').then(m => m.Subjects),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'teachers',
    loadComponent: () => import('./teachers/teachers').then(m => m.Teachers),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'reports',
    loadComponent: () => import('./consolidado/consolidado').then(m => m.Consolidado),
    canActivate: [roleGuard(DIRECTOR_OR_ADMIN)]
  },
  {
    path: 'consolidados-generados',
    loadComponent: () => import('./consolidados-generados/consolidados-generados').then(m => m.ConsolidadosGenerados),
    canActivate: [roleGuard(DIRECTOR_OR_ADMIN)]
  },
  {
    path: 'chats',
    loadComponent: () => import('./chats/chats').then(m => m.Chats),
    canActivate: [roleGuard(ANY_KNOWN_ROLE)]
  },
  {
    path: 'settings',
    loadComponent: () => import('./settings/settings').then(m => m.Settings),
    canActivate: [roleGuard(ANY_KNOWN_ROLE)]
  },
  {
    path: 'directors-group',
    loadComponent: () => import('./directors-group/directors-group').then(m => m.DirectorsGroup),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'parents',
    loadComponent: () => import('./parents/parents').then(m => m.Parents),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'periods',
    loadComponent: () => import('./periods/periods').then(m => m.Periods),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'school-year-config',
    loadComponent: () => import('./school-year-config/school-year-config').then(m => m.SchoolYearConfig),
    canActivate: [roleGuard(ADMIN_ONLY)]
  },
  {
    path: 'not-found',
    loadComponent: () => import('./not-found/not-found').then(m => m.NotFound)
  },
  {
    path: '**',
    loadComponent: () => import('./not-found/not-found').then(m => m.NotFound)
  }
];