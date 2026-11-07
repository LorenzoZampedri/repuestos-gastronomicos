package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.models.DetalleVenta;
import com.repuestos.models.Pago;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PagoRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, ProductoService.class,
        ConfiguracionService.class, AuditoriaService.class,
        FotoPerfilService.class, AvatarStorageService.class})
class VentaAnonimaTest {

    @Autowired private VentaService ventaService;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;
    private Producto producto;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("anonimo-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Empleado Anonimo")
                .rol(Usuario.Rol.EMPLEADO)
                .activo(true)
                .build());
        producto = productoRepository.save(Producto.builder()
                .nombre("Producto Anonimo")
                .precio(new BigDecimal("100.00"))
                .stockActual(10)
                .activo(true)
                .build());
    }

    private Venta venta() {
        return Venta.builder().usuario(usuario).build();
    }

    private List<DetalleVenta> detalles() {
        return List.of(DetalleVenta.builder().producto(producto).cantidad(2).build());
    }

    @Test
    void ventaAnonimaACuentaCorrienteSeRechaza() {
        NegocioException error = assertThrows(NegocioException.class,
                () -> ventaService.crearVentaConPago(
                        venta(), detalles(), null, "CUENTA_CORRIENTE"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("Para vender a cuenta corriente tenés que seleccionar un cliente registrado",
                error.getMessage());
        assertEquals(0, ventaRepository.count());
        assertEquals(10, recargarProducto().getStockActual());
    }

    @Test
    void ventaAnonimaEnEfectivoFuncionaSinTocarNingunSaldo() {
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Con Saldo")
                .saldoCorriente(new BigDecimal("500.00"))
                .activo(true)
                .build());

        Venta creada = ventaService.crearVentaConPago(
                venta(), detalles(), null, "EFECTIVO");

        entityManager.flush();
        entityManager.clear();

        Venta guardada = ventaRepository.findById(creada.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(guardada.getTotal()));
        assertEquals(Venta.EstadoVenta.PAGADA, guardada.getEstado());
        assertNull(guardada.getCliente(), "la venta anónima no tiene cliente");

        assertEquals(8, recargarProducto().getStockActual(), "el stock sí se descuenta");
        assertEquals(0, new BigDecimal("500.00").compareTo(
                        clienteRepository.findById(cliente.getId()).orElseThrow()
                                .getSaldoCorriente()),
                "ningún saldo corriente se modifica");
        assertEquals(1, pagoRepository.count(), "el pago queda ligado solo a la venta");
        assertNull(pagoRepository.findAll().get(0).getCliente());
        assertTrue(pagoRepository.findByVentaId(creada.getId()).size() == 1);
    }

    private Producto recargarProducto() {
        entityManager.flush();
        entityManager.clear();
        return productoRepository.findById(producto.getId()).orElseThrow();
    }
}
