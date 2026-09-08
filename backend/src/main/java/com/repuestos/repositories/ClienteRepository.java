package com.repuestos.repositories;

import com.repuestos.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    
    List<Cliente> findByActivoTrue();
    
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(c.telefono) LIKE LOWER(CONCAT('%', :busqueda, '%')))")
    List<Cliente> buscar(@Param("busqueda") String busqueda);
    
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.saldoCorriente > 0")
    List<Cliente> findClientesConDeuda();
}
