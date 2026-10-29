package com.repuestos.repositories;

import com.repuestos.models.ConfiguracionHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguracionHistorialRepository extends JpaRepository<ConfiguracionHistorial, Long> {
    List<ConfiguracionHistorial> findByClaveOrderByFechaDesc(String clave);
}
