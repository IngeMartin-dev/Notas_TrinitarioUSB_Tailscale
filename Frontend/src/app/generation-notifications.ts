/* Componente global de notificaciones de generación de boletines (Frontend).
 * Se monta UNA vez en app.html (fuera del <router-outlet>) para que la pila
 * de notificaciones se vea sin importar en qué apartado de la app esté el
 * usuario, y no desaparezca al navegar entre secciones. */
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GenerationService, GenerationJob } from './services/generation.service';
import { Subscription } from 'rxjs';

/** Tiempo que una notificación "Completada" / "Error" permanece visible
 *  antes de retirarse sola de la pila. */
const AUTO_DISMISS_MS = 6000;

@Component({
  selector: 'app-generation-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './generation-notifications.html',
  styleUrl: './generation-notifications.css'
})
export class GenerationNotifications implements OnInit, OnDestroy {
  allJobs: GenerationJob[] = [];
  visibleJobs: GenerationJob[] = [];

  private sub?: Subscription;
  private tickHandle: any;
  private finishedAtClient: { [jobId: string]: number } = {};

  constructor(private generationService: GenerationService) {}

  ngOnInit(): void {
    this.sub = this.generationService.jobs$.subscribe(jobs => {
      this.allJobs = jobs;
      this.recompute();
    });
    // Recalcula cada segundo para poder retirar las notificaciones ya
    // terminadas pasado el tiempo de gracia, sin depender de un nuevo sondeo.
    this.tickHandle = setInterval(() => this.recompute(), 1000);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.tickHandle) clearInterval(this.tickHandle);
  }

  private recompute() {
    const now = Date.now();
    this.visibleJobs = this.allJobs.filter(job => {
      if (job.status === 'RUNNING') return true;
      if (!this.finishedAtClient[job.jobId]) {
        this.finishedAtClient[job.jobId] = now;
      }
      return now - this.finishedAtClient[job.jobId] < AUTO_DISMISS_MS;
    });
  }

  progressPct(job: GenerationJob): number {
    if (job.status === 'DONE') return 100;
    if (!job.total) return 0;
    return Math.min(100, Math.round((job.completed / job.total) * 100));
  }

  statusIcon(job: GenerationJob): string {
    if (job.status === 'DONE') return '✅';
    if (job.status === 'ERROR') return '⚠️';
    return '⏳';
  }

  /** El servidor corre en UTC; si la hora llega sin zona ("2026-10-26T15:19:00") el
   *  navegador la tomaría como hora local y el contador quedaría siempre en 0. */
  private parseServerTime(value: string | null | undefined): number {
    if (!value) return NaN;
    const hasZone = /(Z|[+-]\d{2}:?\d{2})$/i.test(value);
    return Date.parse(hasZone ? value : value + 'Z');
  }

  elapsedText(job: GenerationJob): string {
    const start = this.parseServerTime(job.startedAt);
    if (Number.isNaN(start)) return '';
    const end = job.finishedAt ? this.parseServerTime(job.finishedAt) : Date.now();
    const seconds = Math.max(0, Math.floor((end - start) / 1000));
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return minutes > 0
      ? `${minutes} min ${remainingSeconds.toString().padStart(2, '0')} s`
      : `${remainingSeconds} s`;
  }

  statusText(job: GenerationJob): string {
    if (job.status === 'DONE') return `Completado — ${job.total} boletines`;
    if (job.status === 'ERROR') return 'Error en la generación';
    return `${job.completed} / ${job.total} boletines`;
  }

  onClick(job: GenerationJob) {
    this.generationService.openJob(job);
  }

  trackByJobId(index: number, job: GenerationJob): string {
    return job.jobId;
  }
}