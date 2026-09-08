package com.repuestos.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pago {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
    
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MetodoPago metodoPago;
    
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String moneda = "ARS";
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime fecha;
    
    @Column(length = 200)
    private String referencia;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    
    public enum MetodoPago {
        EFECTIVO, TARJETA, TRANSFERENCIA, OTRO
    }
}
