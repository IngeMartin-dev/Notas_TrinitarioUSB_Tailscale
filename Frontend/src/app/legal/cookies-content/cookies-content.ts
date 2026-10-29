import { Component } from '@angular/core';

/**
 * Política de Cookies y Almacenamiento Local.
 *
 * Nota de honestidad técnica: se llama "Política de Cookies" porque es el
 * nombre que la gente busca, pero esta aplicación NO usa cookies HTTP.
 * Usa `localStorage` del navegador. Se documenta así, con precisión, en
 * vez de describir un mecanismo de cookies que no existe en el código.
 */
@Component({
  selector: 'app-cookies-content',
  standalone: true,
  template: `
    <h4>1. QUÉ USAMOS REALMENTE</h4>
    <p>Notas Trinitario no coloca cookies de rastreo, publicitarias ni de terceros en su navegador. En su lugar, usa el almacenamiento local (<code>localStorage</code>) de su propio navegador, que solo su navegador puede leer y que nunca se envía automáticamente a otros sitios.</p>

    <h4>2. QUÉ SE GUARDA LOCALMENTE</h4>
    <ul>
      <li><strong>token / refreshToken:</strong> mantienen su sesión iniciada sin pedirle la contraseña en cada acción.</li>
      <li><strong>role, currentUser:</strong> su rol y datos básicos de perfil, para mostrarle la interfaz correcta.</li>
      <li><strong>Preferencia de tema (claro/oscuro):</strong> recuerda cómo prefiere ver la aplicación.</li>
      <li><strong>Notificaciones y mensajes ya vistos/leídos:</strong> para no repetirle avisos que ya revisó.</li>
    </ul>
    <p>Todo lo anterior es estrictamente necesario para que la aplicación funcione (autenticación e interfaz). No incluye publicidad, rastreo entre sitios ni perfilado comercial.</p>

    <h4>3. TERCEROS QUE CARGAN RECURSOS EXTERNOS O RECIBEN DATOS TÉCNICOS</h4>
    <p>La aplicación carga tipografías de íconos y la librería KaTeX (para fórmulas matemáticas) desde redes de distribución de contenido externas (Google Fonts y jsDelivr). Además, si usted activa las notificaciones push, su navegador se conecta con <strong>Firebase Cloud Messaging (Google)</strong> para poder recibir avisos; el buscador de GIF del chat envía su término de búsqueda a <strong>Tenor (Google)</strong>; y si un docente genera un plan de estudio con IA, ese texto (sin su apellido ni sus calificaciones exactas) se envía a <strong>Mistral AI</strong> para generarlo. Ninguna de estas conexiones usa cookies de rastreo propias de la aplicación, pero implican que su navegador o el backend se conectan directamente a esos proveedores.</p>

    <h4>4. ¿NECESITA ACEPTAR UN BANNER DE COOKIES?</h4>
    <p>No. La normativa de cookies (y su equivalente para almacenamiento local) exige pedir consentimiento previo cuando se usan cookies no esenciales (analítica, publicidad, redes sociales). Como aquí todo el almacenamiento local es estrictamente necesario para que la plataforma funcione, no se requiere un banner de aceptación de cookies. Si en el futuro se agrega analítica o publicidad, esta política se actualizará y se implementará el banner correspondiente.</p>

    <h4>5. CÓMO BORRAR ESTOS DATOS</h4>
    <p>Puede borrar todo lo guardado localmente cerrando sesión desde la aplicación, o manualmente desde la configuración de su navegador ("borrar datos de sitio" para este dominio). Al hacerlo, se cerrará su sesión y deberá volver a iniciarla.</p>

    <h4>6. CONTACTO</h4>
    <p>Para dudas sobre esta política, contacte a secretaria.trinitario&#64;gmail.com</p>
  `,
})
export class CookiesContentComponent {}