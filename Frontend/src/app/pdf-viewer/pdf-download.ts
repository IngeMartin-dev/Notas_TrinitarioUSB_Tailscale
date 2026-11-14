/**
 * Descarga REAL de un archivo (Blob) en el dispositivo.
 *
 * Antes, "Descargar" hacía window.open(blobUrl), que solo abre el PDF en otra
 * pestaña (vista previa) y en muchos celulares ni siquiera la abre porque el
 * navegador bloquea la ventana (la petición ya no es "un clic" cuando llega la
 * respuesta). Un <a download> sí guarda el archivo, en computador y celular.
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName || 'documento.pdf';
  a.rel = 'noopener';
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  a.remove();
  // Se libera la memoria un poco después, cuando el navegador ya empezó a guardar.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Garantiza la extensión .pdf en el nombre con que se guarda. */
export function pdfFileName(name: string | null | undefined, fallback = 'documento'): string {
  const base = (name || fallback).trim() || fallback;
  return /\.pdf$/i.test(base) ? base : `${base}.pdf`;
}
