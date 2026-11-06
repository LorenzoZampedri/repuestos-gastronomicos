package com.repuestos.controllers;

import com.repuestos.models.Pago;
import com.repuestos.models.Usuario;
import com.repuestos.services.NegocioException;
import com.repuestos.services.UsuarioService;
import com.repuestos.services.VentaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaControllerTest {

    @Mock private VentaService ventaService;
    @Mock private UsuarioService usuarioService;
    @InjectMocks private VentaController ventaController;

    @Test
    void registrarPagoVerificaPasswordYRegistraUsuarioQueAutoriza() {
        Usuario autorizador = Usuario.builder().id(7L).username("admin").nombre("Admin").build();
        Pago pagoGuardado = Pago.builder().id(11L).monto(new BigDecimal("150.00")).build();

        when(usuarioService.verificarPasswordActual("clave")).thenReturn(autorizador);
        when(ventaService.registrarPago(any(), any())).thenReturn(pagoGuardado);

        ventaController.registrarPago(5L, pago(new BigDecimal("150.00"), "EFECTIVO", "clave"));

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(ventaService).registrarPago(org.mockito.ArgumentMatchers.eq(5L), captor.capture());
        assertEquals(autorizador, captor.getValue().getUsuario());
    }

    @Test
    void registrarPagoConPasswordIncorrectaNoRegistraElPago() {
        when(usuarioService.verificarPasswordActual("incorrecta"))
                .thenThrow(new NegocioException(HttpStatus.FORBIDDEN, "Contraseña incorrecta"));

        NegocioException error = assertThrows(NegocioException.class,
                () -> ventaController.registrarPago(5L,
                        pago(new BigDecimal("150.00"), "EFECTIVO", "incorrecta")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(ventaService, never()).registrarPago(any(), any());
    }

    private VentaController.PagoRequest pago(BigDecimal monto, String metodoPago, String password) {
        VentaController.PagoRequest request = new VentaController.PagoRequest();
        request.setMonto(monto);
        request.setMetodoPago(metodoPago);
        request.setMoneda("ARS");
        request.setPassword(password);
        return request;
    }
}
