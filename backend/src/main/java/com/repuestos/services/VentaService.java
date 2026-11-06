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
import java.util.ArrayList;
import java.util.Comparator;
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
    
    /**
     * Crea la venta y, si se indica un método de pago distinto de cuenta corriente,
     * registra el pago por el total calculado por el backend dentro de la misma
     * transacción. Así el monto cobrado nunca depende del total calculado en el frontend.
     */
    @Transactional
    public Venta crearVentaConPago(Venta venta, List<DetalleVenta> detalles,
                                   Long clienteId, String metodoPago) {
        Venta ventaGuardada = crearVenta(venta, detalles, clienteId);
        if (metodoPago != null
                && !metodoPago.isBlank()
                && !"CUENTA_CORRIENTE".equalsIgnoreCase(metodoPago)) {
            Pago pago = Pago.builder()
                    .monto(ventaGuardada.getTotal())
                    .metodoPago(parseMetodoPago(metodoPago))
                    .moneda("ARS")
                    .referencia("Pago de venta")
                    .usuario(ventaGuardada.getUsuario())
                    .build();
            registrarPago(ventaGuardada.getId(), pago);
        }
        return buscarPorId(ventaGuardada.getId());
    }

    @Transactional
    public Pago registrarPago(Long ventaId, Pago pago) {
        Venta venta = buscarPorId(ventaId);
        // Normalizar el monto a escala 2 ANTES de cualquier cálculo
        if (pago.getMonto() != null) {
            pago.setMonto(pago.getMonto().setScale(2, RoundingMode.HALF_UP));
        }
        // Valida monto, estado de la venta y saldo pendiente; actualiza el estado (PARCIAL/PAGADA).
        // El saldo pagado se calcula con una query ANTES de persistir el pago nuevo, para no
        // contar dos veces el pago recién insertado (bug de doble conteo).
        aplicarPagoAVenta(venta, pago.getMonto());
        pago.setVenta(venta);
        pago.setCliente(venta.getCliente());
        if (pago.getUsuario() == null) {
            pago.setUsuario(venta.getUsuario());
        }
        pago.setFecha(LocalDateTime.now());

        Pago pagoGuardado = pagoRepository.save(pago);

        // Actualizar saldo del cliente exactamente una vez por el monto pagado
        if (venta.getCliente() != null) {
            Cliente cliente = venta.getCliente();
            BigDecimal saldoActual = cliente.getSaldoCorriente() == null
                    ? BigDecimal.ZERO
                    : cliente.getSaldoCorriente();
            cliente.setSaldoCorriente(saldoActual.subtract(pago.getMonto()));
            clienteRepository.save(cliente);
        }

        return pagoGuardado;
    }

    /**
     * Registra un pago de cuenta corriente: reparte el monto entre las ventas abiertas del
     * cliente (PENDIENTE/PARCIAL) de más antigua a más reciente. El sobrante (deuda manual
     * cargada con tipo "sumar" o un sobrepago) se registra como pago sin venta. El
     * saldoCorriente del cliente se descuenta exactamente una vez por el monto total del
     * pago; si el pago supera la deuda, el saldo queda negativo y representa un saldo a
     * favor del cliente.
     */
    @Transactional
    public void registrarPagoCuentaCorriente(Long clienteId, BigDecimal monto, String metodoPago) {
        registrarPagoCuentaCorriente(clienteId, monto, metodoPago, null);
    }

    @Transactional
    public void registrarPagoCuentaCorriente(Long clienteId, BigDecimal monto, String metodoPago,
                                             Usuario usuario) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        BigDecimal montoPago = monto.setScale(2, RoundingMode.HALF_UP);

        // Los datos viejos pueden tener ventas sobrepagadas (saldo pendiente negativo)
        // junto con otras ventas impagas del mismo cliente. Antes de imputar el pago,
        // movemos esos excedentes a las ventas abiertas para que nunca queden créditos
        // "atrapados" en ventas ya pagadas.
        reasignarExcedentes(clienteId);

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        BigDecimal saldoActual = cliente.getSaldoCorriente() == null
                ? BigDecimal.ZERO
                : cliente.getSaldoCorriente();

        Pago.MetodoPago metodo = parseMetodoPago(metodoPago);
        BigDecimal restante = montoPago;

        List<Venta> ventasAbiertas = ventaRepository.findVentasAbiertasPorCliente(clienteId);
        for (Venta venta : ventasAbiertas) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal saldoPendiente = venta.getTotal().subtract(pagadoPrevio(venta.getId()));
            if (saldoPendiente.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal aplicar = restante.min(saldoPendiente);
            aplicarPagoAVenta(venta, aplicar);
            pagoRepository.save(Pago.builder()
                    .venta(venta)
                    .cliente(cliente)
                    .monto(aplicar)
                    .metodoPago(metodo)
                    .referencia("Pago de cuenta corriente")
                    .usuario(usuario)
                    .fecha(LocalDateTime.now())
                    .build());
            restante = restante.subtract(aplicar);
        }

        // Sobra monto porque el saldo incluye deuda manual sin venta asociada
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            pagoRepository.save(Pago.builder()
                    .cliente(cliente)
                    .monto(restante)
                    .metodoPago(metodo)
                    .referencia("Pago de cuenta corriente")
                    .usuario(usuario)
                    .fecha(LocalDateTime.now())
                    .build());
        }

        cliente.setSaldoCorriente(saldoActual.subtract(montoPago));
        clienteRepository.save(cliente);
    }

    /**
     * Reasigna el excedente de las ventas sobrepagadas a las ventas abiertas del mismo
     * cliente. Mueve los pagos (o parte de ellos) sin alterar el monto total cobrado ni
     * el saldoCorriente del cliente.
     */
    private void reasignarExcedentes(Long clienteId) {
        List<Venta> ventas = new ArrayList<>(ventaRepository.findByClienteId(clienteId));
        ventas.sort(Comparator.comparing(Venta::getFecha).thenComparing(Venta::getId));

        for (Venta origen : ventas) {
            if (origen.getEstado() == Venta.EstadoVenta.ANULADA) {
                continue;
            }
            BigDecimal excedente = pagadoPrevio(origen.getId()).subtract(origen.getTotal());
            if (excedente.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            for (Venta destino : ventas) {
                if (excedente.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                if (destino.getId().equals(origen.getId())
                        || destino.getEstado() == Venta.EstadoVenta.ANULADA) {
                    continue;
                }
                BigDecimal pendiente = destino.getTotal().subtract(pagadoPrevio(destino.getId()));
                if (pendiente.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal mover = excedente.min(pendiente);
                moverPagos(origen.getId(), destino, mover);
                actualizarEstado(destino);
                excedente = excedente.subtract(mover);
            }

            actualizarEstado(origen);
        }
    }

    /**
     * Mueve hasta {@code monto} de los pagos de una venta origen a una venta destino,
     * partiendo un pago si hace falta.
     */
    private void moverPagos(Long ventaOrigenId, Venta destino, BigDecimal monto) {
        BigDecimal restante = monto;
        List<Pago> pagos = new ArrayList<>(pagoRepository.findByVentaId(ventaOrigenId));
        pagos.sort(Comparator.comparing(Pago::getId));

        for (Pago pago : pagos) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal disponible = pago.getMonto();
            if (disponible.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal mover = restante.min(disponible);
            if (mover.compareTo(disponible) >= 0) {
                pago.setVenta(destino);
                pago.setCliente(destino.getCliente());
                pagoRepository.save(pago);
            } else {
                pago.setMonto(disponible.subtract(mover));
                pagoRepository.save(pago);
                pagoRepository.save(Pago.builder()
                        .venta(destino)
                        .cliente(destino.getCliente())
                        .monto(mover)
                        .metodoPago(pago.getMetodoPago())
                        .moneda(pago.getMoneda())
                        .fecha(pago.getFecha())
                        .referencia("Reasignación de sobrepago")
                        .usuario(pago.getUsuario())
                        .build());
            }
            restante = restante.subtract(mover);
        }

        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("No hay pagos suficientes para reasignar el excedente");
        }
    }

    private void actualizarEstado(Venta venta) {
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            return;
        }
        BigDecimal pagado = pagadoPrevio(venta.getId());
        if (pagado.compareTo(BigDecimal.ZERO) <= 0) {
            venta.setEstado(Venta.EstadoVenta.PENDIENTE);
        } else if (pagado.compareTo(venta.getTotal()) >= 0) {
            venta.setEstado(Venta.EstadoVenta.PAGADA);
        } else {
            venta.setEstado(Venta.EstadoVenta.PARCIAL);
        }
    }

    /**
     * Aplica un monto a una venta: valida que pueda recibir el pago y actualiza su estado.
     * Debe invocarse ANTES de persistir el Pago correspondiente.
     */
    private void aplicarPagoAVenta(Venta venta, BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor a cero");
        }
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new IllegalArgumentException("No se pueden registrar pagos sobre una venta anulada");
        }
        if (venta.getEstado() == Venta.EstadoVenta.PAGADA) {
            throw new IllegalArgumentException("La venta ya está pagada");
        }
        BigDecimal pagadoPrevio = pagadoPrevio(venta.getId());
        BigDecimal saldoPendiente = venta.getTotal().subtract(pagadoPrevio);
        if (monto.compareTo(saldoPendiente) > 0) {
            throw new IllegalArgumentException(
                    "El pago supera el saldo pendiente de la venta #" + venta.getId()
                            + " (saldo pendiente: " + saldoPendiente + ")");
        }
        BigDecimal nuevoPagado = pagadoPrevio.add(monto);
        venta.setEstado(nuevoPagado.compareTo(venta.getTotal()) >= 0
                ? Venta.EstadoVenta.PAGADA
                : Venta.EstadoVenta.PARCIAL);
    }

    /** Monto ya pagado en una venta según la tabla de pagos (no usa la colección lazy). */
    private BigDecimal pagadoPrevio(Long ventaId) {
        BigDecimal suma = pagoRepository.sumMontoPagadoPorVenta(ventaId);
        return (suma == null ? BigDecimal.ZERO : suma).setScale(2, RoundingMode.HALF_UP);
    }

    private Pago.MetodoPago parseMetodoPago(String valor) {
        if (valor == null || valor.isBlank()) {
            return Pago.MetodoPago.EFECTIVO;
        }
        try {
            return Pago.MetodoPago.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return Pago.MetodoPago.EFECTIVO;
        }
    }
    
    @Transactional
    public void anularVenta(Long ventaId) {
        Venta venta = buscarPorId(ventaId);
        if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
            throw new IllegalArgumentException("La venta ya está anulada");
        }
        
        // Devolver stock
        for (DetalleVenta detalle : venta.getDetalles()) {
            productoService.agregarStock(detalle.getProducto().getId(), detalle.getCantidad());
        }

        // Cancelar el efecto neto que la venta tenía sobre la cuenta corriente:
        // si quedaba deuda, se elimina; si había un excedente a favor, también se revierte.
        BigDecimal saldoNetoVenta = venta.getTotal().subtract(pagadoPrevio(ventaId));
        if (venta.getCliente() != null && saldoNetoVenta.compareTo(BigDecimal.ZERO) != 0) {
            Cliente cliente = clienteRepository.findById(venta.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            BigDecimal saldoActual = cliente.getSaldoCorriente() == null
                    ? BigDecimal.ZERO
                    : cliente.getSaldoCorriente();
            cliente.setSaldoCorriente(saldoActual.subtract(saldoNetoVenta));
            clienteRepository.save(cliente);
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
