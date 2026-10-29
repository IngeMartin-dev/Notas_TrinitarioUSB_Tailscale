package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Entity
@Table(name = "users")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class User implements UserDetails {

    private static final long serialVersionUID = 6795585560628651356L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Column(length = 100)
    @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
    private String surname;

    @Column(name = "username", nullable = false, length = 100, unique = true)
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 3, max = 100, message = "El usuario debe tener entre 3 y 100 caracteres")
    private String username;

    @Column(name = "mail", nullable = false, length = 100, unique = true)
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo inválido")
    @Size(max = 200, message = "El correo no puede superar 200 caracteres")
    private String email;

    @Column(nullable = false)
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 255, message = "La contraseña debe tener al menos 6 caracteres")
    @JsonIgnore
    private String password;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "digital_signature")
    private String digitalSignature;

    private String profilePicture;

    @Column(name = "fcm_token")
    private String fcmToken;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<FcmToken> fcmTokens = new ArrayList<>();

    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "temp_2fa_code")
    private String temp2faCode;

    @Column(name = "temp_2fa_expiry")
    private Long temp2faExpiry;

    private Boolean enable;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "privacy_accepted_at")
    private LocalDateTime privacyAcceptedAt;

    // Rol ADMIN "extra": se puede sumar a cualquier usuario (padre, profesor,
    // etc.) SIN quitarle su rol principal (`role`). Pensado para
    // "Configuración de Año" → recuadro de roles, donde un admin puede darle
    // acceso de administrador a alguien más, sin importar el rol que ya
    // tenga. Se guarda aparte de `role` (que sigue siendo el rol principal)
    // para no romper nada que ya dependa de un único rol por usuario.
    @Column(name = "additional_admin")
    private Boolean additionalAdmin = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDigitalSignature() { return digitalSignature; }
    public void setDigitalSignature(String digitalSignature) { this.digitalSignature = digitalSignature; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }

    public Boolean getTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(Boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    // @JsonIgnore en estos 3: son secretos internos de la verificación en dos
    // pasos (el código temporal y su vencimiento). Antes se serializaban tal
    // cual en CUALQUIER respuesta que incluyera un User (p.ej. /api/auth/me),
    // lo que hubiera dejado ver el código de 2FA vigente en la pestaña de red
    // del navegador -bastaba con inspeccionar la respuesta para saltarse la
    // verificación-. Igual que la contraseña, esto nunca debe llegar al cliente.
    @JsonIgnore
    public String getTwoFactorSecret() { return twoFactorSecret; }
    public void setTwoFactorSecret(String twoFactorSecret) { this.twoFactorSecret = twoFactorSecret; }

    @JsonIgnore
    public String getTemp2faCode() { return temp2faCode; }
    public void setTemp2faCode(String temp2faCode) { this.temp2faCode = temp2faCode; }

    @JsonIgnore
    public Long getTemp2faExpiry() { return temp2faExpiry; }
    public void setTemp2faExpiry(Long temp2faExpiry) { this.temp2faExpiry = temp2faExpiry; }

    public Boolean getEnable() { return enable; }
    public void setEnable(Boolean enable) { this.enable = enable; }

    public LocalDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }

    public LocalDateTime getPrivacyAcceptedAt() { return privacyAcceptedAt; }
    public void setPrivacyAcceptedAt(LocalDateTime privacyAcceptedAt) { this.privacyAcceptedAt = privacyAcceptedAt; }

    public Boolean getAdditionalAdmin() { return additionalAdmin != null && additionalAdmin; }
    public void setAdditionalAdmin(Boolean additionalAdmin) { this.additionalAdmin = additionalAdmin; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public List<FcmToken> getFcmTokens() { return fcmTokens; }
    public void setFcmTokens(List<FcmToken> fcmTokens) { this.fcmTokens = fcmTokens; }

    public String getFullName() {
        return (name != null ? name : "") + " " + (surname != null ? surname : "").trim();
    }

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        }
        // Rol ADMIN "extra" sumado (ver `additionalAdmin`): se agrega la
        // autoridad ROLE_ADMIN además de la del rol principal, sin
        // reemplazarla. Se evita duplicarla si el rol principal YA es ADMIN.
        if (getAdditionalAdmin() && (role == null || !"ADMIN".equalsIgnoreCase(role.getName()))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() { return true; }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() { return true; }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @JsonIgnore
    @Override
    public boolean isEnabled() { return enable != null && enable; }
}