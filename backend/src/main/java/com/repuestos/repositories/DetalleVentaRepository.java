package com.repuestos.repositories;

import com.repuestos.models.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    
    @Query("SELECT COALESCE(SUM(d.cantidad), 0) FROM DetalleVenta d WHERE d.venta.fecha BETWEEN :inicio AND :fin AND d.venta.estado <> 'ANULADA'")
    Long sumCantidadPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query(value = "SELECT CAST(v.fecha AS date) AS dia, COALESCE(SUM(d.cantidad), 0) AS cantidad "
            + "FROM detalle_venta d JOIN ventas v ON v.id = d.venta_id "
            + "WHERE v.fecha >= :inicio AND v.fecha < :fin AND v.estado <> 'ANULADA' "
            + "GROUP BY CAST(v.fecha AS date)", nativeQuery = true)
    List<Object[]> agregadoProductosPorDia(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
