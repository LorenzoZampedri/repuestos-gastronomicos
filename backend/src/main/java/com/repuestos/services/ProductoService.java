package com.repuestos.services;

import com.repuestos.models.Producto;
import com.repuestos.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {
    
    private final ProductoRepository productoRepository;
    
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
    
    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }
    
    public Producto actualizar(Long id, Producto productoActualizado) {
        Producto producto = buscarPorId(id);
        producto.setNombre(productoActualizado.getNombre());
        producto.setDescripcion(productoActualizado.getDescripcion());
        producto.setPrecio(productoActualizado.getPrecio());
        producto.setStockActual(productoActualizado.getStockActual());
        producto.setStockMinimo(productoActualizado.getStockMinimo());
        producto.setCodigoBarras(productoActualizado.getCodigoBarras());
        producto.setImagenUrl(productoActualizado.getImagenUrl());
        producto.setCategoria(productoActualizado.getCategoria());
        return productoRepository.save(producto);
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
    
    public void eliminar(Long id) {
        Producto producto = buscarPorId(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }
    
    public Long contarProductosActivos() {
        return productoRepository.countProductosActivos();
    }
}
