package com.repuestos.services;

import com.repuestos.dto.CargarDeudaRequest;
import com.repuestos.dto.CargarDeudaResponse;
import com.repuestos.dto.CargoResponse;
import com.repuestos.dto.ClienteNuevoRequest;
import com.repuestos.dto.ClienteResponse;
import com.repuestos.dto.ComprasClienteResponse;
import com.repuestos.dto.GrupoComprasResponse;
import com.repuestos.dto.VentaResumenResponse;
import com.repuestos.models.CargoCuentaCorriente;
import com.repuestos.models.Cliente;
import com.repuestos.models.Usuario;
import com.repuestos.models.Venta;
import com.repuestos.repositories.CargoCuentaCorrienteRepository;
import com.repuestos.repositories.ClienteRepository;
import com.repuestos.repositories.UsuarioRepository;
import com.repuestos.repositories.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CuentaCorrienteService {

    private static final int LIMITE_COMPRAS_MAXIMO = 100;

    private final ClienteRepository clienteRepository;
    private final CargoCuentaCorrienteRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final ClienteService clienteService;
    private final CargoCuentaCorrienteService cargoCuentaCorrienteService;

    @Transactional
    public CargarDeudaResponse cargarDeuda(CargarDeudaRequest request) {
        return cargarDeuda(request, usuarioActual());
    }

    @Transactional
    public CargarDeudaResponse cargarDeuda(CargarDeudaRequest request, Usuario actor) {
        if (actor == null || actor.getRol() != Usuario.Rol.ADMIN) {
            throw new NegocioException(HttpStatus.FORBIDDEN,
                    "Solo un administrador puede cargar deudas");
        }
        if (request == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Datos inválidos");
        }
        boolean tieneCliente = request.clienteId() != null;
        boolean tieneClienteNuevo = request.clienteNuevo() != null;
        if (tieneCliente == tieneClienteNuevo) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Tenés que indicar un cliente existente o los datos de un cliente nuevo");
        }

        cargoCuentaCorrienteService.validarDatos(request.monto(), request.motivo());

        Cliente cliente;
        boolean clienteCreado = false;
        if (tieneClienteNuevo) {
            ClienteNuevoRequest nuevo = request.clienteNuevo();
            validarClienteNuevo(nuevo);
            verificarDuplicado(nuevo);
            cliente = clienteService.guardar(construirCliente(nuevo), "carga de deuda");
            clienteCreado = true;
        } else {
            cliente = clienteRepository.findById(request.clienteId())
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                            "Cliente no encontrado"));
            if (!Boolean.TRUE.equals(cliente.getActivo())) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "El cliente '" + cliente.getNombre() + "' (#" + cliente.getId()
                                + ") está dado de baja. Reactivalo para usar su cuenta corriente.",
                        "CLIENTE_INACTIVO",
                        Map.of("clienteId", cliente.getId(),
                                "clienteNombre", cliente.getNombre()));
            }
        }

        CargoCuentaCorriente cargo = cargoCuentaCorrienteService.registrar(
                cliente.getId(), request.monto(), request.motivo(), actor, clienteCreado);

        Cliente actualizado = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Cliente no encontrado"));
        boolean cuentaVinculada = usuarioRepository.existsByClienteId(actualizado.getId());
        return new CargarDeudaResponse(
                ClienteResponse.desde(actualizado, true, cuentaVinculada),
                CargoResponse.desde(cargo),
                clienteCreado);
    }

    @Transactional(readOnly = true)
    public List<CargoResponse> cargosDeCliente(Long clienteId) {
        buscarCliente(clienteId);
        return cargoRepository.findByClienteIdOrderByFechaDescIdDesc(clienteId).stream()
                .map(CargoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComprasClienteResponse comprasDeCliente(Long clienteId, int limite,
                                                   boolean incluirCuentaCorriente) {
        buscarCliente(clienteId);
        int limiteNormalizado = Math.max(0, Math.min(limite, LIMITE_COMPRAS_MAXIMO));

        List<Venta> ventas = new ArrayList<>(ventaRepository.findByClienteId(clienteId));
        ventas.sort(Comparator.comparing(Venta::getFecha)
                .thenComparing(Venta::getId)
                .reversed());

        List<Venta> realizadas = new ArrayList<>();
        List<Venta> pendientes = new ArrayList<>();
        List<Venta> canceladas = new ArrayList<>();
        for (Venta venta : ventas) {
            if (venta.getEstado() == Venta.EstadoVenta.PAGADA) {
                realizadas.add(venta);
            } else if (venta.getEstado() == Venta.EstadoVenta.ANULADA) {
                canceladas.add(venta);
            } else {
                pendientes.add(venta);
            }
        }

        GrupoComprasResponse grupoRealizadas = grupo(realizadas, limiteNormalizado,
                incluirCuentaCorriente);
        if (!incluirCuentaCorriente) {
            return new ComprasClienteResponse(clienteId, grupoRealizadas, null, null);
        }
        return new ComprasClienteResponse(clienteId, grupoRealizadas,
                grupo(pendientes, limiteNormalizado, true),
                grupo(canceladas, limiteNormalizado, true));
    }

    private GrupoComprasResponse grupo(List<Venta> ventas, int limite,
                                       boolean incluirCuentaCorriente) {
        List<VentaResumenResponse> items = limite == 0
                ? List.of()
                : ventas.stream()
                        .limit(limite)
                        .map(venta -> VentaResumenResponse.desde(venta, incluirCuentaCorriente))
                        .toList();
        return new GrupoComprasResponse(ventas.size(), items);
    }

    private Cliente buscarCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Cliente no encontrado"));
    }

    private Usuario usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return usuarioRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED,
                        "Usuario no autenticado"));
    }

    private void validarClienteNuevo(ClienteNuevoRequest nuevo) {
        String nombre = nuevo.nombre() == null ? "" : nuevo.nombre().trim();
        if (nombre.length() < 2 || nombre.length() > 200) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El nombre debe tener entre 2 y 200 caracteres");
        }
    }

    private void verificarDuplicado(ClienteNuevoRequest nuevo) {
        String nombreBuscado = normalizarNombre(nuevo.nombre());
        String telefonoBuscado = soloDigitos(nuevo.telefono());
        String emailBuscado = normalizarEmail(nuevo.email());
        String cuitBuscado = soloDigitos(nuevo.cuit());

        for (Cliente existente : clienteRepository.findAll()) {
            boolean mismoNombre = !nombreBuscado.isEmpty()
                    && nombreBuscado.equals(normalizarNombre(existente.getNombre()));
            boolean mismoTelefono = telefonoBuscado.length() >= 6
                    && telefonoBuscado.equals(soloDigitos(existente.getTelefono()));
            boolean mismoEmail = !emailBuscado.isEmpty()
                    && emailBuscado.equals(normalizarEmail(existente.getEmail()));
            boolean mismoCuit = !cuitBuscado.isEmpty()
                    && cuitBuscado.equals(soloDigitos(existente.getCuit()));
            if (mismoNombre || mismoTelefono || mismoEmail || mismoCuit) {
                lanzarDuplicado(existente);
            }
        }
    }

    private void lanzarDuplicado(Cliente existente) {
        String estado = Boolean.TRUE.equals(existente.getActivo())
                ? "Ya existe un cliente con esos datos"
                : "Ya existe un cliente dado de baja con esos datos";
        String codigo = Boolean.TRUE.equals(existente.getActivo())
                ? "CLIENTE_DUPLICADO" : "CLIENTE_INACTIVO";
        throw new NegocioException(HttpStatus.CONFLICT,
                estado + ": '" + existente.getNombre() + "' (#" + existente.getId() + ").",
                codigo,
                Map.of("clienteId", existente.getId(),
                        "clienteNombre", existente.getNombre(),
                        "clienteActivo", Boolean.TRUE.equals(existente.getActivo())));
    }

    private Cliente construirCliente(ClienteNuevoRequest nuevo) {
        return Cliente.builder()
                .nombre(nuevo.nombre().trim())
                .telefono(limpiarOpcional(nuevo.telefono()))
                .email(limpiarOpcional(nuevo.email()))
                .cuit(limpiarOpcional(nuevo.cuit()))
                .saldoCorriente(java.math.BigDecimal.ZERO)
                .activo(true)
                .build();
    }

    private String limpiarOpcional(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        return valor.trim();
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }
}
