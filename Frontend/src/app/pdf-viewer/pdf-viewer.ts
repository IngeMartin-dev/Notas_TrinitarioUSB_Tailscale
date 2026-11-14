import {
  Component, ElementRef, EventEmitter, HostListener, Input, NgZone, OnChanges, OnDestroy,
  Output, SimpleChanges, ViewChild, inject, signal
} from '@angular/core';
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import { downloadBlob, pdfFileName } from './pdf-download';

/**
 * Vista previa de PDF que funciona en CUALQUIER dispositivo.
 *
 * Un <iframe> con el PDF solo funciona bien en computador: en Chrome de Android
 * y Safari de iPhone sale en blanco o solo muestra la primera página. Aquí cada
 * página se dibuja con pdf.js en un <canvas>, así la vista previa se ve igual en
 * PC, tablet y celular (Xiaomi, iPhone, etc.), con zoom y descarga.
 *
 * Dos modos:
 *  - modal = false (por defecto): se muestra dentro de la página (ej. Consolidado).
 *  - modal = true: ventana a pantalla completa con botón de cerrar (ej. Boletines).
 */

type PdfJs = typeof import('pdfjs-dist');

let pdfjsPromise: Promise<PdfJs> | null = null;

/** pdf.js pesa bastante: solo se descarga la primera vez que alguien abre una vista previa. */
function loadPdfJs(): Promise<PdfJs> {
  if (!pdfjsPromise) {
    pdfjsPromise = import('pdfjs-dist/legacy/build/pdf.mjs').then((m) => {
      // El "worker" (trabaja en segundo plano) se copia a /pdfjs al compilar (ver angular.json).
      m.GlobalWorkerOptions.workerSrc = '/pdfjs/pdf.worker.min.mjs';
      return m as unknown as PdfJs;
    });
    // Si falla (sin red), la próxima vez se vuelve a intentar.
    pdfjsPromise.catch(() => (pdfjsPromise = null));
  }
  return pdfjsPromise;
}

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 4;
const ZOOM_STEP = 0.25;
/** Tope de píxeles por página (iPhone/Safari falla con canvas de más de ~16,7 millones). */
const MAX_CANVAS_PIXELS = 14_000_000;

@Component({
  selector: 'app-pdf-viewer',
  templateUrl: './pdf-viewer.html',
  styleUrl: './pdf-viewer.css'
})
export class PdfViewer implements OnChanges, OnDestroy {
  /** El PDF ya descargado del servidor. */
  @Input() blob: Blob | null = null;
  /** Nombre con el que se guarda al descargar. */
  @Input() fileName = 'documento.pdf';
  @Input() title = '';
  /** true = ventana a pantalla completa con botón de cerrar. */
  @Input() modal = false;
  @Output() closed = new EventEmitter<void>();

  @ViewChild('scroller', { static: true }) scrollerRef!: ElementRef<HTMLDivElement>;
  @ViewChild('pages', { static: true }) pagesRef!: ElementRef<HTMLDivElement>;

  status = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  pageCount = signal(0);
  currentPage = signal(1);
  zoom = signal(1);

  private readonly zone = inject(NgZone);
  private readonly host = inject(ElementRef<HTMLElement>);
  private doc: PDFDocumentProxy | null = null;
  private renderTasks: RenderTask[] = [];
  private loadToken = 0;
  private renderToken = 0;
  private resizeObserver: ResizeObserver | null = null;
  private lastWidth = 0;
  private resizeTimer: any = null;
  private bodyOverflowBefore: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['blob']) {
      this.zoom.set(1);
      void this.open();
    }
    if (changes['modal'] && this.modal) {
      this.lockBodyScroll();
      this.moveToBody();
    }
  }

  ngOnDestroy(): void {
    this.loadToken++;
    this.renderToken++;
    this.cleanup();
    this.resizeObserver?.disconnect();
    clearTimeout(this.resizeTimer);
    this.unlockBodyScroll();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.modal) this.close();
  }

  close(): void {
    this.closed.emit();
  }

  /** En modo ventana, un clic en el fondo oscuro (fuera del visor) la cierra. */
  onBackdrop(event: Event): void {
    if (this.modal && event.target === event.currentTarget) this.close();
  }

  download(): void {
    if (this.blob) downloadBlob(this.blob, pdfFileName(this.fileName));
  }

  zoomIn(): void { this.setZoom(this.zoom() + ZOOM_STEP); }
  zoomOut(): void { this.setZoom(this.zoom() - ZOOM_STEP); }
  zoomFit(): void { this.setZoom(1); }

  zoomPercent(): string {
    return Math.round(this.zoom() * 100) + '%';
  }

  onScroll(): void {
    const scroller = this.scrollerRef.nativeElement;
    const canvases = Array.from(this.pagesRef.nativeElement.children) as HTMLElement[];
    const mid = scroller.scrollTop + scroller.clientHeight / 3;
    let current = 1;
    canvases.forEach((c, i) => { if (c.offsetTop <= mid) current = i + 1; });
    if (current !== this.currentPage()) this.currentPage.set(current);
  }

  // ---------------------------------------------------------------------------

  private setZoom(value: number): void {
    const z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(value * 100) / 100));
    if (z === this.zoom()) return;
    this.zoom.set(z);
    void this.renderPages();
  }

  private async open(): Promise<void> {
    const token = ++this.loadToken;
    this.cleanup();
    this.currentPage.set(1);
    this.pageCount.set(0);
    if (!this.blob) {
      this.status.set('idle');
      return;
    }
    this.status.set('loading');
    try {
      const [pdfjs, buffer] = await Promise.all([loadPdfJs(), this.blob.arrayBuffer()]);
      if (token !== this.loadToken) return;
      const doc = await pdfjs.getDocument({ data: new Uint8Array(buffer) }).promise;
      if (token !== this.loadToken) {
        void doc.destroy();
        return;
      }
      this.doc = doc;
      this.pageCount.set(doc.numPages);
      this.status.set('ready');
      this.watchResize();
      await this.renderPages();
    } catch (err) {
      if (token !== this.loadToken) return;
      console.error('[PdfViewer] No se pudo mostrar el PDF:', err);
      this.status.set('error');
    }
  }

  /** Si el celular gira o cambia el ancho, se vuelve a dibujar a la nueva medida. */
  private watchResize(): void {
    if (this.resizeObserver || typeof ResizeObserver === 'undefined') return;
    this.zone.runOutsideAngular(() => {
      this.lastWidth = this.scrollerRef.nativeElement.clientWidth;
      this.resizeObserver = new ResizeObserver(() => {
        const w = this.scrollerRef.nativeElement.clientWidth;
        if (Math.abs(w - this.lastWidth) < 4) return;
        this.lastWidth = w;
        clearTimeout(this.resizeTimer);
        this.resizeTimer = setTimeout(() => this.zone.run(() => void this.renderPages()), 150);
      });
      this.resizeObserver.observe(this.scrollerRef.nativeElement);
    });
  }

  private async renderPages(): Promise<void> {
    const doc = this.doc;
    if (!doc) return;
    const token = ++this.renderToken;
    this.cancelRenders();

    const container = this.pagesRef.nativeElement;
    const scroller = this.scrollerRef.nativeElement;
    // Ancho disponible: se descuenta el relleno lateral para que la página no toque los bordes.
    const style = getComputedStyle(scroller);
    const padding = parseFloat(style.paddingLeft || '0') + parseFloat(style.paddingRight || '0');
    const available = Math.max(160, scroller.clientWidth - padding);
    const dpr = Math.min(window.devicePixelRatio || 1, 3);
    const fragment = document.createDocumentFragment();

    try {
      for (let i = 1; i <= doc.numPages; i++) {
        const page = await doc.getPage(i);
        if (token !== this.renderToken) return;

        const base = page.getViewport({ scale: 1 });
        const scale = (available / base.width) * this.zoom();
        const viewport = page.getViewport({ scale });

        // Nitidez: se dibuja a la densidad real de la pantalla, sin pasar el límite del navegador.
        let output = dpr;
        const pixels = viewport.width * output * viewport.height * output;
        if (pixels > MAX_CANVAS_PIXELS) output = Math.sqrt(MAX_CANVAS_PIXELS / (viewport.width * viewport.height));

        const canvas = document.createElement('canvas');
        canvas.className = 'pdf-page';
        canvas.width = Math.floor(viewport.width * output);
        canvas.height = Math.floor(viewport.height * output);
        canvas.style.width = Math.floor(viewport.width) + 'px';
        canvas.style.height = Math.floor(viewport.height) + 'px';
        canvas.setAttribute('role', 'img');
        canvas.setAttribute('aria-label', `Página ${i} de ${doc.numPages}`);
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Canvas no disponible');

        const task = page.render({
          canvasContext: context,
          viewport,
          transform: output !== 1 ? [output, 0, 0, output, 0, 0] : undefined
        } as any);
        this.renderTasks.push(task);
        await task.promise;
        if (token !== this.renderToken) return;
        fragment.appendChild(canvas);
      }
      container.replaceChildren(fragment);
      this.onScroll();
    } catch (err: any) {
      // Una página cancelada (por otro zoom/giro) no es un error.
      if (err?.name === 'RenderingCancelledException' || token !== this.renderToken) return;
      console.error('[PdfViewer] Error dibujando el PDF:', err);
      this.status.set('error');
    }
  }

  private cancelRenders(): void {
    this.renderTasks.forEach((t) => { try { t.cancel(); } catch { /* ya terminó */ } });
    this.renderTasks = [];
  }

  private cleanup(): void {
    this.cancelRenders();
    this.pagesRef?.nativeElement.replaceChildren();
    if (this.doc) {
      void this.doc.destroy();
      this.doc = null;
    }
  }

  /**
   * En modo ventana el visor se pasa al final de <body>. Dentro del layout de la app
   * (que tiene barra superior y contenedores con su propio nivel de superposición)
   * quedaba DEBAJO de la barra superior y se tapaban los botones Cerrar y Descargar.
   * Angular lo quita solo al destruir el componente, esté donde esté.
   */
  private moveToBody(): void {
    const el = this.host.nativeElement as HTMLElement;
    if (el.parentElement !== document.body) document.body.appendChild(el);
  }

  private lockBodyScroll(): void {
    if (this.bodyOverflowBefore !== null) return;
    this.bodyOverflowBefore = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }

  private unlockBodyScroll(): void {
    if (this.bodyOverflowBefore === null) return;
    document.body.style.overflow = this.bodyOverflowBefore;
    this.bodyOverflowBefore = null;
  }
}
