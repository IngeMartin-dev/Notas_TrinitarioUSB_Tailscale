/**
 * Configuración compartida de Firebase Cloud Messaging.
 *
 * La configuración pública del proyecto web puede permanecer en el cliente.
 * Para habilitar notificaciones push, pega la clave pública Web Push
 * (VAPID) de Firebase Cloud Messaging en FIREBASE_VAPID_KEY.
 */
import type { FirebaseOptions } from 'firebase/app';

export const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyAIpQLpT3ZYDD-9jWtOwAdQdxMvke6tvVc',
  authDomain: 'notas-trinitario.firebaseapp.com',
  projectId: 'notas-trinitario',
  storageBucket: 'notas-trinitario.firebasestorage.app',
  messagingSenderId: '593665027046',
  appId: '1:593665027046:web:f6d9d428fcd1026ce4c427',
  measurementId: 'G-L46RJJLB1X',
} satisfies FirebaseOptions;

/**
 * Clave pública Web Push de Firebase (Cloud Messaging > Web configuration).
 * No incluyas aquí claves privadas ni credenciales de una cuenta de servicio.
 */
export const FIREBASE_VAPID_KEY = 'BBQedqMCZbbU7zMCLQfRQRTjwtd7lMd2MlEwUMo2oaPf4VWHtY6rujajsn5T4fT9rrdLskym6-7YoXfb8_yt2vI';

export function isFirebaseConfigured(): boolean {
  return Boolean(
    FIREBASE_CONFIG.apiKey &&
      FIREBASE_CONFIG.authDomain &&
      FIREBASE_CONFIG.projectId &&
      FIREBASE_CONFIG.messagingSenderId &&
      FIREBASE_CONFIG.appId &&
      FIREBASE_VAPID_KEY,
  );
}