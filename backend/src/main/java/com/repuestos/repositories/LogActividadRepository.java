package com.repuestos.repositories;

import com.repuestos.models.LogActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LogActividadRepository
        extends JpaRepository<LogActividad, Long>, JpaSpecificationExecutor<LogActividad> {
}
