import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Silencia los console.log / console.debug / console.info en el navegador
// del usuario final. Se deja console.warn y console.error intactos porque
// sirven para detectar problemas reales; lo que se pidió quitar es el
// "ruido" de logs informativos que llenaban la consola.
console.log = () => {};
console.debug = () => {};
console.info = () => {};

bootstrapApplication(App, appConfig)
  // La pantalla de carga (index.html) se oculta cuando la app emite "appReady" (sesión ya validada).
  .catch((err) => console.error(err));