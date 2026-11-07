package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.models.Pago;
import com.repuestos.models.Pedido;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.PagoRepository;
import com.repuestos.repositories.PedidoRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({ClienteService.class, VentaService.class, ProductoService.class,
        ConfiguracionService.class, AuditoriaService.class,
        FotoPerfilService.class, AvatarStorageService.class})
class ClienteVinculacionTest {

    @Autowired private ClienteService clienteService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private EntityManager entityManager;

    private Usuario crearUsuarioCliente(String username) {
        return usuarioRepository.save(Usuario.builder()
                .username(username + "-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Cliente " + username)
                .email(username + "@example.com")
                .rol(Usuario.Rol.CLIENTE)
                .activo(true)
                .build());
    }

    private Cliente crearCliente(String nombre) {
        return clienteRepository.save(Cliente.builder()
                .nombre(nombre)
                .saldoCorriente(BigDecimal.ZERO)
                .activo(true)
                .build());
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void vinculaClienteExistenteYReapuntaLosPedidosDelUsuario() {
        Usuario usuario = crearUsuarioCliente("vincular");
        Cliente anterior = clienteService.asegurarClienteParaUsuario(usuario);
        Cliente destino = crearCliente("Cliente Existente");
        pedidoRepository.save(Pedido.builder()
                .cliente(anterior)
                .usuario(usuario)
                .fecha(LocalDateTime.now())
                .totalEstimado(new BigDecimal("100.00"))
                .build());
        flushClear();

        Usuario vinculado = clienteService.vincularClienteAUsuario(
                usuario.getId(), destino.getId());
        flushClear();

        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertEquals(destino.getId(), recargado.getCliente().getId());
        assertEquals(destino.getId(), vinculado.getCliente().getId());

        Pedido pedido = pedidoRepository.findAll().get(0);
        assertEquals(destino.getId(), pedido.getCliente().getId());
        assertFalse(clienteRepository.findById(anterior.getId()).orElseThrow().getActivo(),
                "el cliente automático sin ventas ni pagos debe quedar inactivo");
    }

    @Test
    void rechazaLaVinculacionSiElClienteAnteriorTieneVentas() {
        Usuario usuario = crearUsuarioCliente("con-venta");
        Cliente anterior = clienteService.asegurarClienteParaUsuario(usuario);
        Cliente destino = crearCliente("Destino");
        ventaRepository.save(Venta.builder()
                .cliente(anterior)
                .usuario(usuario)
                .fecha(LocalDateTime.now())
                .total(new BigDecimal("50.00"))
                .build());
        flushClear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> clienteService.vincularClienteAUsuario(usuario.getId(), destino.getId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        flushClear();
        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertEquals(anterior.getId(), recargado.getCliente().getId());
        assertTrue(clienteRepository.findById(anterior.getId()).orElseThrow().getActivo(),
                "no debe darse de baja un cliente con ventas");
    }

    @Test
    void rechazaLaVinculacionSiElClienteAnteriorTienePagos() {
        Usuario usuario = crearUsuarioCliente("con-pago");
        Cliente anterior = clienteService.asegurarClienteParaUsuario(usuario);
        Cliente destino = crearCliente("Destino Pago");
        pagoRepository.save(Pago.builder()
                .cliente(anterior)
                .monto(new BigDecimal("20.00"))
                .metodoPago(Pago.MetodoPago.EFECTIVO)
                .moneda("ARS")
                .fecha(LocalDateTime.now())
                .build());
        flushClear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> clienteService.vincularClienteAUsuario(usuario.getId(), destino.getId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        flushClear();
        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertEquals(anterior.getId(), recargado.getCliente().getId());
        assertTrue(clienteRepository.findById(anterior.getId()).orElseThrow().getActivo(),
                "no debe darse de baja un cliente con pagos");
    }

    @Test
    void noVinculaUnClienteQueYaPerteneceAOtroUsuario() {
        Usuario usuario = crearUsuarioCliente("usuario-a");
        Usuario otro = crearUsuarioCliente("usuario-b");
        Cliente destino = clienteService.asegurarClienteParaUsuario(otro);
        flushClear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> clienteService.vincularClienteAUsuario(
                        usuario.getId(), destino.getId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void noVinculaUnClienteInactivo() {
        Usuario usuario = crearUsuarioCliente("usuario-inactivo");
        Cliente destino = crearCliente("Cliente Inactivo");
        destino.setActivo(false);
        clienteRepository.save(destino);
        flushClear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> clienteService.vincularClienteAUsuario(
                        usuario.getId(), destino.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void soloUsuariosClientePuedenVincularse() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-vinculo-" + System.nanoTime())
                .passwordHash("hash")
                .nombre("Admin")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());
        Cliente destino = crearCliente("Destino Admin");
        flushClear();

        NegocioException error = assertThrows(NegocioException.class,
                () -> clienteService.vincularClienteAUsuario(
                        admin.getId(), destino.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void vincularAlMismoClienteEsIdempotente() {
        Usuario usuario = crearUsuarioCliente("idempotente");
        Cliente cliente = clienteService.asegurarClienteParaUsuario(usuario);
        flushClear();

        clienteService.vincularClienteAUsuario(usuario.getId(), cliente.getId());
        flushClear();

        assertTrue(clienteRepository.findById(cliente.getId()).orElseThrow().getActivo());
        assertEquals(1, clienteRepository.count());
    }
}
