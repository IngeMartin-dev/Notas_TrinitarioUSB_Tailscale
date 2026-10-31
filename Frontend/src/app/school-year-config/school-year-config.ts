import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SchoolYearService, SchoolYearConfigDto, PendienteOrganizar, UsuarioSinRol, RoleDto, UsuarioGestionRol
} from '../services/school-year.service';
import { DialogService } from '../services/dialog.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-school-year-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './school-year-config.html',
  styleUrls: ['./school-year-config.css']
})
export class SchoolYearConfig implements OnInit {
  private service = inject(SchoolYearService);
  private dialogService = inject(DialogService);
  private router = inject(Router);

  config: SchoolYearConfigDto | null = null;
  editYearEndDate = '';
  editMinPassingGrade = 3.5;
  savingConfig = false;
  configMessage = '';

  // Borrado de fin de año
  showWipeConfirm = false;
  wipeConfirmText = '';
  wiping = false;
  wipeMessage = '';

  // Adelantar/retroceder año
  advancing = false;
  reverting = false;
  advanceMessage = '';
  /** true justo después de pasar de año: muestra los accesos a Estudiantes / Añadir estudiantes. */
  advanceDone = false;
  pendientes: PendienteOrganizar[] = [];
  asignaciones: { [studentId: number]: 'A' | 'B' } = {};
  guardandoEstudiante: { [studentId: number]: boolean } = {};
  savingClassrooms = false;

  // Asistente paso a paso "Organizar salones" (Grado 1 a Grado 11).
  // Grado 1 es especial: ahí no llega nadie promovido (no existe "Grado 0"),
  // así que en vez de organizar pendientes se registran los estudiantes
  // NUEVOS que ingresan a Grado 1 este año.
  wizardGradeNumbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
  currentWizardIndex = 0;
  newStudentsGrade1: { name: string; surname: string; documentNumber: string; classGroup: 'A' | 'B' }[] = [];
  newStudentForm = { name: '', surname: '', documentNumber: '', classGroup: 'A' as 'A' | 'B' };
  addingStudent = false;
  addStudentError = '';

  // Usuarios sin rol
  usuariosSinRol: UsuarioSinRol[] = [];
  roles: RoleDto[] = [];
  selectedRoleByUser: { [userId: number]: number } = {};
  loadingUsuarios = false;
  assigningUser: { [userId: number]: boolean } = {};

  // Recuadro "Roles y permisos" (pestañas Padres / Profesores / Administradores)
  usuariosGestion: UsuarioGestionRol[] = [];
  loadingGestion = false;
  activeRoleTab: 'PARENT' | 'TEACHER' | 'ADMIN' = 'PARENT';
  togglingAdminExtra: { [userId: number]: boolean } = {};

  // Crear administrador
  newAdminForm = { name: '', surname: '', username: '', email: '', password: '' };
  creatingAdmin = false;
  adminMessage = '';
  adminMessageError = false;
  showAdminPassword = false;

  crearAdministrador() {
    const f = this.newAdminForm;
    this.adminMessage = '';
    this.adminMessageError = true;
    if (f.username.trim().length < 3) { this.adminMessage = 'El usuario debe tener al menos 3 caracteres.'; return; }
    if (!/^\S+@\S+\.\S+$/.test(f.email.trim())) { this.adminMessage = 'Escribe un correo válido.'; return; }
    if (f.password.length < 8) { this.adminMessage = 'La contraseña debe tener al menos 8 caracteres.'; return; }

    this.creatingAdmin = true;
    this.service.crearAdministrador({
      name: f.name.trim(), surname: f.surname.trim(),
      username: f.username.trim(), email: f.email.trim(), password: f.password
    }).subscribe({
      next: () => {
        this.creatingAdmin = false;
        this.adminMessageError = false;
        this.adminMessage = `Administrador "${f.username.trim()}" creado correctamente.`;
        this.newAdminForm = { name: '', surname: '', username: '', email: '', password: '' };
        this.showAdminPassword = false;
        this.loadUsuariosGestion();
      },
      error: (err) => {
        this.creatingAdmin = false;
        this.adminMessage = err?.error?.error || 'No se pudo crear el administrador.';
      }
    });
  }

  /** Filtro por "Grado X - Salón Y" dentro de la pestaña Padres: al elegir
   *  un botón, solo se muestran los padres que tengan al menos un hijo en
   *  ese grado/salón. null = sin filtro (se ven todos los padres). */
  selectedParentGradoSalon: string | null = null;

  ngOnInit() {
    this.loadConfig();
    this.loadPendientes();
    this.loadUsuariosSinRol();
    this.loadRoles();
    this.loadUsuariosGestion();
  }

  loadConfig() {
    this.service.getConfig().subscribe(cfg => {
      this.config = cfg;
      this.editYearEndDate = cfg.yearEndDate || '';
      this.editMinPassingGrade = cfg.minPassingGrade ?? 3.5;
    });
  }

  saveConfig() {
    this.savingConfig = true;
    this.configMessage = '';
    const clamped = Math.max(0, Math.min(5, this.editMinPassingGrade));
    this.service.updateConfig(this.editYearEndDate || null, clamped).subscribe({
      next: (cfg) => {
        this.config = cfg;
        this.savingConfig = false;
        this.configMessage = 'Configuración guardada correctamente.';
        setTimeout(() => this.configMessage = '', 4000);
      },
      error: () => {
        this.savingConfig = false;
        this.configMessage = 'No se pudo guardar la configuración.';
      }
    });
  }

  // ── Borrado de fin de año ──────────────────────────────────────────
  openWipeConfirm() {
    this.wipeConfirmText = '';
    this.wipeMessage = '';
    this.showWipeConfirm = true;
  }

  closeWipeConfirm() {
    this.showWipeConfirm = false;
  }

  confirmWipe() {
    if (this.wipeConfirmText.trim().toUpperCase() !== 'BORRAR') return;
    this.wiping = true;
    this.service.wipeNow().subscribe({
      next: () => {
        this.wiping = false;
        this.showWipeConfirm = false;
        this.wipeMessage = 'Se borró la información del año escolar. Estudiantes y padres de familia se conservaron.';
        this.service.reiniciarAvisoFinDeAno();
        this.loadConfig();
        this.loadConfig();
        setTimeout(() => this.wipeMessage = '', 6000);
      },
      error: (err) => {
        this.wiping = false;
        this.wipeMessage = err?.error?.error || 'No se pudo completar el borrado.';
      }
    });
  }

  // ── Adelantar / retroceder año ───────────────────────────────────────
  async advanceYear() {
    const ok = await this.dialogService.confirm(
      '¿Seguro que quieres pasar de año? Todos los estudiantes activos subirán un grado y deberás elegir manualmente su salón A o B (Grado 1º pasa a 2º, 2º a 3º… y los de Grado 11º pasan a Promociones). Grado 1º quedará vacío para que añadas a los estudiantes nuevos. Se borrarán TODAS las notas, los boletines y consolidados generados y los porcentajes de calificaciones (Promociones, estudiantes y cuentas de profesores se conservan), y el año académico sube en uno. Podrás deshacer el cambio de grados con "Retroceder año" (lo borrado no se recupera).',
      'Adelantar año'
    );
    if (!ok) return;

    this.advancing = true;
    this.advanceMessage = '';
    this.service.advanceYear().subscribe({
      next: (res) => {
        this.advancing = false;
        this.advanceMessage = `Listo: ${res.estudiantesPromovidos} estudiante(s) pasaron al grado siguiente y ${res.estudiantesGraduados} pasaron a Promociones (Grado 11º). Ahora elige el salón (A o B) de cada estudiante en el asistente. Grado 1º quedó sin estudiantes: añade a los nuevos.`;
        this.advanceDone = true;
        this.service.reiniciarAvisoFinDeAno();
        this.pendientes = res.pendientesDeOrganizar;
        this.asignaciones = {};
        this.newStudentsGrade1 = [];
        this.currentWizardIndex = 0; // el asistente siempre arranca en Grado 1
        this.loadConfig();
      },
      error: () => {
        this.advancing = false;
        this.advanceMessage = 'No se pudo adelantar el año.';
      }
    });
  }

  /** Después de pasar de año: abre Estudiantes. */
  irAEstudiantes() {
    this.router.navigate(['/students']);
  }

  /** Después de pasar de año: abre Grado 1º (que queda vacío) listo para añadir estudiantes. */
  irAAnadirGrado1() {
    this.router.navigate(['/students'], { queryParams: { grade: 'Grado 1º' } });
  }

  async revertYear() {
    const ok = await this.dialogService.confirm(
      '¿Deshacer el último "Adelantar año"? Todos los estudiantes volverán a su grado y salón anteriores.',
      'Retroceder año'
    );
    if (!ok) return;

    this.reverting = true;
    this.service.revertYear().subscribe({
      next: (res) => {
        this.reverting = false;
        this.advanceMessage = `${res.revertidos} estudiante(s) devuelto(s) a su grado y salón anterior.`;
        this.advanceDone = false;
        this.pendientes = [];
        this.loadConfig();
      },
      error: () => {
        this.reverting = false;
        this.advanceMessage = 'No se pudo retroceder el año.';
      }
    });
  }

  loadPendientes() {
    this.service.getPendientes().subscribe(list => {
      this.pendientes = list;
      this.asignaciones = {};
    });
  }

  /** Cierra la pantalla de organización sin perder el progreso: los
   *  estudiantes ya marcados quedan como "pendientes" y esta pantalla
   *  vuelve a aparecer la próxima vez que se entre a Configuración de Año. */
  cerrarOrganizacionPorAhora() {
    this.pendientes = [];
  }

  get pendientesPorGrado(): { grade: string; estudiantes: PendienteOrganizar[] }[] {
    const grupos = new Map<string, PendienteOrganizar[]>();
    for (const p of this.pendientes) {
      if (!grupos.has(p.grade)) grupos.set(p.grade, []);
      grupos.get(p.grade)!.push(p);
    }
    return Array.from(grupos.entries()).map(([grade, estudiantes]) => ({ grade, estudiantes }));
  }

  countAsignados(grade: string, salon: 'A' | 'B'): number {
    return this.pendientes.filter(p => p.grade === grade && this.asignaciones[p.studentId] === salon).length;
  }

  /** Pulsar A o B: el estudiante pasa YA a ese salón (se guarda al instante). */
  toggleAsignacion(studentId: number, salon: 'A' | 'B') {
    const anterior = this.asignaciones[studentId];
    if (anterior === salon || this.guardandoEstudiante[studentId]) return;
    this.asignaciones[studentId] = salon;
    this.guardandoEstudiante[studentId] = true;
    this.service.assignClassrooms({ [studentId]: salon }).subscribe({
      next: () => { this.guardandoEstudiante[studentId] = false; },
      error: () => {
        this.guardandoEstudiante[studentId] = false;
        if (anterior) this.asignaciones[studentId] = anterior; else delete this.asignaciones[studentId];
        this.advanceMessage = 'No se pudo asignar el salón. Intenta de nuevo.';
      }
    });
  }

  // ── Asistente paso a paso: Grado 1 → Grado 11 ────────────────────────
  get currentWizardGradeNumber(): number {
    return this.wizardGradeNumbers[this.currentWizardIndex];
  }

  get isFirstWizardStep(): boolean {
    return this.currentWizardIndex === 0;
  }

  get isLastWizardStep(): boolean {
    return this.currentWizardIndex === this.wizardGradeNumbers.length - 1;
  }

  /** Pendientes (estudiantes YA promovidos que hay que organizar) del
   *  grado que se está mostrando en este paso del asistente. Vacío para el
   *  paso de Grado 1 (ahí no hay promovidos, ver newStudentsGrade1). */
  get pendientesDelPasoActual(): PendienteOrganizar[] {
    const gradoTexto = `Grado ${this.currentWizardGradeNumber}º`;
    return this.pendientes.filter(p => p.grade === gradoTexto);
  }

  countAsignadosPaso(salon: 'A' | 'B'): number {
    return this.pendientesDelPasoActual.filter(p => this.asignaciones[p.studentId] === salon).length;
  }

  // -- Paso "Grado 1": alta de estudiantes nuevos --
  agregarEstudianteNuevo() {
    this.addStudentError = '';
    if (!this.newStudentForm.name.trim() || !this.newStudentForm.surname.trim()) {
      this.addStudentError = 'Nombre y apellido son obligatorios.';
      return;
    }
    this.addingStudent = true;
    this.service.createStudent({
      name: this.newStudentForm.name.trim(),
      surname: this.newStudentForm.surname.trim(),
      documentNumber: this.newStudentForm.documentNumber.trim(),
      grade: 'Grado 1º',
      classGroup: 'Salon ' + this.newStudentForm.classGroup,
      active: true
    }).subscribe({
      next: () => {
        this.addingStudent = false;
        this.newStudentsGrade1.push({ ...this.newStudentForm });
        this.newStudentForm = { name: '', surname: '', documentNumber: '', classGroup: 'A' };
      },
      error: (err) => {
        this.addingStudent = false;
        this.addStudentError = err?.error?.error || 'No se pudo registrar el estudiante.';
      }
    });
  }

  countNuevosPorSalon(salon: 'A' | 'B'): number {
    return this.newStudentsGrade1.filter(e => e.classGroup === salon).length;
  }

  // -- Navegación del asistente --
  siguientePasoWizard() {
    // Las asignaciones A/B ya se guardaron al pulsar cada botón: solo se avanza.
    this.avanzarIndiceWizard();
  }

  private avanzarIndiceWizard() {
    if (this.isLastWizardStep) {
      const sinSalon = this.pendientes.filter(p => !this.asignaciones[p.studentId]).length;
      this.advanceMessage = sinSalon > 0
        ? `Quedaron ${sinSalon} estudiante(s) sin salón; los verás de nuevo al entrar a esta pantalla.`
        : 'Organización de salones completada de Grado 1º a Grado 11º.';
      this.pendientes = [];
      this.currentWizardIndex = 0;
      this.loadConfig();
    } else {
      this.currentWizardIndex++;
    }
  }

  pasoAnteriorWizard() {
    if (!this.isFirstWizardStep) {
      this.currentWizardIndex--;
    }
  }

  // ── Usuarios sin rol ──────────────────────────────────────────────
  loadUsuariosSinRol() {
    this.loadingUsuarios = true;
    this.service.getUsuariosSinRol().subscribe({
      next: (list) => {
        this.usuariosSinRol = list;
        this.loadingUsuarios = false;
      },
      error: () => { this.loadingUsuarios = false; }
    });
  }

  loadRoles() {
    this.service.getRolesDisponibles().subscribe(roles => this.roles = roles);
  }

  asignarRol(user: UsuarioSinRol) {
    const roleId = this.selectedRoleByUser[user.id];
    if (!roleId) return;
    this.assigningUser[user.id] = true;
    this.service.asignarRol(user.id, roleId).subscribe({
      next: () => {
        this.assigningUser[user.id] = false;
        this.usuariosSinRol = this.usuariosSinRol.filter(u => u.id !== user.id);
      },
      error: () => { this.assigningUser[user.id] = false; }
    });
  }

  // ── Recuadro "Roles y permisos": todos los usuarios por pestaña ────────
  loadUsuariosGestion() {
    this.loadingGestion = true;
    this.service.getUsuariosGestionRoles().subscribe({
      next: (list) => {
        this.usuariosGestion = list;
        this.loadingGestion = false;
      },
      error: () => { this.loadingGestion = false; }
    });
  }

  setRoleTab(tab: 'PARENT' | 'TEACHER' | 'ADMIN') {
    this.activeRoleTab = tab;
    if (tab !== 'PARENT') {
      // El filtro de grado/salón solo aplica dentro de Padres; al salir de
      // esa pestaña se limpia para que no quede "pegado" si se vuelve luego.
      this.selectedParentGradoSalon = null;
    }
  }

  /** Etiqueta "Grado X - Salón Y" única por cada combinación real que
   *  tenga al menos un hijo de algún padre (para pintar los botones de
   *  filtro). Ordenada numéricamente por grado. */
  get gradosSalonesConPadres(): string[] {
    const set = new Set<string>();
    for (const u of this.usuariosGestion) {
      if (u.roleName !== 'PARENT') continue;
      for (const hijo of u.hijos) {
        if (!hijo.grade) continue;
        const etiqueta = hijo.classroom ? `${hijo.grade} - ${hijo.classroom}` : hijo.grade;
        set.add(etiqueta);
      }
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  }

  /** Selecciona (o quita, si ya estaba activo) el filtro de grado/salón de
   *  la pestaña Padres. null = "Todos". */
  selectGradoSalonFiltro(valor: string | null) {
    this.selectedParentGradoSalon = this.selectedParentGradoSalon === valor ? null : valor;
  }

  get usuariosDeLaPestanaActiva(): UsuarioGestionRol[] {
    if (this.activeRoleTab === 'ADMIN') {
      // "Administradores" debe mostrar a TODO el que hoy tiene privilegios
      // reales de Administrador: los que lo tienen como rol principal, MÁS
      // los que lo tienen como rol "extra" sumado desde Padres o Profesores
      // (additionalAdmin) — no solo los que tienen roleName === 'ADMIN'.
      return this.usuariosGestion.filter(u => u.roleName === 'ADMIN' || u.additionalAdmin);
    }

    let lista = this.usuariosGestion.filter(u => u.roleName === this.activeRoleTab);

    if (this.activeRoleTab === 'PARENT' && this.selectedParentGradoSalon) {
      const filtro = this.selectedParentGradoSalon;
      lista = lista.filter(u => u.hijos.some(hijo => {
        const etiqueta = hijo.classroom ? `${hijo.grade} - ${hijo.classroom}` : hijo.grade;
        return etiqueta === filtro;
      }));
    }

    return lista;
  }

  countPorRol(roleName: 'PARENT' | 'TEACHER' | 'ADMIN'): number {
    if (roleName === 'ADMIN') {
      // Mismo criterio que usuariosDeLaPestanaActiva: admins nativos + admins "extra".
      return this.usuariosGestion.filter(u => u.roleName === 'ADMIN' || u.additionalAdmin).length;
    }
    return this.usuariosGestion.filter(u => u.roleName === roleName).length;
  }

  /** Da (o quita) el rol de administrador "extra" a un usuario, sin
   *  importar cuál sea su rol principal: se le suma, no se le reemplaza. */
  toggleAdminExtra(user: UsuarioGestionRol) {
    if (user.roleName === 'ADMIN') return; // ya es admin de por sí
    const nuevoValor = !user.additionalAdmin;
    this.togglingAdminExtra[user.id] = true;
    this.service.toggleAdminExtra(user.id, nuevoValor).subscribe({
      next: () => {
        this.togglingAdminExtra[user.id] = false;
        user.additionalAdmin = nuevoValor;
      },
      error: () => { this.togglingAdminExtra[user.id] = false; }
    });
  }
}