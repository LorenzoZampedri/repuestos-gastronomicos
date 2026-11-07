package com.repuestos.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de auditoría inmutable. No tiene setters públicos: una vez creado no puede
 * modificarse desde la aplicación. Tampoco existe operación de borrado.
 */
@Entity
@Table(name = "logs_actividad", indexes = {
        @Index(name = "idx_logs_actividad_fecha", columnList = "fecha"),
        @Index(name = "idx_logs_actividad_usuario", columnList = "usuario_username"),
        @Index(name = "idx_logs_actividad_accion", columnList = "accion"),
        @Index(name = "idx_logs_actividad_modulo", columnList = "modulo")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class LogActividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Sin foreign key: el log debe sobrevivir a cambios o bajas del usuario. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_username", length = 50)
    private String usuarioUsername;

    @Column(name = "usuario_rol", length = 20)
    private String usuarioRol;

    @Column(nullable = false, length = 50)
    private String accion;

    @Column(nullable = false, length = 30)
    private String modulo;

    @Column(name = "entidad_tipo", length = 30)
    private String entidadTipo;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(length = 500)
    private String resumen;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(nullable = false, length = 10)
    private String resultado;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}
