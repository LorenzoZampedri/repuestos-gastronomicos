package com.repuestos.repositories;

import com.repuestos.models.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    
    List<Pago> findByVentaId(Long ventaId);
    
    @Query("SELECT p FROM Pago p WHERE p.fecha BETWEEN :inicio AND :fin ORDER BY p.fecha DESC")
    List<Pago> findPagosPorPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
