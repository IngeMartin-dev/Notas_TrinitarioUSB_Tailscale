import { Component } from '@angular/core';

/**
 * Contenido de la Política de Privacidad.
 *
 * CORRECCIÓN respecto a la versión anterior (solo vivía en settings.html):
 * el texto original afirmaba que la aplicación usa "cookies" para guardar
 * datos de navegación. Eso es falso: el frontend nunca hace
 * `document.cookie`, solo usa `localStorage` (token de sesión, preferencia
 * de tema, notificaciones vistas). Decir que se usan cookies cuando no es
 * así es, en sí mismo, información engañosa dentro de un documento legal.
 * Se corrigió para describir con precisión el almacenamiento local, y se
 * remite a la nueva "Política de Cookies y Almacenamiento Local" para el
 * detalle técnico (ver CookiesContentComponent).
 */
@Component({
  selector: 'app-privacy-content',
  standalone: true,
  template: `
    <h4>1. INFORMACIÓN RECOPILADA</h4>
    <p>Recopilamos la siguiente información personal:</p>
    <ul>
      <li>Información de perfil: nombre, apellido, correo electrónico</li>
      <li>Datos académicos: calificaciones, asistencia, reportes</li>
      <li>Información de uso de la aplicación</li>
      <li>Datos técnicos guardados localmente en su navegador (ver "Política de Cookies y Almacenamiento Local")</li>
    </ul>

    <h4>2. USO DE LA INFORMACIÓN</h4>
    <p>La información recopilada se utiliza para:</p>
    <ul>
      <li>Gestionar el acceso a la plataforma educativa</li>
      <li>Registrar y mostrar calificaciones académicas</li>
      <li>Comunicar información institucional</li>
      <li>Mejorar y personalizar la experiencia del usuario</li>
      <li>Cumplir con obligaciones legales y reglamentarias</li>
    </ul>

    <h4>3. PROTECCIÓN DE DATOS</h4>
    <p>Implementamos medidas de seguridad técnicas y organizativas para proteger sus datos personales contra:</p>
    <ul>
      <li>Acceso no autorizado</li>
      <li>Pérdida o destrucción accidental</li>
      <li>Manipulación o difusión no autorizada</li>
    </ul>

    <h4>4. COMPARTICIÓN DE INFORMACIÓN Y TERCEROS</h4>
    <p>Sus datos personales NO se venden ni se comparten con fines publicitarios. Se comparten datos puntuales con dos proveedores externos, únicamente para que la plataforma funcione:</p>
    <ul>
      <li><strong>Mistral AI</strong> (proveedor de inteligencia artificial): cuando un docente genera un plan de estudio personalizado, se envía a este proveedor el primer nombre del estudiante, su grado, el período y los temas a reforzar — nunca el apellido completo ni las calificaciones exactas. Este proveedor no recibe usuario ni contraseña.</li>
      <li><strong>Firebase Cloud Messaging (Google)</strong>: si usted activa las notificaciones push, su dispositivo recibe un identificador técnico de notificación (no asociado a su nombre por Google) para poder enviarle avisos de la plataforma.</li>
      <li><strong>Tenor (Google)</strong>: si usa el buscador de GIF dentro del chat, el término que escribe se envía directamente desde su navegador a la API de Tenor para mostrarle resultados.</li>
    </ul>
    <p>Además, sus datos podrán compartirse cuando:</p>
    <ul>
      <li>Sea requerido por autoridades educativas competentes</li>
      <li>Sea necesario para cumplir obligaciones legales</li>
      <li>Contemos con su consentimiento expreso</li>
    </ul>

    <h4>5. DERECHOS DEL USUARIO</h4>
    <p>Como usuario, usted tiene derecho a:</p>
    <ul>
      <li>Acceder a sus datos personales</li>
      <li>Rectificar datos inexactos</li>
      <li>Solicitar la eliminación de sus datos</li>
      <li>Oponerse al tratamiento de sus datos</li>
      <li>Exportar una copia de sus datos</li>
    </ul>

    <h4>6. RETENCIÓN DE DATOS</h4>
    <p>Los datos personales se conservarán mientras dure la relación académica con el Colegio Trinitario y posteriormente durante el período exigido por la legislación educativa vigente.</p>

    <h4>7. ALMACENAMIENTO LOCAL (no usamos cookies)</h4>
    <p>Esta aplicación no utiliza cookies de rastreo ni de publicidad. Para funcionar, guarda un número reducido de datos técnicos en el almacenamiento local (<code>localStorage</code>) de su propio navegador: su sesión (token de acceso), su preferencia de tema claro/oscuro y qué notificaciones ya vio. Ninguno de estos datos se comparte con terceros ni se usa con fines publicitarios. Puede borrarlos en cualquier momento desde la configuración de su navegador, aunque esto cerrará su sesión. Vea el detalle completo en la Política de Cookies y Almacenamiento Local.</p>

    <h4>8. MENORES DE EDAD</h4>
    <p>Para usuarios menores de edad, el tratamiento de datos se realiza bajo supervisión de sus padres o representantes legales.</p>

    <h4>9. CAMBIOS A ESTA POLÍTICA</h4>
    <p>Esta política puede actualizarse periódicamente. Notificaremos cualquier cambio importante a través de la aplicación.</p>

    <h4>10. CONTACTO</h4>
    <p>Para ejercer sus derechos de privacidad o realizar consultas, contacte a: secretaria.trinitario&#64;gmail.com</p>

    <h4>11. TRANSFERENCIA Y ALMACENAMIENTO DE DATOS</h4>
    <p>Sus datos se almacenan en servidores con medidas de seguridad adecuadas. En caso de requerirse una transferencia a un tercero (por ejemplo, un proveedor de infraestructura), se garantizará un nivel de protección equivalente al aquí descrito.</p>

    <h4>12. MARCO LEGAL (Colombia)</h4>
    <p>Esta política se enmarca en la Ley 1581 de 2012 y el Decreto 1377 de 2013 de protección de datos personales en Colombia. Puede presentar quejas o reclamos ante la Superintendencia de Industria y Comercio si considera que sus derechos no han sido respetados.</p>

    <h4>13. ACTUALIZACIONES DE ESTA POLÍTICA</h4>
    <p>Cualquier cambio sustancial a esta política será notificado a través de la aplicación con al menos 15 días de anticipación a su entrada en vigor.</p>
  `,
})
export class PrivacyContentComponent {}