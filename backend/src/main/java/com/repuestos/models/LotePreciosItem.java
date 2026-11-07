package com.repuestos.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Snapshot por producto de una actualización masiva de precios (antes/después). */
@Entity
@Table(name = "lotes_precios_items",
        indexes = @Index(name = "idx_lotes_precios_items_lote", columnList = "lote_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotePreciosItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LotePrecios lote;

    /** Id del producto al momento de aplicar, sin FK: es un snapshot. */
    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false, length = 200)
    private String productoNombre;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUsdAntes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUsdDespues;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioArsAntes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precioArsDespues;
}
