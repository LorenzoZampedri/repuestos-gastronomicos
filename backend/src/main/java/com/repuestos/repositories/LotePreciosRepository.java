package com.repuestos.repositories;

import com.repuestos.models.LotePrecios;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LotePreciosRepository extends JpaRepository<LotePrecios, Long> {

    /** Bloqueo pesimista para evitar doble reversión del mismo lote. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LotePrecios l WHERE l.id = :id")
    Optional<LotePrecios> findByIdParaActualizar(@Param("id") Long id);
}
