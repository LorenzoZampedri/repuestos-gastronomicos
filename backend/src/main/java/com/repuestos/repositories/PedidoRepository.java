package com.repuestos.repositories;

import com.repuestos.models.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = {"cliente", "usuario", "detalles", "detalles.producto",
            "venta", "resueltoPor"})
    List<Pedido> findByClienteIdOrderByFechaDesc(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "usuario", "detalles", "detalles.producto",
            "venta", "resueltoPor"})
    Optional<Pedido> findByIdAndClienteId(Long id, Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "usuario", "detalles", "detalles.producto",
            "venta", "resueltoPor"})
    List<Pedido> findByEstadoOrderByFechaAsc(Pedido.EstadoPedido estado);

    @EntityGraph(attributePaths = {"cliente", "usuario", "detalles", "detalles.producto",
            "venta", "resueltoPor"})
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdConRelaciones(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdParaActualizar(@Param("id") Long id);

    long countByClienteIdAndEstado(Long clienteId, Pedido.EstadoPedido estado);

    long countByEstado(Pedido.EstadoPedido estado);

    @Modifying
    @Query("UPDATE Pedido p SET p.cliente = :clienteDestino "
            + "WHERE p.usuario.id = :usuarioId AND p.cliente.id = :clienteOrigen")
    int reasignarClienteDeUsuario(@Param("usuarioId") Long usuarioId,
                                  @Param("clienteOrigen") Long clienteOrigen,
                                  @Param("clienteDestino") com.repuestos.models.Cliente clienteDestino);
}
