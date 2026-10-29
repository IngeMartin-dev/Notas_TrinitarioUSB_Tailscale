package com.notastrinitario.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Único contenido que un cliente puede mandar al auto-registrarse.
 * Rol, enable, additionalAdmin y 2FA los fija SIEMPRE el servidor.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(min = 6, max = 255) String password,
        @Size(max = 100) String name,
        @Size(max = 100) String surname) {
}