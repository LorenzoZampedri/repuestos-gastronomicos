package com.repuestos.repositories;

import com.repuestos.models.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    
    List<Producto> findByActivoTrue();
    
    List<Producto> findByCategoriaIdAndActivoTrue(Long categoriaId);
    
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND " +
           "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(p.codigoBarras) LIKE LOWER(CONCAT('%', :busqueda, '%')))")
    List<Producto> buscar(@Param("busqueda") String busqueda);
    
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo")
    List<Producto> findProductosStockBajo();
    
    @Query("SELECT COUNT(p) FROM Producto p WHERE p.activo = true")
    Long countProductosActivos();
}
