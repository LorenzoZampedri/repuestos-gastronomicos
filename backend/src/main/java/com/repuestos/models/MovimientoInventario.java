package com.repuestos.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_inventario")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoInventario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TipoMovimiento tipo;
    
    @Column(nullable = false)
    private Integer cantidad;
    
    @Column(length = 200)
    private String motivo;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime fecha;
    
    public enum TipoMovimiento {
        ENTRADA, SALIDA, AJUSTE
    }
}
