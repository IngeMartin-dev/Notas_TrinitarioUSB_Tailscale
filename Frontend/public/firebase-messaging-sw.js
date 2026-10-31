/* eslint-disable no-undef */
// Service worker de Firebase Cloud Messaging.
// Recibe la configuración del proyecto por query string al registrarse
// (ver firebase-push.service.ts → registerServiceWorker), así que NO hace
// falta pegar las llaves de Firebase aquí también: basta con configurarlas
// una sola vez en Frontend/src/app/firebase-config.ts.

importScripts('https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js');

const params = new URLSearchParams(self.location.search);
const firebaseConfig = {
  apiKey: params.get('apiKey'),
  authDomain: params.get('authDomain'),
  projectId: params.get('projectId'),
  storageBucket: params.get('storageBucket'),
  messagingSenderId: params.get('messagingSenderId'),
  appId: params.get('appId')
};

if (firebaseConfig.apiKey) {
  firebase.initializeApp(firebaseConfig);
  const messaging = firebase.messaging();

  // En segundo plano, FCM ya muestra la notificación por sí mismo (el servidor
  // envía el bloque `notification`). Mostrar otra aquí la duplicaba, y con una
  // etiqueta fija cada aviso nuevo reemplazaba al anterior. Solo se inicializa
  // el cliente de mensajería para que FCM pueda entregar en este service worker.
  void messaging;
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window' }).then((clientsArr) => {
      const hadWindowToFocus = clientsArr.some((client) => {
        if (client.url.includes(self.location.origin)) {
          client.focus();
          return true;
        }
        return false;
      });
      if (!hadWindowToFocus) {
        self.clients.openWindow('/');
      }
    })
  );
});