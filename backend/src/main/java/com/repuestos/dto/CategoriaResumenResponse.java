package com.repuestos.dto;

import com.repuestos.models.Categoria;

public record CategoriaResumenResponse(Long id, String nombre) {

    public static CategoriaResumenResponse desde(Categoria categoria) {
        if (categoria == null) {
            return null;
        }
        return new CategoriaResumenResponse(categoria.getId(), categoria.getNombre());
    }
}
