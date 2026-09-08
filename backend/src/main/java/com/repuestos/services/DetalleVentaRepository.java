package com.repuestos.repositories;

import com.repuestos.models.DetalleVenta;
import com.repuestos.models.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    
    // This method is actually in PagoRepository, but we keep this for clarity
    default Pago savePago(Pago pago) {
        throw new UnsupportedOperationException("Use PagoRepository.save() instead");
    }
}
