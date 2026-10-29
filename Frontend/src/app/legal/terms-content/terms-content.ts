import { Component } from '@angular/core';

/**
 * Contenido de los Términos y Condiciones.
 *
 * Se extrajo de settings.html a un componente propio para que tanto el
 * modal de Configuración como el bloqueo de consentimiento obligatorio
 * (ver ConsentGate) muestren EXACTAMENTE el mismo texto. Antes el texto
 * vivía solo en settings.html: si alguien lo editaba ahí, cualquier otra
 * copia (como la que se agrega en este cambio) se habría desincronizado.
 */
@Component({
  selector: 'app-terms-content',
  standalone: true,
  template: `
    <h4>1. ACEPTACIÓN DE TÉRMINOS</h4>
    <p>Al acceder y utilizar la aplicación "Notas Trinitario", usted acepta estar sujeto a estos términos y condiciones. Si no está de acuerdo con alguno de estos términos, no debe utilizar esta aplicación.</p>

    <h4>2. DESCRIPCIÓN DEL SERVICIO</h4>
    <p>Notas Trinitario es una plataforma educativa desarrollada exclusivamente para el Colegio Trinitario, que permite a estudiantes y docentes gestionar calificaciones, reportes académicos y comunicación institucional.</p>

    <h4>3. USO AUTORIZADO</h4>
    <p>Esta aplicación está destinada exclusivamente a:</p>
    <ul>
      <li>Estudiantes matriculados en el Colegio Trinitario</li>
      <li>Docentes y personal administrativo del colegio</li>
      <li>Padres o representantes legales de estudiantes</li>
    </ul>
    <p>El uso de la aplicación para fines distintos a los educativos está prohibido.</p>

    <h4>4. CUENTA DE USUARIO</h4>
    <p>Cada usuario es responsable de mantener la confidencialidad de su cuenta y contraseña. El Colegio Trinitario no se hace responsable por cualquier pérdida o daño derivado del incumplimiento de esta obligación.</p>

    <h4>5. PRIVACIDAD Y PROTECCIÓN DE DATOS</h4>
    <p>La información personal recopilada está sujeta a nuestra Política de Privacidad. Al usar esta aplicación, usted consiente el tratamiento de sus datos conforme a dicha política.</p>

    <h4>6. PROPIEDAD INTELECTUAL</h4>
    <p>Todo el contenido, diseño, gráficos y código de la aplicación son propiedad del Colegio Trinitario y están protegidos por las leyes de propiedad intelectual vigentes.</p>

    <h4>7. LIMITACIÓN DE RESPONSABILIDAD</h4>
    <p>El Colegio Trinitario no garantiza que la aplicación esté libre de errores o disponible de manera continua. El uso de la aplicación es bajo responsabilidad del usuario.</p>

    <h4>8. MODIFICACIONES</h4>
    <p>El Colegio Trinitario se reserva el derecho de modificar estos términos en cualquier momento. Las modificaciones entrarán en vigor desde su publicación en la aplicación.</p>

    <h4>9. CONTACTO</h4>
    <p>Para consultas sobre estos términos, contacte a secretaria.trinitario&#64;gmail.com</p>

    <h4>10. CONDUCTA Y USO RESPONSABLE</h4>
    <p>Los usuarios se comprometen a hacer un uso respetuoso de los canales de comunicación de la plataforma (chat, comentarios). Está prohibido el envío de contenido ofensivo, discriminatorio o que vulnere los derechos de otros miembros de la comunidad educativa.</p>

    <h4>11. DISPONIBILIDAD DEL SERVICIO</h4>
    <p>La plataforma puede presentar interrupciones programadas por mantenimiento, actualizaciones o causas de fuerza mayor. Se notificará con antelación siempre que sea posible.</p>

    <h4>12. SUSPENSIÓN O TERMINACIÓN DE CUENTA</h4>
    <p>El Colegio Trinitario podrá suspender o cancelar el acceso de un usuario que incumpla estos términos, sin perjuicio de las acciones disciplinarias o legales a que haya lugar.</p>

    <h4>13. LEY APLICABLE</h4>
    <p>Estos términos se rigen por las leyes de la República de Colombia. Cualquier controversia se resolverá ante las autoridades competentes de Cartagena de Indias, Bolívar.</p>

    <h4>14. VIGENCIA</h4>
    <p>Estos términos entran en vigor desde su publicación y permanecerán vigentes mientras el usuario mantenga acceso activo a la plataforma.</p>
  `,
})
export class TermsContentComponent {}