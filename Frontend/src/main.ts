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
  .then(() => {
    // Hide the loading screen once the app is ready
    const loadingElement = document.getElementById('loading');
    if (loadingElement) {
      loadingElement.classList.add('hidden');
      setTimeout(() => {
        loadingElement.style.display = 'none';
      }, 300);
    }
  })
  .catch((err) => console.error(err));