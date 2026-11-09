package com.repuestos.repositories;

import com.repuestos.models.CargoCuentaCorriente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CargoCuentaCorrienteRepository extends JpaRepository<CargoCuentaCorriente, Long> {

    @EntityGraph(attributePaths = {"usuario"})
    List<CargoCuentaCorriente> findByClienteIdOrderByFechaDescIdDesc(Long clienteId);

    boolean existsByCliente_IdAndUsuario_IdAndMontoAndMotivoAndFechaAfter(
            Long clienteId, Long usuarioId, BigDecimal monto, String motivo,
            LocalDateTime fechaDesde);
}
