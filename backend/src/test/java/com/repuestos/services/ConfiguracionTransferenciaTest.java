package com.repuestos.services;

import com.repuestos.dto.DatosTransferenciaRequest;
import com.repuestos.dto.DatosTransferenciaResponse;
import com.repuestos.repositories.ConfiguracionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({ConfiguracionService.class, AuditoriaService.class})
class ConfiguracionTransferenciaTest {

    @Autowired private ConfiguracionService configuracionService;
    @Autowired private ConfiguracionRepository configuracionRepository;

    @Test
    void elSembradoInicialEsIdempotenteYNoPisaValoresEditados() {
        configuracionService.inicializarClaves();

        DatosTransferenciaResponse inicial = configuracionService.obtenerDatosTransferencia();
        assertEquals("0000003100098765432101", inicial.cvu());
        assertEquals("gastorepuestos.mp", inicial.alias());
        assertEquals("Repuestos Gastronomicos Once", inicial.titular());

        configuracionService.guardarDatosTransferencia(new DatosTransferenciaRequest(
                "1111111111111111111111", "nuevo.alias", "Nuevo Titular"));
        configuracionService.inicializarClaves();
        configuracionService.inicializarClaves();

        DatosTransferenciaResponse editado = configuracionService.obtenerDatosTransferencia();
        assertEquals("1111111111111111111111", editado.cvu());
        assertEquals("nuevo.alias", editado.alias());
        assertEquals("Nuevo Titular", editado.titular());
    }

    @Test
    void guardarRegistraElHistorialDeCadaClaveQueCambio() {
        configuracionService.inicializarClaves();

        configuracionService.guardarDatosTransferencia(new DatosTransferenciaRequest(
                "2222222222222222222222", "otro.alias", "Otro Titular"));

        assertEquals(1, configuracionService.listarHistorial(
                ConfiguracionService.CLAVE_TRANSFERENCIA_CVU).size());
        assertEquals(1, configuracionService.listarHistorial(
                ConfiguracionService.CLAVE_TRANSFERENCIA_ALIAS).size());
        assertEquals(1, configuracionService.listarHistorial(
                ConfiguracionService.CLAVE_TRANSFERENCIA_TITULAR).size());
        assertEquals("gastorepuestos.mp", configuracionService.listarHistorial(
                ConfiguracionService.CLAVE_TRANSFERENCIA_ALIAS).get(0).getValorAnterior());
        assertEquals("otro.alias", configuracionService.listarHistorial(
                ConfiguracionService.CLAVE_TRANSFERENCIA_ALIAS).get(0).getValorNuevo());
    }

    @Test
    void validaCvuAliasYTitular() {
        assertValidation(new DatosTransferenciaRequest(
                "00000031000987654321AB", "gastorepuestos.mp", "Titular"),
                "El CVU debe tener exactamente 22 dígitos numéricos");
        assertValidation(new DatosTransferenciaRequest(
                "000000310009876543210", "gastorepuestos.mp", "Titular"),
                "El CVU debe tener exactamente 22 dígitos numéricos");
        assertValidation(new DatosTransferenciaRequest(
                "0000003100098765432101", "alias con espacio", "Titular"),
                "El alias debe tener entre 6 y 20 caracteres, sin espacios, y solo puede "
                        + "contener letras, números, puntos y guiones");
        assertValidation(new DatosTransferenciaRequest(
                "0000003100098765432101", "corto", "Titular"),
                "El alias debe tener entre 6 y 20 caracteres, sin espacios, y solo puede "
                        + "contener letras, números, puntos y guiones");
        assertValidation(new DatosTransferenciaRequest(
                "0000003100098765432101", "gastorepuestos.mp", "  "),
                "El titular de la cuenta es obligatorio");
    }

    @Test
    void elEndpointGenericoNoPuedeEditarLasClavesDeTransferencia() {
        configuracionService.inicializarClaves();

        assertEquals(true, configuracionService.esClaveTransferencia(
                ConfiguracionService.CLAVE_TRANSFERENCIA_CVU));
        assertEquals(false, configuracionService.esClaveTransferencia("whatsapp_number"));
        assertEquals(1, configuracionRepository.findAll().stream()
                .filter(config -> ConfiguracionService.CLAVE_TRANSFERENCIA_CVU
                        .equals(config.getClave()))
                .count());
    }

    private void assertValidation(DatosTransferenciaRequest request, String mensaje) {
        NegocioException error = assertThrows(NegocioException.class,
                () -> configuracionService.guardarDatosTransferencia(request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(mensaje, error.getMessage());
    }
}
