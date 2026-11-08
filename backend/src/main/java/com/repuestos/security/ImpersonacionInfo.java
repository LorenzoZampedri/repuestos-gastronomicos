package com.repuestos.security;

/**
 * Marca la autenticación de una sesión de "ver como": el principal es el usuario
 * observado y el admin que inició la vista queda identificado en {@code adminUsername}.
 */
public record ImpersonacionInfo(String adminUsername, String observadoUsername, String rol) {
}
