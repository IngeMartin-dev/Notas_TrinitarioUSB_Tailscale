package com.notastrinitario.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos personales editables (por el propio usuario o por un ADMIN).
 * NO incluye rol, enable, additionalAdmin, 2FA ni contraseña.
 */
public record UserProfileUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String name,
        @Size(max = 100) String surname,
        @NotBlank(message = "El nombre de usuario es obligatorio") @Size(min = 3, max = 100) String username,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") @Size(max = 200) String email) {
}