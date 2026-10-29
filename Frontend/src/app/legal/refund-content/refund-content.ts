import { Component } from '@angular/core';

/**
 * Política de Reembolsos.
 *
 * Se revisó el código del backend y del frontend en busca de integraciones
 * de pago (Stripe, PayPal, pasarelas locales, etc.) y no existe ninguna:
 * la plataforma no procesa cobros ni pagos en línea. Por eso este texto
 * NO inventa condiciones de pago o reembolso que no existen; deja
 * constancia del estado actual y qué pasaría si eso cambiara.
 */
@Component({
  selector: 'app-refund-content',
  standalone: true,
  template: `
    <h4>1. ESTADO ACTUAL DEL SERVICIO</h4>
    <p>Notas Trinitario es una plataforma de uso interno del Colegio Trinitario. Actualmente, la aplicación no procesa pagos, cobros ni suscripciones en línea dentro de la app: no hay pasarela de pago integrada en este momento.</p>

    <h4>2. SI SU ACCESO ESTÁ LIGADO A UN CONTRATO O MATRÍCULA</h4>
    <p>Si el acceso a esta plataforma forma parte de los servicios educativos contratados con el Colegio Trinitario (por ejemplo, a través de la matrícula o pensión), cualquier reembolso relacionado con esos pagos se rige por las políticas administrativas y financieras del colegio, no por esta aplicación. Le recomendamos consultar directamente con la administración del Colegio Trinitario para ese tipo de solicitudes.</p>

    <h4>3. SI EN EL FUTURO SE HABILITAN PAGOS EN LA PLATAFORMA</h4>
    <p>Si en el futuro se incorpora algún cobro directo dentro de la aplicación (por ejemplo, servicios adicionales), esta política se actualizará antes de habilitar esa función, especificando plazos, condiciones y el procedimiento para solicitar un reembolso.</p>

    <h4>4. CONTACTO</h4>
    <p>Para consultas sobre pagos o reembolsos relacionados con el colegio, contacte a secretaria.trinitario&#64;gmail.com</p>
  `,
})
export class RefundContentComponent {}