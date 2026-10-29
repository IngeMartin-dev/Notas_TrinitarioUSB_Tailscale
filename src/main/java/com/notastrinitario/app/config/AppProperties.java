package com.notastrinitario.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Firebase firebase = new Firebase();
    private Institution institution = new Institution();
    private Ai ai = new Ai();
    private Security security = new Security();

    public static class Jwt {
        // IMPORTANTE (seguridad): NUNCA pongas aquí un valor por defecto.
        // Este secreto firma todos los tokens de sesión de la app; si queda
        // hardcodeado y el repositorio es público, cualquiera puede
        // fabricar un token válido (incluso de ADMIN) sin conocer ninguna
        // contraseña. Debe llegar SIEMPRE desde una variable de entorno:
        //   APP_JWT_SECRET=<valor aleatorio largo, ej: openssl rand -hex 64>
        // JwtUtil valida en el arranque que este valor no sea nulo/vacío ni
        // el antiguo valor filtrado, y la aplicación no arrancará si falta.
        private String secret;
        private long expiration = 604800000;
        private long refreshExpiration = 2592000000L;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpiration() { return expiration; }
        public void setExpiration(long expiration) { this.expiration = expiration; }
        public long getRefreshExpiration() { return refreshExpiration; }
        public void setRefreshExpiration(long refreshExpiration) { this.refreshExpiration = refreshExpiration; }
    }

    public static class Firebase {
        private boolean enabled = false;
        private String serviceAccountFile = "firebase-service-account.json";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServiceAccountFile() { return serviceAccountFile; }
        public void setServiceAccountFile(String serviceAccountFile) { this.serviceAccountFile = serviceAccountFile; }
    }

    public static class Institution {
        private String name = "Corporacion Colegio Trinitario";
        private String address = "Cra 81C #22-124, San Fernando, Cartagena de Indias, Provincia de Cartagena, Bolivar, Colombia";
        private String phone = "56539572";
        private String logo = "/img/logo.png";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getLogo() { return logo; }
        public void setLogo(String logo) { this.logo = logo; }
    }

    public static class Ai {
        private String mistralApiKey;
        private String mistralModel = "codestral-2508";
        private String mistralUrl = "https://api.mistral.ai/v1/chat/completions";

        public String getMistralApiKey() { return mistralApiKey; }
        public void setMistralApiKey(String mistralApiKey) { this.mistralApiKey = mistralApiKey; }
        public String getMistralModel() { return mistralModel; }
        public void setMistralModel(String mistralModel) { this.mistralModel = mistralModel; }
        public String getMistralUrl() { return mistralUrl; }
        public void setMistralUrl(String mistralUrl) { this.mistralUrl = mistralUrl; }
    }

    public static class Security {
        // IMPORTANTE (seguridad): X-Forwarded-For lo puede escribir CUALQUIERA
        // que le hable directo al backend (curl, Postman, un bot), no solo un
        // proxy. Si se confía en ese encabezado sin tener un proxy real
        // delante que lo sobrescriba, un atacante manda un valor distinto en
        // cada petición y el RateLimitFilter y el BruteForceProtection nunca
        // lo bloquean (cada "IP" falsa tiene su propio cupo nuevo): el
        // rate-limit queda completamente evadido, que es justo lo que se
        // supone que evita un ataque de denegación de servicio.
        //
        // Poner esto en true SOLO si el backend está detrás de un proxy de
        // confianza (nginx, el DevTunnel, un load balancer) que vos controlás
        // y que SIEMPRE reemplaza/limpia el X-Forwarded-For entrante antes de
        // reenviar la petición. Variable de entorno: APP_SECURITY_TRUST_PROXY_HEADERS=true
        private boolean trustProxyHeaders = false;

        // Orígenes EXTRA autorizados por CORS (además de localhost), separados
        // por coma. Variable de entorno: APP_SECURITY_ALLOWED_ORIGINS
        // Ej: https://notas.tucolegio.edu.co  (opcional, cuando tengas dominio)
        private java.util.List<String> allowedOrigins = new java.util.ArrayList<>();

        public java.util.List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(java.util.List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

        public boolean isTrustProxyHeaders() { return trustProxyHeaders; }
        public void setTrustProxyHeaders(boolean trustProxyHeaders) { this.trustProxyHeaders = trustProxyHeaders; }
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Firebase getFirebase() { return firebase; }
    public void setFirebase(Firebase firebase) { this.firebase = firebase; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public Ai getAi() { return ai; }
    public void setAi(Ai ai) { this.ai = ai; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
}