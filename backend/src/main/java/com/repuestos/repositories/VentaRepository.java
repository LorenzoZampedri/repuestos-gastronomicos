package com.repuestos.repositories;

import com.repuestos.models.Venta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    
    @EntityGraph(attributePaths = {"cliente", "usuario"})
    List<Venta> findByFechaBetweenAndEstadoNot(LocalDateTime inicio, LocalDateTime fin, Venta.EstadoVenta estado);
    
    @EntityGraph(attributePaths = {"cliente", "usuario"})
    List<Venta> findByClienteId(Long clienteId);
    
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin AND v.estado <> 'ANULADA'")
    BigDecimal sumVentasPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
    
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.estado <> 'ANULADA'")
    BigDecimal sumTotalVentas();
    
    @EntityGraph(attributePaths = {"cliente", "usuario"})
    @Query("SELECT v FROM Venta v WHERE v.estado IN ('PENDIENTE', 'PARCIAL') ORDER BY v.fecha DESC")
    List<Venta> findVentasPendientes();
}
