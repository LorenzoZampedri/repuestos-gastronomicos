package com.repuestos.repositories;

import com.repuestos.models.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    
    List<Producto> findByActivoTrueOrderByIdAsc();
    
    List<Producto> findByCategoriaIdAndActivoTrueOrderByIdAsc(Long categoriaId);

    List<Producto> findByCategoriaIdInAndActivoTrueOrderByIdAsc(List<Long> categoriaIds);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.id IN :ids ORDER BY p.id ASC")
    List<Producto> findActivosByIds(@Param("ids") List<Long> ids);

    /** Bloqueo pesimista de escritura para actualizaciones masivas de precios. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Producto> findByIdsParaActualizar(@Param("ids") List<Long> ids);
    
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND " +
           "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(p.codigoBarras) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY p.id ASC")
    List<Producto> buscar(@Param("busqueda") String busqueda);
    
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo ORDER BY p.id ASC")
    List<Producto> findProductosStockBajo();
    
    @Query("SELECT COUNT(p) FROM Producto p WHERE p.activo = true")
    Long countProductosActivos();
}
