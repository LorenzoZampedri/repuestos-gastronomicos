package com.repuestos.services;

import com.repuestos.dto.PedidoContactoResponse;
import com.repuestos.dto.PedidoCrearRequest;
import com.repuestos.dto.PedidoRechazarRequest;
import com.repuestos.dto.PedidoResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Cliente;
import com.repuestos.models.DetallePedido;
import com.repuestos.models.DetalleVenta;
import com.repuestos.models.Pedido;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PedidoRepository;
import com.repuestos.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Set<String> METODOS_PAGO_CLIENTE = Set.of(
            "EFECTIVO", "TARJETA", "TRANSFERENCIA");
    private static final int MAXIMO_PEDIDOS_PENDIENTES = 10;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final ClienteService clienteService;
    private final VentaService ventaService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public PedidoResponse crear(Usuario usuario, PedidoCrearRequest request) {
        String metodoPago = validarMetodoPagoCliente(request.metodoPago());

        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Tu cuenta de cliente está inhabilitada, contactanos");
        }

        long pendientes = pedidoRepository.countByClienteIdAndEstado(
                cliente.getId(), Pedido.EstadoPedido.PENDIENTE);
        if (pendientes >= MAXIMO_PEDIDOS_PENDIENTES) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Ya tenés 10 pedidos pendientes. Esperá a que sean procesados.");
        }

        Map<Long, Integer> cantidades = new HashMap<>();
        for (var item : request.items()) {
            int cantidad = cantidades.getOrDefault(item.productoId(), 0) + item.cantidad();
            if (cantidad > 9999) {
                throw new NegocioException(HttpStatus.BAD_REQUEST,
                        "La cantidad máxima por producto es 9999");
            }
            cantidades.put(item.productoId(), cantidad);
        }

        Pedido pedido = Pedido.builder()
                .cliente(cliente)
                .usuario(usuario)
                .fecha(LocalDateTime.now())
                .estado(Pedido.EstadoPedido.PENDIENTE)
                .metodoPago(metodoPago)
                .observaciones(request.observaciones() == null
                        ? null : request.observaciones().trim())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cantidades.entrySet()) {
            Producto producto = productoRepository.findById(entry.getKey())
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                            "Producto no encontrado"));
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new NegocioException(HttpStatus.BAD_REQUEST,
                        "El producto no está disponible: " + producto.getNombre());
            }
            if (producto.getStockActual() < entry.getValue()) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "Stock insuficiente para: " + producto.getNombre());
            }

            BigDecimal precioUnitario = ventaService.calcularPrecioUnitario(
                    producto, entry.getValue());
            total = total.add(ventaService.calcularSubtotal(
                    precioUnitario, entry.getValue()));
            pedido.agregarDetalle(DetallePedido.builder()
                    .producto(producto)
                    .cantidad(entry.getValue())
                    .nombreProducto(producto.getNombre())
                    .precioUnitarioEstimado(precioUnitario)
                    .build());
        }
        pedido.setTotalEstimado(total);

        String telefono = request.telefono().trim();
        if (cliente.getTelefono() == null || cliente.getTelefono().isBlank()
                || !telefono.equals(cliente.getTelefono())) {
            cliente.setTelefono(telefono);
            clienteRepository.save(cliente);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("pedidoId", guardado.getId());
        detalle.put("totalEstimado", guardado.getTotalEstimado());
        detalle.put("metodoPago", guardado.getMetodoPago());
        detalle.put("cantidadItems", guardado.getDetalles() == null
                ? 0 : guardado.getDetalles().size());
        detalle.put("clienteId", cliente.getId());
        detalle.put("cliente", cliente.getNombre());
        auditoriaService.registrarComo(AccionAuditoria.PEDIDO_CREADO, usuario, "Pedido",
                guardado.getId(),
                "Creó el pedido #" + guardado.getId() + " por $"
                        + formatearMonto(guardado.getTotalEstimado())
                        + " (" + guardado.getMetodoPago() + ")",
                detalle);
        return PedidoResponse.desde(guardado, false, false, false);
    }

    @Transactional
    public List<PedidoResponse> listarMios(Usuario usuario) {
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        return pedidoRepository.findByClienteIdOrderByFechaDesc(cliente.getId()).stream()
                .map(pedido -> PedidoResponse.desde(pedido, false, false, false))
                .toList();
    }

    @Transactional
    public PedidoResponse obtenerMio(Long id, Usuario usuario) {
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        Pedido pedido = pedidoRepository.findByIdAndClienteId(id, cliente.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Pedido no encontrado"));
        return PedidoResponse.desde(pedido, false, false, false);
    }

    @Transactional
    public PedidoContactoResponse contacto(Usuario usuario) {
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        return new PedidoContactoResponse(cliente.getTelefono(), cliente.getDireccion());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarStaff(Pedido.EstadoPedido estado, boolean esAdmin) {
        return pedidoRepository.findByEstadoOrderByFechaAsc(estado).stream()
                .map(pedido -> PedidoResponse.desde(pedido, true, esAdmin, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtenerStaff(Long id, boolean esAdmin) {
        Pedido pedido = pedidoRepository.findByIdConRelaciones(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Pedido no encontrado"));
        return PedidoResponse.desde(pedido, true, esAdmin, true);
    }

    public long contarPendientes() {
        return pedidoRepository.countByEstado(Pedido.EstadoPedido.PENDIENTE);
    }

    @Transactional
    public PedidoResponse convertir(Long id, Usuario actor, boolean esAdmin) {
        Pedido pedido = bloqueado(id);
        validarPendiente(pedido);

        String metodoPago = pedido.getMetodoPago();
        if (metodoPago == null || metodoPago.isBlank()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Este pedido es anterior a la selección de método de pago y no puede "
                            + "aprobarse: rechazalo y pedile al cliente que lo repita");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<DetalleVenta> lineasVenta = new ArrayList<>();
        for (DetallePedido detalle : pedido.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (producto == null || !Boolean.TRUE.equals(producto.getActivo())) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "El producto ya no está disponible: " + detalle.getNombreProducto());
            }
            if (producto.getStockActual() < detalle.getCantidad()) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "Stock insuficiente para: " + producto.getNombre());
            }

            BigDecimal precioUnitario = ventaService.calcularPrecioUnitario(
                    producto, detalle.getCantidad());
            detalle.setPrecioUnitarioEstimado(precioUnitario);
            detalle.setNombreProducto(producto.getNombre());
            total = total.add(ventaService.calcularSubtotal(
                    precioUnitario, detalle.getCantidad()));
            lineasVenta.add(DetalleVenta.builder()
                    .producto(producto)
                    .cantidad(detalle.getCantidad())
                    .build());
        }
        pedido.setTotalEstimado(total);

        Venta venta = Venta.builder()
                .usuario(actor)
                .observaciones("Pedido #" + pedido.getId())
                .build();

        Venta ventaGuardada;
        try {
            ventaGuardada = ventaService.crearVentaConPago(
                    venta, lineasVenta, pedido.getCliente().getId(), metodoPago);
        } catch (NegocioException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Stock insuficiente para:")) {
                throw new NegocioException(HttpStatus.CONFLICT, ex.getMessage());
            }
            throw ex;
        }

        pedido.setEstado(Pedido.EstadoPedido.CONVERTIDO);
        pedido.setVenta(ventaGuardada);
        pedido.setResueltoPor(actor);
        pedido.setFechaResolucion(LocalDateTime.now());
        Pedido guardado = pedidoRepository.save(pedido);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("pedidoId", guardado.getId());
        detalle.put("ventaId", ventaGuardada.getId());
        detalle.put("total", ventaGuardada.getTotal());
        detalle.put("metodoPago", metodoPago);
        detalle.put("clienteId", guardado.getCliente() == null
                ? null : guardado.getCliente().getId());
        detalle.put("cliente", guardado.getCliente() == null
                ? null : guardado.getCliente().getNombre());
        auditoriaService.registrarComo(AccionAuditoria.PEDIDO_APROBADO, actor, "Pedido",
                guardado.getId(),
                "Aprobó el pedido #" + guardado.getId()
                        + " y generó la venta #" + ventaGuardada.getId()
                        + " por $" + formatearMonto(ventaGuardada.getTotal()),
                detalle);
        return PedidoResponse.desde(guardado, true, esAdmin, true);
    }

    @Transactional
    public PedidoResponse rechazar(Long id, PedidoRechazarRequest request,
                                   Usuario actor, boolean esAdmin) {
        Pedido pedido = bloqueado(id);
        validarPendiente(pedido);

        pedido.setEstado(Pedido.EstadoPedido.RECHAZADO);
        pedido.setMotivoRechazo(request.motivo().trim());
        pedido.setResueltoPor(actor);
        pedido.setFechaResolucion(LocalDateTime.now());
        Pedido guardado = pedidoRepository.save(pedido);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("pedidoId", guardado.getId());
        detalle.put("motivo", guardado.getMotivoRechazo());
        detalle.put("totalEstimado", guardado.getTotalEstimado());
        detalle.put("clienteId", guardado.getCliente() == null
                ? null : guardado.getCliente().getId());
        detalle.put("cliente", guardado.getCliente() == null
                ? null : guardado.getCliente().getNombre());
        auditoriaService.registrarComo(AccionAuditoria.PEDIDO_RECHAZADO, actor, "Pedido",
                guardado.getId(),
                "Rechazó el pedido #" + guardado.getId()
                        + " por: " + guardado.getMotivoRechazo(),
                detalle);
        return PedidoResponse.desde(guardado, true, esAdmin, true);
    }

    @Transactional
    public PedidoResponse cancelarMio(Long id, Usuario usuario) {
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        Pedido pedido = bloqueado(id);
        if (pedido.getCliente() == null
                || !pedido.getCliente().getId().equals(cliente.getId())) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        validarPendiente(pedido);

        pedido.setEstado(Pedido.EstadoPedido.CANCELADO);
        pedido.setResueltoPor(usuario);
        pedido.setFechaResolucion(LocalDateTime.now());
        Pedido guardado = pedidoRepository.save(pedido);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("pedidoId", guardado.getId());
        detalle.put("totalEstimado", guardado.getTotalEstimado());
        detalle.put("metodoPago", guardado.getMetodoPago());
        detalle.put("clienteId", guardado.getCliente() == null
                ? null : guardado.getCliente().getId());
        auditoriaService.registrarComo(AccionAuditoria.PEDIDO_CANCELADO, usuario, "Pedido",
                guardado.getId(),
                "Canceló el pedido #" + guardado.getId(),
                detalle);
        return PedidoResponse.desde(guardado, false, false, false);
    }

    private Pedido bloqueado(Long id) {
        return pedidoRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Pedido no encontrado"));
    }

    private void validarPendiente(Pedido pedido) {
        if (pedido.getEstado() != Pedido.EstadoPedido.PENDIENTE) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El pedido ya fue procesado");
        }
    }

    private String validarMetodoPagoCliente(String metodoPago) {
        if (metodoPago == null || metodoPago.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Método de pago no disponible para compras online");
        }
        String metodo = metodoPago.trim().toUpperCase(Locale.ROOT);
        if (!METODOS_PAGO_CLIENTE.contains(metodo)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Método de pago no disponible para compras online");
        }
        return metodo;
    }

    private String formatearMonto(BigDecimal monto) {
        if (monto == null) {
            return "0,00";
        }
        DecimalFormat formato = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        return formato.format(monto);
    }
}
