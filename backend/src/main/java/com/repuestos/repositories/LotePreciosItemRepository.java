package com.repuestos.repositories;

import com.repuestos.models.LotePreciosItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LotePreciosItemRepository extends JpaRepository<LotePreciosItem, Long> {

    List<LotePreciosItem> findByLoteIdOrderByIdAsc(Long loteId);
}
