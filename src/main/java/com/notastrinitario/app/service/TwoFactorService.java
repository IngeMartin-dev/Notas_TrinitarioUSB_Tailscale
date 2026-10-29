package com.notastrinitario.app.service;

import com.notastrinitario.app.config.AppProperties;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Autenticación de Dos Factores (2FA) por CÓDIGO DE 6 DÍGITOS enviado al
 * correo del usuario (no por app autenticadora/TOTP, para no depender de
 * una librería extra ni de que el usuario instale algo). Reutiliza el
 * mismo JavaMailSender que ya usa ReportCardService para enviar boletines
 * por correo.
 *
 * Los campos temp2faCode / temp2faExpiry ya existían en la entidad User
 * (sin usar hasta ahora); este servicio es el que finalmente los usa.
 */
@Service
public class TwoFactorService {

    private static final long CODE_VALID_MS = 10 * 60 * 1000; // 10 minutos
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @org.springframework.beans.factory.annotation.Value("${app.mail.from:${spring.mail.username:no-reply@localhost}}")
    private String remitente;

    /** Motivo real del último fallo de {@link #generarYEnviarCodigo}, para que
     *  el controlador se lo pueda mostrar a quien depura el problema (por
     *  ejemplo en Ajustes) en vez de solo un "no se pudo enviar" genérico. */
    private volatile String ultimoError;

    public String getUltimoError() {
        return ultimoError;
    }

    public TwoFactorService(UserRepository userRepository, JavaMailSender mailSender, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    /** Genera un código de 6 dígitos, lo guarda (con expiración) y lo envía
     *  por correo al usuario. Devuelve false si algo falla: al enviar el
     *  correo (por ejemplo si el SMTP no está bien configurado), O al
     *  guardar (por ejemplo si el usuario tiene algún dato antiguo que ya
     *  no cumple las validaciones del formulario -como un correo con
     *  formato inválido guardado desde antes-; Hibernate revalida TODOS los
     *  campos del usuario en cada guardado, no solo los que cambian). Antes
     *  ese guardado no estaba protegido y una validación fallida tumbaba la
     *  petición entera con un error 500 sin explicación. */
    public boolean generarYEnviarCodigo(User user, String asunto, String mensajeIntro) {
        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setTemp2faCode(codigo);
        user.setTemp2faExpiry(Instant.now().toEpochMilli() + CODE_VALID_MS);

        try {
            userRepository.save(user);
        } catch (Exception e) {
            // Hibernate/Spring suelen envolver la violación real (@NotBlank,
            // @Email, @Size...) dentro de TransactionSystemException, cuyo
            // mensaje ("Could not commit JPA transaction") no dice nada útil
            // por sí solo. Bajamos por la cadena de causas hasta encontrarla.
            ultimoError = "No se pudo guardar el usuario: " + describirCausaRaiz(e);
            System.err.println("[2FA] El usuario " + user.getId() + " tiene datos inválidos que impiden guardarlo: "
                    + describirCausaRaiz(e));
            return false;
        }

        try {
            String nombreInstitucion = appProperties.getInstitution().getName();

            MimeMessage message = mailSender.createMimeMessage();
            // MULTIPART_MODE_MIXED_RELATED: permite imagen inline (el logo) +
            // una versión en texto plano como respaldo. Tener también texto
            // plano (no solo HTML) es una de las señales que los filtros de
            // spam usan para confiar más en un correo.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setTo(user.getEmail());
            // Nombre visible del remitente en vez de solo la dirección
            // "pelada" — también ayuda un poco a que no se vea como spam.
            helper.setFrom(remitente, nombreInstitucion);
            helper.setSubject("[" + nombreInstitucion + "] " + asunto);

            String textoPlano = nombreInstitucion + "\n\n"
                    + mensajeIntro + "\n\n"
                    + "Tu código es: " + codigo + "\n\n"
                    + "Este código vence en 10 minutos. Si no fuiste tú quien lo solicitó, ignora este correo.";
            helper.setText(textoPlano, construirHtml(nombreInstitucion, asunto, mensajeIntro, codigo));

            try {
                helper.addInline("logoColegio", new ClassPathResource("Logo Colegio.png"));
            } catch (Exception logoEx) {
                // Si el logo no se encuentra, se envía igual el correo sin
                // imagen en vez de bloquear el envío del código.
                System.err.println("[2FA] No se pudo adjuntar el logo (se envía el correo sin él): " + logoEx.getMessage());
            }

            mailSender.send(message);
            ultimoError = null;
            return true;
        } catch (Exception e) {
            ultimoError = "No se pudo enviar el correo: " + describirCausaRaiz(e);
            System.err.println("[2FA] No se pudo enviar el código por correo a " + user.getEmail() + ": " + describirCausaRaiz(e));
            return false;
        }
    }

    /** Arma el cuerpo HTML del correo, con el logo del colegio (referenciado
     *  por el cid "logoColegio", agregado como inline en el mensaje) y un
     *  diseño más cuidado que el bloque de texto plano de antes. */
    private String construirHtml(String nombreInstitucion, String asunto, String mensajeIntro, String codigo) {
        return "<div style=\"background:#f4f6fb;padding:32px 12px;font-family:Arial,Helvetica,sans-serif\">"
            + "<div style=\"max-width:480px;margin:auto;background:#ffffff;border-radius:12px;overflow:hidden;"
            + "box-shadow:0 2px 10px rgba(0,0,0,0.06)\">"

            // Encabezado con logo
            + "<div style=\"background:#1b6aeb;padding:24px;text-align:center\">"
            + "<img src=\"cid:logoColegio\" alt=\"" + nombreInstitucion + "\" "
            + "style=\"height:56px;width:auto;display:inline-block;background:#fff;border-radius:8px;padding:6px\">"
            + "</div>"

            // Cuerpo
            + "<div style=\"padding:32px 28px\">"
            + "<h2 style=\"color:#1b2b4b;margin:0 0 4px;font-size:20px\">" + asunto + "</h2>"
            + "<p style=\"color:#4a5568;font-size:14px;margin:0 0 20px\">" + nombreInstitucion + "</p>"
            + "<p style=\"color:#333;font-size:15px;line-height:1.5\">" + mensajeIntro + "</p>"

            + "<div style=\"background:#f1f5fb;border-radius:10px;padding:18px;text-align:center;margin:24px 0\">"
            + "<span style=\"font-size:34px;font-weight:bold;letter-spacing:8px;color:#1b6aeb\">" + codigo + "</span>"
            + "</div>"

            + "<p style=\"color:#666;font-size:13px;line-height:1.5\">"
            + "Este código vence en <strong>10 minutos</strong>. "
            + "Si no fuiste tú quien lo solicitó, simplemente ignora este correo; tu cuenta sigue segura.</p>"
            + "</div>"

            // Pie de página
            + "<div style=\"background:#f8f9fb;padding:16px 28px;border-top:1px solid #eef0f4;"
            + "text-align:center;color:#9aa2b1;font-size:12px\">"
            + "Este es un mensaje automático de " + nombreInstitucion + ", por favor no respondas a este correo."
            + "</div>"

            + "</div>"
            + "</div>";
    }

    /** Baja por la cadena de "causas" de una excepción hasta encontrar la
     *  violación de validación concreta (ConstraintViolationException), o,
     *  si no la encuentra, el mensaje más profundo disponible. */
    private String describirCausaRaiz(Throwable e) {
        Throwable actual = e;
        while (actual != null) {
            if (actual instanceof jakarta.validation.ConstraintViolationException cve) {
                StringBuilder detalle = new StringBuilder();
                cve.getConstraintViolations().forEach(v ->
                    detalle.append(v.getPropertyPath()).append(" ").append(v.getMessage()).append("; "));
                return detalle.toString();
            }
            if (actual.getCause() == null || actual.getCause() == actual) {
                return actual.getClass().getSimpleName() + ": " + actual.getMessage();
            }
            actual = actual.getCause();
        }
        return e.getMessage();
    }

    /** Verifica el código ingresado contra el guardado, revisando expiración.
     *  Si es válido, lo limpia (un código no se puede reutilizar). */
    public boolean verificarCodigo(User user, String codigoIngresado) {
        String codigoGuardado = user.getTemp2faCode();
        Long expiry = user.getTemp2faExpiry();
        if (codigoGuardado == null || expiry == null) return false;
        if (Instant.now().toEpochMilli() > expiry) return false;
        if (!codigoGuardado.equals(codigoIngresado)) return false;

        // Un código usado no debe servir dos veces.
        user.setTemp2faCode(null);
        user.setTemp2faExpiry(null);
        try {
            userRepository.save(user);
        } catch (Exception e) {
            System.err.println("[2FA] No se pudo limpiar el código usado para el usuario "
                    + user.getId() + ": " + e.getMessage());
            // El código ya fue validado como correcto; no bloqueamos el login
            // por un problema al limpiarlo (en el peor caso, seguirá válido
            // hasta que expire en 10 minutos).
        }
        return true;
    }
}