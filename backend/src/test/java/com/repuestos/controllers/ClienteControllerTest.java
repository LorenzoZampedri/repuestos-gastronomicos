package com.repuestos.controllers;

import com.repuestos.dto.ClienteResponse;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.services.ClienteService;
import com.repuestos.services.NegocioException;
import com.repuestos.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteControllerTest {

    @Mock private ClienteService clienteService;
    @Mock private UsuarioService usuarioService;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private ClienteController clienteController;

    @Test
    void ajustarSaldoRestarVerificaPasswordYRegistraUsuario() {
        Usuario autorizador = Usuario.builder().id(7L).username("admin").nombre("Admin").build();
        Cliente cliente = Cliente.builder().id(3L).nombre("Cliente").build();

        when(usuarioService.verificarPasswordActual("clave")).thenReturn(autorizador);
        when(clienteService.ajustarSaldo(eq(3L), eq(new BigDecimal("150.00")),
                eq("restar"), eq("EFECTIVO"), eq(autorizador))).thenReturn(cliente);

        ClienteResponse resultado = clienteController.ajustarSaldo(3L,
                ajuste(new BigDecimal("150.00"), "restar", "EFECTIVO", "clave")).getBody();

        assertEquals(cliente.getId(), resultado.id());
        assertEquals(cliente.getNombre(), resultado.nombre());
        verify(usuarioService).verificarPasswordActual("clave");
    }

    @Test
    void ajustarSaldoSumarNoVerificaPassword() {
        Cliente cliente = Cliente.builder().id(3L).nombre("Cliente").build();
        when(clienteService.ajustarSaldo(eq(3L), eq(new BigDecimal("80.00")),
                eq("sumar"), eq(null), eq(null))).thenReturn(cliente);

        ClienteResponse resultado = clienteController.ajustarSaldo(3L,
                ajuste(new BigDecimal("80.00"), "sumar", null, null)).getBody();

        assertEquals(cliente.getId(), resultado.id());
        verify(usuarioService, never()).verificarPasswordActual(any());
    }

    @Test
    void ajustarSaldoRestarConPasswordIncorrectaNoAjustaElSaldo() {
        when(usuarioService.verificarPasswordActual("incorrecta"))
                .thenThrow(new NegocioException(HttpStatus.FORBIDDEN, "Contraseña incorrecta"));

        NegocioException error = assertThrows(NegocioException.class,
                () -> clienteController.ajustarSaldo(3L,
                        ajuste(new BigDecimal("150.00"), "restar", "EFECTIVO", "incorrecta")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(clienteService, never()).ajustarSaldo(any(), any(), any(), any(), any());
    }

    private ClienteController.AjustarSaldoRequest ajuste(BigDecimal monto, String tipo,
                                                         String metodoPago, String password) {
        ClienteController.AjustarSaldoRequest request = new ClienteController.AjustarSaldoRequest();
        request.setMonto(monto);
        request.setTipo(tipo);
        request.setMetodoPago(metodoPago);
        request.setPassword(password);
        return request;
    }
}
