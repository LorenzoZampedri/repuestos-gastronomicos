package com.repuestos.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lote inalterable de una actualización masiva de precios. El único cambio
 * permitido es pasar de APLICADO a REVERTIDO; no hay endpoints ni métodos de
 * borrado.
 */
@Entity
@Table(name = "lotes_precios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotePrecios {

    public static final String ESTADO_APLICADO = "APLICADO";
    public static final String ESTADO_REVERTIDO = "REVERTIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Snapshot del usuario que aplicó el lote, sin FK. */
    private Long usuarioId;

    @Column(length = 50)
    private String usuarioUsername;

    /** PORCENTAJE | MONTO_USD (String a propósito: sin enums JPA). */
    @Column(nullable = false, length = 20)
    private String tipoAjuste;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal valor;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal redondeo;

    /** Descripción legible del alcance, ej. "Categorías: Gas, Eléctrico". */
    @Column(nullable = false, length = 200)
    private String alcance;

    @Column(length = 200)
    private String nota;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cotizacionUsada;

    @Column(nullable = false)
    private Integer cantidadProductos;

    @Column(nullable = false)
    private Integer cantidadOmitidos;

    /** APLICADO | REVERTIDO (String a propósito: sin enums JPA). */
    @Column(nullable = false, length = 20)
    private String estado;

    @Column(length = 50)
    private String revertidoPorUsername;

    private LocalDateTime fechaReversion;

    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<LotePreciosItem> items = new ArrayList<>();

    public void agregarItem(LotePreciosItem item) {
        item.setLote(this);
        items.add(item);
    }
}
