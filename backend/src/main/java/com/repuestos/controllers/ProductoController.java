package com.repuestos.controllers;

import com.repuestos.dto.CatalogoProductoResponse;
import com.repuestos.models.Producto;
import com.repuestos.services.ImagenInvalidaException;
import com.repuestos.services.ImagenStorageService;
import com.repuestos.services.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {
    
    private final ProductoService productoService;
    private final ImagenStorageService imagenStorageService;
    
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
    public ResponseEntity<List<CatalogoProductoResponse>> catalogoPublico() {
        return ResponseEntity.ok(productoService.listarTodos().stream()
                .map(CatalogoProductoResponse::desde)
                .toList());
    }
    
    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody Producto producto) {
        return ResponseEntity.ok(productoService.guardar(producto));
    }

    @PostMapping("/imagen")
    public ResponseEntity<Map<String, String>> subirImagen(@RequestParam("file") MultipartFile file) {
        try {
            String url = imagenStorageService.guardar(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (ImagenInvalidaException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo guardar la imagen"));
        }
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
