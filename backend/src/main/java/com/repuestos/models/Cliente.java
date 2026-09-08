package com.repuestos.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 200)
    private String nombre;
    
    @Column(length = 20)
    private String telefono;
    
    @Column(length = 100)
    private String email;
    
    @Column(columnDefinition = "TEXT")
    private String direccion;
    
    @Column(length = 20)
    private String cuit;
    
    @Column(length = 20)
    @Builder.Default
    private String condicionIva = "CONSUMIDOR_FINAL";
    
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal saldoCorriente = BigDecimal.ZERO;
    
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    
    @Builder.Default
    private Boolean activo = true;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
