package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.repositories.ClienteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({VentaService.class, ClienteService.class, CargoCuentaCorrienteService.class, ProductoService.class, ConfiguracionService.class, AuditoriaService.class, FotoPerfilService.class, AvatarStorageService.class})
class OrdenClientesTest {

    @Autowired private ClienteService clienteService;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        crearCliente("Cliente Orden Uno", "50000.00");
        crearCliente("Cliente Orden Dos", "40000.00");
        crearCliente("Cliente Orden Tres", "30000.00");
        crearCliente("Cliente Orden Cuatro", "0.00");
        crearCliente("Cliente Orden Cinco", "20000.00");
        entityManager.flush();
        entityManager.clear();
    }

    private void crearCliente(String nombre, String saldo) {
        clienteRepository.save(Cliente.builder()
                .nombre(nombre)
                .telefono("+5491100000000")
                .saldoCorriente(new BigDecimal(saldo))
                .build());
    }

    private List<Long> ids(List<Cliente> clientes) {
        return clientes.stream().map(Cliente::getId).collect(Collectors.toList());
    }

    private List<Long> idsEsperados() {
        return clienteRepository.findAll().stream()
                .map(Cliente::getId)
                .sorted()
                .collect(Collectors.toList());
    }

    // Simula el request HTTP de un pago: modifica el saldoCorriente de un cliente.
    private void pagarSobreElClienteDelMedio() {
        Long idMedio = ids(clienteService.listarTodos()).get(2);
        entityManager.clear();
        Cliente medio = clienteRepository.findById(idMedio).orElseThrow();
        medio.setSaldoCorriente(medio.getSaldoCorriente().subtract(new BigDecimal("1000.00")));
        clienteRepository.save(medio);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void listarTodosMantieneElMismoOrdenDespuesDeUnPago() {
        List<Long> antes = ids(clienteService.listarTodos());
        assertEquals(idsEsperados(), antes, "la lista inicial debe venir ordenada por id ascendente");

        pagarSobreElClienteDelMedio();

        List<Long> despues = ids(clienteService.listarTodos());
        assertEquals(antes, despues, "un pago no debe cambiar el orden de la lista de clientes");
        assertEquals(idsEsperados(), despues, "tras el pago el orden sigue siendo id ascendente");
    }

    @Test
    void buscarMantieneElMismoOrdenDespuesDeUnPago() {
        entityManager.clear();
        List<Long> antes = ids(clienteService.buscar("cliente"));
        assertEquals(idsEsperados(), antes, "la busqueda debe venir ordenada por id ascendente");

        Long idMedio = antes.get(2);
        entityManager.clear();
        Cliente medio = clienteRepository.findById(idMedio).orElseThrow();
        medio.setSaldoCorriente(medio.getSaldoCorriente().subtract(new BigDecimal("1000.00")));
        clienteRepository.save(medio);
        entityManager.flush();
        entityManager.clear();

        List<Long> despues = ids(clienteService.buscar("cliente"));
        assertEquals(antes, despues, "un pago no debe cambiar el orden de la busqueda de clientes");
        assertEquals(idsEsperados(), despues, "tras el pago la busqueda sigue ordenada por id ascendente");
    }

    @Test
    void clientesConDeudaMantieneElMismoOrdenDespuesDeUnPago() {
        entityManager.clear();
        List<Long> antes = ids(clienteService.clientesConDeuda());
        assertEquals(antes.size(), 4, "los clientes con saldo > 0 deben aparecer en la lista de deuda");

        Long idMedio = antes.get(2);
        entityManager.clear();
        Cliente medio = clienteRepository.findById(idMedio).orElseThrow();
        medio.setSaldoCorriente(medio.getSaldoCorriente().subtract(new BigDecimal("1000.00")));
        clienteRepository.save(medio);
        entityManager.flush();
        entityManager.clear();

        List<Long> despues = ids(clienteService.clientesConDeuda());
        assertEquals(antes, despues, "un pago no debe cambiar el orden de los clientes con deuda");
        assertEquals(idsEsperados().stream().filter(antes::contains).collect(Collectors.toList()),
                despues, "los deudores deben venir ordenados por id ascendente");
    }

    @Test
    void todasLasConsultasDeClientesDelRepositorioDefinenOrdenExplicito() {
        for (Method metodo : ClienteRepository.class.getDeclaredMethods()) {
            Query query = metodo.getAnnotation(Query.class);
            if (query == null || metodo.getName().equals("findByIdParaActualizar")) {
                continue;
            }
            assertTrue(query.value().toLowerCase().contains("order by"),
                    "la query de " + metodo.getName() + " debe definir ORDER BY explicito, pero es: "
                            + query.value());
        }
        assertTrue(Arrays.stream(ClienteRepository.class.getDeclaredMethods())
                        .anyMatch(m -> m.getName().equals("findByActivoTrueOrderByIdAsc")),
                "la lista de clientes activos debe pedirse ordenada por id ascendente");
    }
}
