package com.repuestos.services;

import com.repuestos.models.Categoria;
import com.repuestos.models.Producto;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.repositories.ProductoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * H2 devuelve las filas en orden de inserción, por lo que este test fija el
 * CONTRATO de orden por id ascendente en los listados de productos/categorías.
 * La verificación real del defecto original (un UPDATE reescribía la fila y la
 * movía de posición) se hizo contra el PostgreSQL de Docker.
 */
@DataJpaTest
class OrdenProductosTest {

    @Autowired private ProductoRepository productoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private EntityManager entityManager;

    private Categoria categoria(String nombre) {
        return categoriaRepository.save(Categoria.builder().nombre(nombre).activa(true).build());
    }

    private Producto producto(String nombre, Categoria categoria, int stockActual) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .descripcion("Descripción de " + nombre)
                .precio(new BigDecimal("100.00"))
                .stockActual(stockActual)
                .stockMinimo(5)
                .categoria(categoria)
                .activo(true)
                .build());
    }

    private List<Long> ids(List<Producto> productos) {
        return productos.stream().map(Producto::getId).toList();
    }

    private void flushYLimpiar() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void elOrdenPorIdSeMantieneAunqueSeEditeUnProductoDelMedio() {
        Categoria categoria = categoria("Categoría orden");
        Producto primero = producto("Producto primero", categoria, 1);
        Producto segundo = producto("Producto segundo", categoria, 1);
        Producto tercero = producto("Producto tercero", categoria, 1);
        Producto cuarto = producto("Producto cuarto", categoria, 1);
        Producto quinto = producto("Producto quinto", categoria, 1);
        flushYLimpiar();

        List<Long> esperado = List.of(primero.getId(), segundo.getId(), tercero.getId(),
                cuarto.getId(), quinto.getId());

        assertEquals(esperado, ids(productoRepository.findByActivoTrueOrderByIdAsc()));
        assertEquals(esperado, ids(productoRepository.buscar("Producto")));
        assertEquals(esperado, ids(productoRepository
                .findByCategoriaIdAndActivoTrueOrderByIdAsc(categoria.getId())));
        assertEquals(esperado, ids(productoRepository.findProductosStockBajo()));

        Producto delMedio = productoRepository.findById(tercero.getId()).orElseThrow();
        delMedio.setNombre("Producto tercero editado");
        delMedio.setDescripcion("Descripción editada");
        productoRepository.saveAndFlush(delMedio);
        entityManager.clear();

        assertEquals(esperado, ids(productoRepository.findByActivoTrueOrderByIdAsc()));
        assertEquals(esperado, ids(productoRepository.buscar("Producto")));
        assertEquals(esperado, ids(productoRepository
                .findByCategoriaIdAndActivoTrueOrderByIdAsc(categoria.getId())));
        assertEquals(esperado, ids(productoRepository.findProductosStockBajo()));
    }

    @Test
    void consultasPorIdsDevuelvenSiempreOrdenAscendente() {
        Categoria categoria = categoria("Categoría ids");
        Producto primero = producto("Producto ids uno", categoria, 10);
        Producto segundo = producto("Producto ids dos", categoria, 10);
        Producto tercero = producto("Producto ids tres", categoria, 10);
        flushYLimpiar();

        List<Long> invertidos = List.of(tercero.getId(), primero.getId(), segundo.getId());
        List<Long> esperado = List.of(primero.getId(), segundo.getId(), tercero.getId());

        assertEquals(esperado, ids(productoRepository.findActivosByIds(invertidos)));
        assertEquals(esperado, ids(productoRepository.findByIdsParaActualizar(invertidos)));
    }

    @Test
    void lasCategoriasSeListanEnOrdenDeId() {
        Categoria primera = categoria("Categoría uno");
        Categoria segunda = categoria("Categoría dos");
        Categoria tercera = categoria("Categoría tres");
        flushYLimpiar();

        assertEquals(List.of(primera.getId(), segunda.getId(), tercera.getId()),
                categoriaRepository.findByActivaTrueOrderByIdAsc().stream()
                        .map(Categoria::getId).toList());
    }
}
