package com.repuestos.repositories;

import com.repuestos.models.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    
    List<Cliente> findByActivoTrueOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.id = :id")
    Optional<Cliente> findByIdParaActualizar(@Param("id") Long id);
    
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(c.telefono) LIKE LOWER(CONCAT('%', :busqueda, '%'))) ORDER BY c.id ASC")
    List<Cliente> buscar(@Param("busqueda") String busqueda);
    
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND c.saldoCorriente > 0 ORDER BY c.id ASC")
    List<Cliente> findClientesConDeuda();
    
    long countByFotoUrl(String fotoUrl);
}
