package com.repuestos.services;

import com.repuestos.models.*;
import com.repuestos.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {

    public static final int UMBRAL_DESCUENTO_CANTIDAD = 10;
    public static final BigDecimal PCT_DESCUENTO = new BigDecimal("0.85");

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
        // Validar stock y aplicar descuento por volumen (15% si cantidad > 10)
        for (DetalleVenta detalle : detalles) {
            Producto producto = productoService.buscarPorId(detalle.getProducto().getId());
            if (producto.getStockActual() < detalle.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para: " + producto.getNombre());
            }
            BigDecimal precioUnitario = producto.getPrecio();
            if (detalle.getCantidad() > UMBRAL_DESCUENTO_CANTIDAD) {
                precioUnitario = precioUnitario.multiply(PCT_DESCUENTO).setScale(2, RoundingMode.HALF_UP);
            }
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setProducto(producto);
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
            BigDecimal saldoActual = cliente.getSaldoCorriente() == null
                    ? BigDecimal.ZERO
                    : cliente.getSaldoCorriente();
            cliente.setSaldoCorriente(saldoActual.add(total));
            clienteRepository.save(cliente);
        }
        
        return ventaGuardada;
    }
    
    @Transactional
    public Pago registrarPago(Long ventaId, Pago pago) {
        Venta venta = buscarPorId(ventaId);
        pago.setVenta(venta);
        pago.setCliente(venta.getCliente());
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
    
    public BigDecimal ventasPorPeriodo(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.sumVentasPorPeriodo(inicio, fin);
    }
    
    public Long cantidadVentasPorPeriodo(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.countVentasPorPeriodo(inicio, fin);
    }
    
    public Long cantidadProductosPorPeriodo(LocalDateTime inicio, LocalDateTime fin) {
        return detalleVentaRepository.sumCantidadPorPeriodo(inicio, fin);
    }
    
    public BigDecimal totalVentas() {
        return ventaRepository.sumTotalVentas();
    }
}
