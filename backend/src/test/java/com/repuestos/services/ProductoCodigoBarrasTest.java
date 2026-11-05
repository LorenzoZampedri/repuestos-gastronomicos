package com.repuestos.services;

import com.repuestos.models.Producto;
import com.repuestos.repositories.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({ProductoService.class, ConfiguracionService.class})
class ProductoCodigoBarrasTest {

    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;

    private Producto producto(String nombre, String codigo) {
        return Producto.builder()
                .nombre(nombre)
                .precioUsd(new BigDecimal("10.00"))
                .precio(new BigDecimal("1000.00"))
                .stockActual(1)
                .stockMinimo(1)
                .codigoBarras(codigo)
                .build();
    }

    private NegocioException guardarYCapturarError(Producto producto) {
        return assertThrows(NegocioException.class, () -> productoService.guardar(producto));
    }

    private NegocioException actualizarYCapturarError(Long id, Producto producto) {
        return assertThrows(NegocioException.class, () -> productoService.actualizar(id, producto));
    }

    // ---------------------------------------------------------------------
    // guardar
    // ---------------------------------------------------------------------

    @Test
    void guardarConCodigoNuevoSeCrea() {
        Producto guardado = productoService.guardar(producto("Producto Nuevo", "COD-NEW-001"));

        assertEquals("COD-NEW-001", guardado.getCodigoBarras());
        assertEquals(1, productoRepository.count());
    }

    @Test
    void guardarConCodigoDuplicadoDeActivoDevuelve409() {
        productoService.guardar(producto("Producto Original", "COD-DUP-001"));
        productoRepository.flush();

        NegocioException error = guardarYCapturarError(producto("Otro Producto", "COD-DUP-001"));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Ya existe un producto con el código de barras 'COD-DUP-001' (Producto Original).",
                error.getMessage());
    }

    @Test
    void guardarConCodigoDeProductoEliminadoDevuelve409() {
        Producto original = productoService.guardar(producto("Producto Borrado", "COD-DEL-001"));
        productoRepository.flush();
        productoService.eliminar(original.getId());

        NegocioException error = guardarYCapturarError(producto("Otro Producto", "COD-DEL-001"));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("El código de barras 'COD-DEL-001' pertenece a un producto eliminado (Producto Borrado). Usá otro código.",
                error.getMessage());
    }

    @Test
    void guardarConCodigoEnBlancoQuedaNull() {
        Producto guardado = productoService.guardar(producto("Sin Código", "   "));

        assertNull(guardado.getCodigoBarras());

        Producto conEspacios = productoService.guardar(producto("Con Espacios", "  COD-TRIM  "));
        assertEquals("COD-TRIM", conEspacios.getCodigoBarras());
    }

    @Test
    void guardarConCodigoDeMasDe50CaracteresDevuelve400() {
        String largo = "X".repeat(51);

        NegocioException error = guardarYCapturarError(producto("Producto Largo", largo));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("El código de barras no puede superar los 50 caracteres", error.getMessage());
    }

    // ---------------------------------------------------------------------
    // actualizar
    // ---------------------------------------------------------------------

    @Test
    void actualizarConservandoSuPropioCodigoFunciona() {
        Producto original = productoService.guardar(producto("Producto Propio", "COD-SELF-001"));
        productoRepository.flush();

        Producto cambios = producto("Producto Propio Editado", "COD-SELF-001");
        cambios.setPrecioUsd(new BigDecimal("20.00"));

        Producto actualizado = productoService.actualizar(original.getId(), cambios);

        assertEquals(original.getId(), actualizado.getId());
        assertEquals("COD-SELF-001", actualizado.getCodigoBarras());
        assertEquals("Producto Propio Editado", actualizado.getNombre());
    }

    @Test
    void actualizarConCodigoDeOtroProductoDevuelve409() {
        productoService.guardar(producto("Producto Dueño", "COD-OTHER-001"));
        Producto propio = productoService.guardar(producto("Producto Editado", "COD-OWN-001"));
        productoRepository.flush();

        NegocioException error = actualizarYCapturarError(propio.getId(),
                producto("Producto Editado", "COD-OTHER-001"));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("Ya existe un producto con el código de barras 'COD-OTHER-001' (Producto Dueño).",
                error.getMessage());
    }

    @Test
    void dosProductosSinCodigoSeCreanAmbos() {
        productoService.guardar(producto("Sin Código 1", null));
        productoService.guardar(producto("Sin Código 2", null));

        assertEquals(2, productoRepository.count());
    }
}
