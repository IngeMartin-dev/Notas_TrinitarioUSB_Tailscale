/* Servicio global de generación de boletines (Frontend).
 * Se encarga de:
 *  - Sondear (poll) al backend por generaciones activas/recientes, para que
 *    la "notificación" de generación en curso aparezca en CUALQUIER pantalla
 *    de la app (no solo en el apartado de Boletines), y para que sobreviva
 *    a una recarga de página (F5) ya que el estado real vive en el backend.
 *  - Permitir marcar qué job se debe "enfocar" cuando el usuario hace click
 *    en una notificación, para que el componente de Boletines lo muestre.
 */
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthService } from './auth.service';

export interface GenerationJobFile {
  studentId: number;
  studentName: string;
  fileName: string;
}

export interface GenerationJob {
  jobId: string;
  grade: string;
  classroom: string;
  period: number;
  total: number;
  prepared: number;
  completed: number;
  status: 'RUNNING' | 'DONE' | 'ERROR';
  phase: 'PREPARING' | 'RENDERING';
  startedAt: string;
  finishedAt: string | null;
  errors: string[];
  files: GenerationJobFile[];
}

const API_BASE = 'http://localhost:8080/api/boletines';
const POLL_INTERVAL_MS = 500;

@Injectable({ providedIn: 'root' })
export class GenerationService {
  private jobsSubject = new BehaviorSubject<GenerationJob[]>([]);
  jobs$ = this.jobsSubject.asObservable();

  /** jobId en el que el usuario hizo click desde una notificación. Se lee
   *  con consumeFocusJobId() — un property normal (no un Observable) para
   *  evitar problemas de timing: si Boletines no estaba montado, navegar
   *  crea la instancia DESPUÉS del click, y un Subject ya habría emitido
   *  sin nadie escuchando. */
  private focusJobId: string | null = null;

  /** Emite el jobId cuando el usuario hace click en una notificación y la
   *  pantalla de Boletines YA está montada (navegar a la misma ruta no
   *  recrea el componente, así que necesita este aviso para reaccionar). */
  private focusRequestSubject = new Subject<string>();
  focusRequest$ = this.focusRequestSubject.asObservable();

  private pollHandle: any = null;
  private polling = false;

  constructor(private http: HttpClient, private router: Router, private authService: AuthService) {}

  startPolling() {
    if (this.polling) return;
    this.polling = true;
    this.refresh();
    this.pollHandle = setInterval(() => this.refresh(), POLL_INTERVAL_MS);
  }

  stopPolling() {
    this.polling = false;
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  refresh() {
    // startPolling() se llama al arrancar la app (App constructor), ANTES
    // de que el usuario inicie sesión. Sin este chequeo, cada 500ms se
    // pedía /generaciones sin token y el backend respondía 403 en bucle
    // (eso es lo que se veía repetido en la consola en la pantalla de
    // login). Ahora, si no hay sesión, ni siquiera se hace la petición.
    if (!this.authService.isAuthenticated()) {
      return;
    }
    this.http.get<GenerationJob[]>(`${API_BASE}/generaciones`).subscribe({
      next: (jobs) => this.jobsSubject.next(jobs || []),
      error: (err) => {
        // Si el token dejó de ser válido (expiró / logout en otra pestaña),
        // no seguir insistiendo con la lista vieja en pantalla.
        if (err?.status === 401 || err?.status === 403) {
          this.jobsSubject.next([]);
        }
        /* otros errores: backend no disponible momentáneamente, no limpiar la lista */
      }
    });
  }

  get currentJobs(): GenerationJob[] {
    return this.jobsSubject.value;
  }

  /** Inserta/actualiza un job de inmediato en memoria (optimista) para que
   *  la notificación aparezca sin esperar al próximo ciclo de sondeo. */
  upsertJobLocal(job: GenerationJob) {
    const current = this.jobsSubject.value.filter(j => j.jobId !== job.jobId);
    this.jobsSubject.next([job, ...current]);
  }

  /** Click en una notificación: navega a Boletines y deja marcado qué job enfocar. */
  openJob(job: GenerationJob) {
    this.focusJobId = job.jobId;
    this.focusRequestSubject.next(job.jobId);
    this.router.navigate(['/boletines']);
  }

  consumeFocusJobId(): string | null {
    const id = this.focusJobId;
    this.focusJobId = null;
    return id;
  }
}