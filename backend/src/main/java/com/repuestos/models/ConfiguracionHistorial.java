package com.repuestos.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion_historial")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String clave;

    @Column(columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(length = 100)
    private String usuario;

    @CreationTimestamp
    private LocalDateTime fecha;
}
