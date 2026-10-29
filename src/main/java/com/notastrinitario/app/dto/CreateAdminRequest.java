package com.notastrinitario.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un administrador desde Configuración de Año.
 * El rol (ADMIN) y el estado activo los fija SIEMPRE el servidor.
 */
public record CreateAdminRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 255) String password,
        @Size(max = 100) String name,
        @Size(max = 100) String surname) {
}
