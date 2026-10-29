import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of, Subscription, take, timeout } from 'rxjs';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './not-found.html',
  styleUrl: './not-found.css',
})
export class NotFound implements OnInit, OnDestroy {
  isConnection = false;
  checking = false;

  // El API corre en :8080 (igual base que el resto de la app)
  private readonly apiBase = 'http://localhost:8080';

  private stop = false;
  private sub?: Subscription;
  private pollTimer?: any;

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient) {}

  ngOnInit() {
    this.route.queryParamMap.subscribe((params) => {
      this.isConnection = params.get('reason') === 'connection';
    });
    if (this.isConnection) {
      this.poll();
    }
  }

  ngOnDestroy() {
    this.stop = true;
    this.sub?.unsubscribe();
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
    }
  }

  /** Sondea el servidor; al recuperarlo, navega al login automáticamente. */
  private poll() {
    if (this.stop) return;
    this.checking = true;
    this.sub = this.http
      .get(`${this.apiBase}/api/health`, { observe: 'response' })
      .pipe(
        timeout(4000),
        catchError(() => of(null)),
        take(1)
      )
      .subscribe({
        next: (res) => {
          this.checking = false;
          if (res) {
            // Cualquier respuesta del servidor (200 o incluso 404) indica que está disponible
            this.stop = true;
            this.router.navigate(['/login']);
          } else {
            this.pollTimer = setTimeout(() => this.poll(), 4000);
          }
        },
      });
  }

  /** Intento inmediato de reconexión (botón Reintentar). */
  retry() {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
    }
    this.poll();
  }

  goHome() {
    this.router.navigate(['/login']);
  }
}
