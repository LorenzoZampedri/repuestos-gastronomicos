package com.repuestos.controllers;

import com.repuestos.models.Producto;
import com.repuestos.services.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {
    
    private final ProductoService productoService;
    
    @GetMapping
    public ResponseEntity<List<Producto>> listarTodos() {
        return ResponseEntity.ok(productoService.listarTodos());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Producto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.buscarPorId(id));
    }
    
    @GetMapping("/buscar")
    public ResponseEntity<List<Producto>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(productoService.buscar(q));
    }
    
    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<Producto> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(productoService.buscarPorCodigoBarras(codigo));
    }
    
    @GetMapping("/categoria/{categoriaId}")
    public ResponseEntity<List<Producto>> porCategoria(@PathVariable Long categoriaId) {
        return ResponseEntity.ok(productoService.listarPorCategoria(categoriaId));
    }
    
    @GetMapping("/stock-bajo")
    public ResponseEntity<List<Producto>> stockBajo() {
        return ResponseEntity.ok(productoService.productosStockBajo());
    }
    
    @GetMapping("/catalogo")
    public ResponseEntity<List<Producto>> catalogoPublico() {
        return ResponseEntity.ok(productoService.listarTodos());
    }
    
    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto producto) {
        return ResponseEntity.ok(productoService.guardar(producto));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id, @RequestBody Producto producto) {
        return ResponseEntity.ok(productoService.actualizar(id, producto));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.ok().build();
    }
}
