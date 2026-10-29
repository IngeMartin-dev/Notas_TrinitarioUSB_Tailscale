package com.notastrinitario.app.dto;

import jakarta.validation.constraints.NotNull;

/** Solo ADMIN: activar/desactivar una cuenta. */
public record AdminUserEnableRequest(@NotNull Boolean enable) {
}