package com.repuestos.controllers;

import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Categoria;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.services.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {
    
    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoriaService;
    
    @GetMapping
    public ResponseEntity<List<Categoria>> listar() {
        return ResponseEntity.ok(categoriaRepository.findByActivaTrueOrderByIdAsc());
    }
    
    @PostMapping
    public ResponseEntity<Categoria> crear(@RequestBody Categoria categoria) {
        Categoria guardada = categoriaRepository.save(categoria);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("categoriaId", guardada.getId());
        detalle.put("nombre", guardada.getNombre());
        detalle.put("descripcion", guardada.getDescripcion());
        auditoriaService.registrar(AccionAuditoria.CATEGORIA_CREADA, "Categoria",
                guardada.getId(),
                "Creó la categoría '" + guardada.getNombre() + "'",
                detalle);
        return ResponseEntity.ok(guardada);
    }
}
