package com.repuestos.services;

import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Producto;
import com.repuestos.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoService {
    
    private final ProductoRepository productoRepository;
    private final ConfiguracionService configuracionService;
    private final AuditoriaService auditoriaService;
    
    public List<Producto> listarTodos() {
        return productoRepository.findByActivoTrue();
    }
    
    public Producto buscarPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }
    
    public Producto buscarPorCodigoBarras(String codigo) {
        return productoRepository.findByCodigoBarras(codigo)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con código: " + codigo));
    }
    
    public List<Producto> buscar(String termino) {
        return productoRepository.buscar(termino);
    }
    
    public List<Producto> listarPorCategoria(Long categoriaId) {
        return productoRepository.findByCategoriaIdAndActivoTrue(categoriaId);
    }
    
    public List<Producto> productosStockBajo() {
        return productoRepository.findProductosStockBajo();
    }
    
    @Transactional
    public Producto guardar(Producto producto) {
        String codigo = normalizarCodigoBarras(producto.getCodigoBarras());
        validarCodigoBarras(codigo, null);
        producto.setCodigoBarras(codigo);
        sincronizarPrecioArs(producto);
        Producto guardado = productoRepository.save(producto);

        Map<String, Object> detalle = datosEditablesProducto(guardado);
        auditoriaService.registrar(AccionAuditoria.PRODUCTO_CREADO, "Producto",
                guardado.getId(),
                "Creó el producto '" + guardado.getNombre() + "'",
                detalle);
        return guardado;
    }
    
    @Transactional
    public Producto actualizar(Long id, Producto productoActualizado) {
        Producto producto = buscarPorId(id);
        Map<String, Object> antes = datosEditablesProducto(producto);
        String codigo = normalizarCodigoBarras(productoActualizado.getCodigoBarras());
        validarCodigoBarras(codigo, id);
        producto.setNombre(productoActualizado.getNombre());
        producto.setDescripcion(productoActualizado.getDescripcion());
        producto.setPrecioUsd(productoActualizado.getPrecioUsd());
        producto.setStockActual(productoActualizado.getStockActual());
        producto.setStockMinimo(productoActualizado.getStockMinimo());
        producto.setCodigoBarras(codigo);
        producto.setImagenUrl(productoActualizado.getImagenUrl());
        producto.setCategoria(productoActualizado.getCategoria());
        sincronizarPrecioArs(producto);
        Producto guardado = productoRepository.save(producto);

        Map<String, Object> despues = datosEditablesProducto(guardado);
        Map<String, Object> cambios = auditoriaService.calcularDiff(antes, despues);
        if (!cambios.isEmpty()) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("productoId", guardado.getId());
            detalle.put("nombre", guardado.getNombre());
            detalle.put("cambios", cambios);
            auditoriaService.registrar(AccionAuditoria.PRODUCTO_EDITADO, "Producto",
                    guardado.getId(),
                    "Editó el producto '" + guardado.getNombre() + "'",
                    detalle);
        }

        Integer stockAntes = valorEntero(antes.get("stockActual"));
        Integer stockDespues = valorEntero(despues.get("stockActual"));
        if (stockAntes != null && stockDespues != null && !stockAntes.equals(stockDespues)) {
            Map<String, Object> detalleStock = new LinkedHashMap<>();
            detalleStock.put("productoId", guardado.getId());
            detalleStock.put("nombre", guardado.getNombre());
            detalleStock.put("stockAntes", stockAntes);
            detalleStock.put("stockDespues", stockDespues);
            auditoriaService.registrar(AccionAuditoria.STOCK_AJUSTADO, "Producto",
                    guardado.getId(),
                    "Ajustó el stock de '" + guardado.getNombre() + "': "
                            + stockAntes + " → " + stockDespues,
                    detalleStock);
        }
        return guardado;
    }

    private void sincronizarPrecioArs(Producto producto) {
        BigDecimal cotizacion = configuracionService.getCotizacionUsdArs();
        if (producto.getPrecioUsd() != null && producto.getPrecioUsd().compareTo(BigDecimal.ZERO) > 0 && cotizacion != null) {
            producto.setPrecio(PreciosArs.desdeUsd(producto.getPrecioUsd(), cotizacion));
        } else if (producto.getPrecio() == null) {
            producto.setPrecio(BigDecimal.ZERO);
        }
    }

    private String normalizarCodigoBarras(String codigo) {
        if (codigo == null) {
            return null;
        }
        String normalizado = codigo.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private void validarCodigoBarras(String codigo, Long idExcluido) {
        if (codigo == null) {
            return;
        }
        if (codigo.length() > 50) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El código de barras no puede superar los 50 caracteres");
        }
        productoRepository.findByCodigoBarras(codigo).ifPresent(existente -> {
            if (idExcluido != null && idExcluido.equals(existente.getId())) {
                return;
            }
            if (Boolean.TRUE.equals(existente.getActivo())) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "Ya existe un producto con el código de barras '" + codigo + "' (" + existente.getNombre() + ").");
            }
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El código de barras '" + codigo + "' pertenece a un producto eliminado (" + existente.getNombre() + "). Usá otro código.");
        });
    }
    
    @Transactional
    public void descontarStock(Long productoId, int cantidad) {
        Producto producto = buscarPorId(productoId);
        if (producto.getStockActual() < cantidad) {
            throw new RuntimeException("Stock insuficiente para: " + producto.getNombre());
        }
        producto.setStockActual(producto.getStockActual() - cantidad);
        productoRepository.save(producto);
    }
    
    @Transactional
    public void agregarStock(Long productoId, int cantidad) {
        Producto producto = buscarPorId(productoId);
        producto.setStockActual(producto.getStockActual() + cantidad);
        productoRepository.save(producto);
    }
    
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscarPorId(id);
        boolean estabaActivo = Boolean.TRUE.equals(producto.getActivo());
        producto.setActivo(false);
        productoRepository.save(producto);

        if (estabaActivo) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("productoId", producto.getId());
            detalle.put("nombre", producto.getNombre());
            detalle.put("codigoBarras", producto.getCodigoBarras());
            detalle.put("precio", producto.getPrecio());
            detalle.put("stockActual", producto.getStockActual());
            auditoriaService.registrar(AccionAuditoria.PRODUCTO_ELIMINADO, "Producto",
                    producto.getId(),
                    "Dio de baja el producto '" + producto.getNombre() + "'",
                    detalle);
        }
    }
    
    public Long contarProductosActivos() {
        return productoRepository.countProductosActivos();
    }

    private Map<String, Object> datosEditablesProducto(Producto producto) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", producto.getNombre());
        datos.put("descripcion", producto.getDescripcion());
        datos.put("codigoBarras", producto.getCodigoBarras());
        datos.put("categoria", producto.getCategoria() == null
                ? null : producto.getCategoria().getNombre());
        datos.put("precio", producto.getPrecio());
        datos.put("precioUsd", producto.getPrecioUsd());
        datos.put("stockActual", producto.getStockActual());
        datos.put("stockMinimo", producto.getStockMinimo());
        datos.put("imagenUrl", producto.getImagenUrl());
        datos.put("activo", producto.getActivo());
        return datos;
    }

    private Integer valorEntero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : null;
    }
}
