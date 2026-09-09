package com.repuestos.services;

import com.repuestos.models.*;
import com.repuestos.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {
    
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final PagoRepository pagoRepository;
    private final ProductoService productoService;
    private final ClienteRepository clienteRepository;
    
    public List<Venta> listarVentas(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.findByFechaBetweenAndEstadoNot(inicio, fin, Venta.EstadoVenta.ANULADA);
    }
    
    public Venta buscarPorId(Long id) {
        return ventaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
    }
    
    public List<Venta> ventasPorCliente(Long clienteId) {
        return ventaRepository.findByClienteId(clienteId);
    }
    
    public List<Venta> ventasPendientes() {
        return ventaRepository.findVentasPendientes();
    }
    
    @Transactional
    public Venta crearVenta(Venta venta, List<DetalleVenta> detalles, Long clienteId) {
        // Validar stock
        for (DetalleVenta detalle : detalles) {
            Producto producto = productoService.buscarPorId(detalle.getProducto().getId());
            if (producto.getStockActual() < detalle.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para: " + producto.getNombre());
            }
        }
        
        // Calcular total
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta detalle : detalles) {
            BigDecimal subtotal = detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad()));
            detalle.setSubtotal(subtotal);
            total = total.add(subtotal);
        }
        
        // Configurar venta
        venta.setTotal(total);
        venta.setFecha(LocalDateTime.now());
        
        if (clienteId != null) {
            Cliente cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            venta.setCliente(cliente);
        }
        
        Venta ventaGuardada = ventaRepository.save(venta);
        
        // Guardar detalles y descontar stock
        for (DetalleVenta detalle : detalles) {
            detalle.setVenta(ventaGuardada);
            detalleVentaRepository.save(detalle);
            productoService.descontarStock(detalle.getProducto().getId(), detalle.getCantidad());
        }
        
        // Si tiene cliente, acumular deuda (la venta no está pagada al crearse)
        if (venta.getCliente() != null) {
            Cliente cliente = venta.getCliente();
            cliente.setSaldoCorriente(cliente.getSaldoCorriente().add(total));
            clienteRepository.save(cliente);
        }
        
        return ventaGuardada;
    }
    
    @Transactional
    public Pago registrarPago(Long ventaId, Pago pago) {
        Venta venta = buscarPorId(ventaId);
        pago.setVenta(venta);
        pago.setFecha(LocalDateTime.now());
        
        Pago pagoGuardado = pagoRepository.save(pago);
        
        // Actualizar estado de la venta
        BigDecimal montoPagado = venta.getMontoPagado().add(pago.getMonto());
        if (montoPagado.compareTo(venta.getTotal()) >= 0) {
            venta.setEstado(Venta.EstadoVenta.PAGADA);
        } else {
            venta.setEstado(Venta.EstadoVenta.PARCIAL);
        }
        ventaRepository.save(venta);
        
        // Actualizar saldo del cliente
        if (venta.getCliente() != null) {
            Cliente cliente = venta.getCliente();
                BigDecimal nuevoSaldo = cliente.getSaldoCorriente().subtract(pago.getMonto());
            cliente.setSaldoCorriente(nuevoSaldo);
            clienteRepository.save(cliente);
        }
        
        return pagoGuardado;
    }
    
    @Transactional
    public void anularVenta(Long ventaId) {
        Venta venta = buscarPorId(ventaId);
        
        // Devolver stock
        for (DetalleVenta detalle : venta.getDetalles()) {
            productoService.agregarStock(detalle.getProducto().getId(), detalle.getCantidad());
        }
        
        venta.setEstado(Venta.EstadoVenta.ANULADA);
        ventaRepository.save(venta);
    }
    
    public BigDecimal ventasDelDia() {
        LocalDateTime inicio = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime fin = LocalDateTime.now().with(LocalTime.MAX);
        return ventaRepository.sumVentasPorPeriodo(inicio, fin);
    }
    
    public BigDecimal totalVentas() {
        return ventaRepository.sumTotalVentas();
    }
}
