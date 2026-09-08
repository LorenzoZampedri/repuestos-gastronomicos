package com.repuestos.repositories;

import com.repuestos.models.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    
    List<MovimientoInventario> findByProductoIdOrderByFechaDesc(Long productoId);
    
    @Query("SELECT m FROM MovimientoInventario m WHERE m.fecha BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    List<MovimientoInventario> findMovimientosPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
