import { Router } from '@angular/router';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SchoolYearConfigDto {
  yearEndDate: string | null;
  minPassingGrade: number;
  currentAcademicYear: number | null;
  lastWipedAt: string | null;
  lastAdvancedAt: string | null;
  advancePendingClassroomOrg: boolean;
  yearEndNotified: boolean;
  /** true si hoy ya llegó (o pasó) la fecha de fin de año. */
  fechaAlcanzada?: boolean;
}

export interface PendienteOrganizar {
  studentId: number;
  name: string;
  surname: string;
  grade: string;
}

export interface UsuarioSinRol {
  id: number;
  name: string;
  surname: string;
  username: string;
  email: string;
}

export interface RoleDto {
  id: number;
  name: string;
}

/** Un hijo/estudiante enlazado a una cuenta de padre, con su grado y salón
 *  ACTUALES (se usa para mostrar el badge de grado/salón junto a cada
 *  padre en el recuadro "Roles y permisos"). */
export interface HijoGradoSalon {
  studentId: number;
  name: string;
  surname: string;
  grade: string;
  classroom: string | null;
  active: boolean;
}

/** Usuario para el recuadro "Roles y permisos" de Configuración de Año:
 *  a diferencia de UsuarioSinRol, este SÍ incluye a todos los usuarios que
 *  ya tienen un rol (para poder listarlos en sus pestañas Padres /
 *  Profesores / Administradores), más si tienen el rol ADMIN "extra"
 *  sumado además de su rol principal. Para los usuarios PARENT, además
 *  trae "hijos" con el grado/salón de cada estudiante a su cargo. */
export interface UsuarioGestionRol {
  id: number;
  name: string;
  surname: string;
  username: string;
  email: string;
  roleId: number;
  roleName: string; // 'ADMIN' | 'TEACHER' | 'PARENT'
  additionalAdmin: boolean;
  hijos: HijoGradoSalon[];
}

@Injectable({ providedIn: 'root' })
export class SchoolYearService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = 'http://localhost:8080/api/school-year';
  private readonly USERS_BASE = 'http://localhost:8080/api/users';

  getConfig(): Observable<SchoolYearConfigDto> {
    return this.http.get<SchoolYearConfigDto>(`${this.API_BASE}/config`);
  }

  updateConfig(yearEndDate: string | null, minPassingGrade: number): Observable<SchoolYearConfigDto> {
    return this.http.put<SchoolYearConfigDto>(`${this.API_BASE}/config`, { yearEndDate, minPassingGrade });
  }

  wipeNow(): Observable<any> {
    return this.http.post(`${this.API_BASE}/wipe`, { confirm: 'BORRAR' });
  }

  advanceYear(): Observable<{ estudiantesPromovidos: number; estudiantesGraduados: number; pendientesDeOrganizar: PendienteOrganizar[] }> {
    return this.http.post<any>(`${this.API_BASE}/advance`, {});
  }

  getPendientes(): Observable<PendienteOrganizar[]> {
    return this.http.get<PendienteOrganizar[]>(`${this.API_BASE}/advance/pendientes`);
  }

  assignClassrooms(assignments: { [studentId: number]: 'A' | 'B' }): Observable<any> {
    return this.http.post(`${this.API_BASE}/advance/assign-classrooms`, { assignments });
  }

  revertYear(): Observable<{ revertidos: number }> {
    return this.http.post<any>(`${this.API_BASE}/revert`, {});
  }

  getUsuariosSinRol(): Observable<UsuarioSinRol[]> {
    return this.http.get<UsuarioSinRol[]>(`${this.USERS_BASE}/sin-rol`);
  }

  getRolesDisponibles(): Observable<RoleDto[]> {
    return this.http.get<RoleDto[]>(`${this.USERS_BASE}/roles-disponibles`);
  }

  asignarRol(userId: number, roleId: number): Observable<any> {
    return this.http.put(`${this.USERS_BASE}/${userId}/role`, { roleId });
  }

  /** Crea un usuario con rol Administrador (solo un admin puede). */
  crearAdministrador(body: { name: string; surname: string; username: string; email: string; password: string }): Observable<any> {
    return this.http.post(`${this.USERS_BASE}/administradores`, body);
  }

  getUsuariosGestionRoles(): Observable<UsuarioGestionRol[]> {
    return this.http.get<UsuarioGestionRol[]>(`${this.USERS_BASE}/gestion-roles`);
  }

  /** Suma o quita el rol ADMIN "extra" a un usuario, sin tocar su rol
   *  principal (padre/profesor/admin siguen siendo lo que ya eran). */
  toggleAdminExtra(userId: number, enable: boolean): Observable<any> {
    return this.http.put(`${this.USERS_BASE}/${userId}/admin-extra`, { enable });
  }

  fechaAlcanzada(): Observable<{ alcanzada: boolean }> {
    return this.http.get<{ alcanzada: boolean }>(`${this.API_BASE}/fecha-alcanzada`);
  }

  /** Clave de sessionStorage: el aviso de fin de año redirige UNA sola vez por sesión. */
  private static readonly CLAVE_REDIRECCION = 'syc_redirigido';

  /**
   * Si ya llegó la fecha de fin de año, lleva al administrador a Configuración de Año
   * para que pase de año y configure a los estudiantes. Solo lo hace la primera vez que
   * entra en la sesión: antes redirigía en CADA carga del Panel y, como la fecha seguía
   * "alcanzada", el administrador quedaba atrapado sin poder ver el Panel.
   */
  irAConfiguracionSiFinDeAno(router: Router): void {
    try {
      if (sessionStorage.getItem(SchoolYearService.CLAVE_REDIRECCION) === '1') return;
    } catch { /* sin sessionStorage: se redirige igual */ }

    this.fechaAlcanzada().subscribe({
      next: (res) => {
        if (!res.alcanzada) return;
        try { sessionStorage.setItem(SchoolYearService.CLAVE_REDIRECCION, '1'); } catch { /* nada */ }
        if (!router.url.includes('/school-year-config')) {
          router.navigate(['/school-year-config']);
        }
      },
      error: () => { /* silencioso: no bloquea el resto de la app */ }
    });
  }

  /** Tras pasar de año se borra la marca, para que el próximo año vuelva a avisar. */
  reiniciarAvisoFinDeAno(): void {
    try { sessionStorage.removeItem(SchoolYearService.CLAVE_REDIRECCION); } catch { /* nada */ }
  }

  /** Registra un estudiante nuevo (usado en el paso "Grado 1" del asistente
   *  de organización de salones: a diferencia de los demás grados, a Grado
   *  1 no llega nadie promovido, solo estudiantes nuevos). */
  createStudent(student: { name: string; surname: string; documentNumber: string; grade: string; classGroup: string; active: boolean }): Observable<any> {
    return this.http.post('http://localhost:8080/api/students', student);
  }
}