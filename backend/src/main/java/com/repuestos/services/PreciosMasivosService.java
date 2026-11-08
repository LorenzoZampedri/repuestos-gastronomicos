package com.repuestos.services;

import com.repuestos.dto.LotePreciosDetalleResponse;
import com.repuestos.dto.LotePreciosItemResponse;
import com.repuestos.dto.LotePreciosResponse;
import com.repuestos.dto.PaginaLotesResponse;
import com.repuestos.dto.PrecioExcluido;
import com.repuestos.dto.PrecioItemPreview;
import com.repuestos.dto.PreciosAjusteRequest;
import com.repuestos.dto.PreciosAplicarResponse;
import com.repuestos.dto.PreciosEstadoResponse;
import com.repuestos.dto.PreciosParametrosResponse;
import com.repuestos.dto.PreciosRecalcularResponse;
import com.repuestos.dto.PreciosResumenResponse;
import com.repuestos.dto.PreciosRevertirResponse;
import com.repuestos.dto.PreciosVistaPreviaResponse;
import com.repuestos.models.AccionAuditoria;
import com.repuestos.models.Categoria;
import com.repuestos.models.LotePrecios;
import com.repuestos.models.LotePreciosItem;
import com.repuestos.models.Pedido;
import com.repuestos.models.Producto;
import com.repuestos.models.Usuario;
import com.repuestos.repositories.CategoriaRepository;
import com.repuestos.repositories.LotePreciosItemRepository;
import com.repuestos.repositories.LotePreciosRepository;
import com.repuestos.repositories.PedidoRepository;
import com.repuestos.repositories.ProductoRepository;
import com.repuestos.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Actualización masiva de precios con vista previa obligatoria, historial en
 * lotes inalterables y reversión. El precio de referencia es siempre
 * {@code Producto.precioUsd}; el precio en pesos se deriva con la cotización
 * vigente mediante {@link PreciosArs}.
 */
@Service
@RequiredArgsConstructor
public class PreciosMasivosService {

    public static final String TIPO_PORCENTAJE = "PORCENTAJE";
    public static final String TIPO_MONTO_USD = "MONTO_USD";

    public static final String ALCANCE_TODOS = "TODOS";
    public static final String ALCANCE_CATEGORIAS = "CATEGORIAS";
    public static final String ALCANCE_PRODUCTOS = "PRODUCTOS";

    public static final BigDecimal PORCENTAJE_MINIMO = new BigDecimal("-90");
    public static final BigDecimal PORCENTAJE_MAXIMO = new BigDecimal("100");
    public static final BigDecimal UMBRAL_VARIACION_ALTA = new BigDecimal("25");
    public static final BigDecimal MAXIMO_PRECIO_ARS = new BigDecimal("99999999.99");
    public static final BigDecimal MAXIMO_PRECIO_USD = new BigDecimal("99999999.99");
    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal REDONDEO_DEFECTO = new BigDecimal("0.01");
    private static final List<BigDecimal> REDONDEOS_VALIDOS =
            List.of(new BigDecimal("0.01"), new BigDecimal("0.10"),
                    new BigDecimal("0.50"), new BigDecimal("1.00"));

    private static final String MOTIVO_SIN_PRECIO_USD = "sin precio en dólares";
    private static final String MOTIVO_RESULTADO_INVALIDO = "resultado inválido";
    private static final String MOTIVO_EXCEDE_MAXIMO = "excede el máximo";
    private static final String MOTIVO_EDITADO_DESPUES = "el precio fue editado después del lote";

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final LotePreciosRepository lotePreciosRepository;
    private final LotePreciosItemRepository lotePreciosItemRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;
    private final AuditoriaService auditoriaService;

    // ------------------------------------------------------------------
    // Vista previa
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PreciosVistaPreviaResponse vistaPrevia(PreciosAjusteRequest request) {
        Ajuste ajuste = validar(request);
        BigDecimal cotizacion = cotizacionVigente();
        List<Producto> alcance = productosDelAlcance(ajuste);
        Calculo calculo = calcular(ajuste, alcance, cotizacion);
        if (calculo.items().isEmpty()) {
            throw sinEfecto();
        }
        String token = calcularToken(ajuste, calculo, cotizacion);
        long pedidosPendientes = contarPedidosPendientes(calculo);
        return new PreciosVistaPreviaResponse(
                token,
                cotizacion,
                parametros(ajuste),
                calculo.resumen(),
                itemsPreview(calculo),
                calculo.excluidos(),
                pedidosPendientes);
    }

    // ------------------------------------------------------------------
    // Aplicar
    // ------------------------------------------------------------------

    @Transactional
    public PreciosAplicarResponse aplicar(PreciosAjusteRequest request) {
        Ajuste ajuste = validar(request);
        if (request.token() == null || request.token().isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Generá la vista previa antes de aplicar");
        }
        BigDecimal cotizacion = cotizacionVigente();
        List<Long> idsAlcance = idsDelAlcance(ajuste);
        if (idsAlcance.isEmpty()) {
            throw sinEfecto();
        }

        // Bloqueo pesimista de escritura: nadie puede cambiar precios ni la
        // cotización mientras se recalcula y aplica el ajuste.
        List<Producto> bloqueados = productoRepository.findByIdsParaActualizar(idsAlcance);
        bloqueados.sort(Comparator.comparing(Producto::getId));

        Calculo calculo = calcular(ajuste, bloqueados, cotizacion);
        if (calculo.items().isEmpty()) {
            throw sinEfecto();
        }
        String tokenCalculado = calcularToken(ajuste, calculo, cotizacion);
        if (!tokenCalculado.equals(request.token().trim())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Los precios cambiaron desde la vista previa: generala de nuevo");
        }
        if (calculo.resumen().variacionAlta()
                && !Boolean.TRUE.equals(request.confirmarVariacionAlta())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La variación supera el 25%: confirmá la operación para continuar");
        }

        List<Producto> modificados = new ArrayList<>();
        for (ItemCalculo item : calculo.items()) {
            item.producto().setPrecioUsd(item.usdDespues());
            item.producto().setPrecio(item.arsDespues());
            modificados.add(item.producto());
        }
        productoRepository.saveAll(modificados);

        LotePrecios lote = construirLote(ajuste, cotizacion, calculo);
        lotePreciosRepository.save(lote);

        auditoriaService.registrar(AccionAuditoria.PRECIOS_ACTUALIZADOS_MASIVO,
                "LotePrecios", lote.getId(),
                resumenAuditoriaAplicar(ajuste, lote),
                detalleAuditoriaAplicar(lote, ajuste, calculo));

        return new PreciosAplicarResponse(lote.getId(), cotizacion,
                calculo.resumen(), calculo.excluidos());
    }

    // ------------------------------------------------------------------
    // Revertir
    // ------------------------------------------------------------------

    @Transactional
    public PreciosRevertirResponse revertir(Long loteId) {
        LotePrecios lote = lotePreciosRepository.findByIdParaActualizar(loteId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Lote de precios no encontrado"));
        if (LotePrecios.ESTADO_REVERTIDO.equals(lote.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Este lote ya fue revertido");
        }

        List<LotePreciosItem> items = lotePreciosItemRepository.findByLoteIdOrderByIdAsc(loteId);
        BigDecimal cotizacion = cotizacionVigente();

        List<Long> ids = items.stream().map(LotePreciosItem::getProductoId).toList();
        Map<Long, Producto> porId = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            productoRepository.findByIdsParaActualizar(ids)
                    .forEach(producto -> porId.put(producto.getId(), producto));
        }

        List<PrecioExcluido> omitidos = new ArrayList<>();
        List<Producto> modificados = new ArrayList<>();
        for (LotePreciosItem item : items) {
            Producto producto = porId.get(item.getProductoId());
            if (producto == null) {
                omitidos.add(new PrecioExcluido(item.getProductoId(),
                        item.getProductoNombre(), "producto no encontrado"));
                continue;
            }
            if (producto.getPrecioUsd() == null
                    || producto.getPrecioUsd().compareTo(item.getPrecioUsdDespues()) != 0) {
                omitidos.add(new PrecioExcluido(producto.getId(),
                        item.getProductoNombre(), MOTIVO_EDITADO_DESPUES));
                continue;
            }
            producto.setPrecioUsd(item.getPrecioUsdAntes());
            BigDecimal ars = PreciosArs.desdeUsd(item.getPrecioUsdAntes(), cotizacion);
            producto.setPrecio(ars != null ? ars : BigDecimal.ZERO);
            modificados.add(producto);
        }
        productoRepository.saveAll(modificados);

        lote.setEstado(LotePrecios.ESTADO_REVERTIDO);
        lote.setRevertidoPorUsername(usuarioActual());
        lote.setFechaReversion(LocalDateTime.now());
        lotePreciosRepository.save(lote);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("loteId", lote.getId());
        detalle.put("revertidos", modificados.size());
        detalle.put("omitidos", omitidos.size());
        detalle.put("motivosOmitidos", omitidos.stream().map(PrecioExcluido::motivo).distinct().toList());
        detalle.put("cotizacionUsada", cotizacion);
        auditoriaService.registrar(AccionAuditoria.PRECIOS_REVERTIDOS,
                "LotePrecios", lote.getId(),
                "Revirtió el lote #" + lote.getId() + ": "
                        + modificados.size() + " productos restaurados"
                        + (omitidos.isEmpty() ? ""
                        : " y " + omitidos.size() + " omitidos"),
                detalle);

        return new PreciosRevertirResponse(modificados.size(), omitidos);
    }

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PaginaLotesResponse listarLotes(int page, int size) {
        int pagina = Math.max(0, page);
        int tamanio = size <= 0 ? 20 : Math.min(size, TAMANIO_MAXIMO_PAGINA);
        Page<LotePrecios> resultado = lotePreciosRepository.findAll(
                PageRequest.of(pagina, tamanio,
                        Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));
        return new PaginaLotesResponse(
                resultado.getContent().stream().map(LotePreciosResponse::desde).toList(),
                resultado.getNumber(),
                resultado.getTotalPages(),
                resultado.getTotalElements(),
                resultado.getSize());
    }

    @Transactional(readOnly = true)
    public LotePreciosDetalleResponse detalleLote(Long id) {
        LotePrecios lote = lotePreciosRepository.findById(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "Lote de precios no encontrado"));
        List<LotePreciosItem> items = lotePreciosItemRepository.findByLoteIdOrderByIdAsc(id);
        return LotePreciosDetalleResponse.desde(lote, items);
    }

    // ------------------------------------------------------------------
    // Estado y recálculo manual
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PreciosEstadoResponse estado() {
        BigDecimal cotizacion = configuracionService.getCotizacionUsdArs();
        long sinPrecioUsd = 0;
        long desactualizados = 0;
        for (Producto producto : productoRepository.findByActivoTrueOrderByIdAsc()) {
            boolean tieneUsd = producto.getPrecioUsd() != null
                    && producto.getPrecioUsd().compareTo(BigDecimal.ZERO) > 0;
            if (!tieneUsd) {
                sinPrecioUsd++;
                continue;
            }
            if (cotizacion == null) {
                continue;
            }
            BigDecimal esperado = PreciosArs.desdeUsd(producto.getPrecioUsd(), cotizacion);
            BigDecimal actual = producto.getPrecio() == null ? BigDecimal.ZERO : producto.getPrecio();
            if (actual.compareTo(esperado) != 0) {
                desactualizados++;
            }
        }
        return new PreciosEstadoResponse(cotizacion, desactualizados, sinPrecioUsd);
    }

    @Transactional
    public PreciosRecalcularResponse recalcularArs() {
        BigDecimal cotizacion = cotizacionVigente();
        int cambiados = configuracionService.recalcularPreciosArs(cotizacion);
        if (cambiados > 0) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("origen", "boton");
            detalle.put("cotizacion", cotizacion);
            detalle.put("cantidadProductos", cambiados);
            auditoriaService.registrar(AccionAuditoria.PRECIOS_RECALCULADOS,
                    "Producto", null,
                    "Recalculó " + cambiados + " precios en pesos con la cotización "
                            + cotizacion.toPlainString(),
                    detalle);
        }
        return new PreciosRecalcularResponse(cambiados);
    }

    // ------------------------------------------------------------------
    // Validación y normalización
    // ------------------------------------------------------------------

    private Ajuste validar(PreciosAjusteRequest request) {
        if (request == null || request.alcance() == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El alcance es obligatorio");
        }

        String tipo = request.tipo() == null ? "" : request.tipo().trim().toUpperCase();
        if (!TIPO_PORCENTAJE.equals(tipo) && !TIPO_MONTO_USD.equals(tipo)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Tipo de ajuste inválido: usá PORCENTAJE o MONTO_USD");
        }

        BigDecimal valor = request.valor();
        if (valor == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El valor del ajuste es obligatorio");
        }
        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            throw sinEfecto();
        }
        if (TIPO_PORCENTAJE.equals(tipo)
                && (valor.compareTo(PORCENTAJE_MINIMO) < 0
                || valor.compareTo(PORCENTAJE_MAXIMO) > 0)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El porcentaje debe estar entre -90 y 100");
        }

        BigDecimal redondeo = REDONDEO_DEFECTO;
        if (request.redondeo() != null && !request.redondeo().isBlank()) {
            BigDecimal solicitado;
            try {
                solicitado = new BigDecimal(request.redondeo().trim());
            } catch (NumberFormatException e) {
                throw redondeoInvalido();
            }
            redondeo = REDONDEOS_VALIDOS.stream()
                    .filter(opcion -> opcion.compareTo(solicitado) == 0)
                    .findFirst()
                    .orElseThrow(PreciosMasivosService::redondeoInvalido);
        }

        String alcanceTipo = request.alcance().tipo() == null
                ? "" : request.alcance().tipo().trim().toUpperCase();
        List<Long> categoriaIds = normalizarIds(request.alcance().categoriaIds());
        List<Long> productoIds = normalizarIds(request.alcance().productoIds());
        switch (alcanceTipo) {
            case ALCANCE_TODOS -> {
                categoriaIds = List.of();
                productoIds = List.of();
            }
            case ALCANCE_CATEGORIAS -> {
                if (categoriaIds.isEmpty()) {
                    throw new NegocioException(HttpStatus.BAD_REQUEST,
                            "Elegí al menos una categoría");
                }
                productoIds = List.of();
            }
            case ALCANCE_PRODUCTOS -> {
                if (productoIds.isEmpty()) {
                    throw new NegocioException(HttpStatus.BAD_REQUEST,
                            "Elegí al menos un producto");
                }
                categoriaIds = List.of();
            }
            default -> throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Tipo de alcance inválido: usá TODOS, CATEGORIAS o PRODUCTOS");
        }

        String nota = request.nota() == null || request.nota().isBlank()
                ? null : request.nota().trim();
        if (nota != null && nota.length() > 200) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La nota no puede superar los 200 caracteres");
        }

        return new Ajuste(tipo, valor, redondeo, alcanceTipo,
                categoriaIds, productoIds, nota);
    }

    private static NegocioException redondeoInvalido() {
        return new NegocioException(HttpStatus.BAD_REQUEST,
                "Redondeo inválido: elegí 0.01, 0.10, 0.50 o 1.00");
    }

    private static NegocioException sinEfecto() {
        return new NegocioException(HttpStatus.BAD_REQUEST,
                "El ajuste no modifica ningún producto");
    }

    private List<Long> normalizarIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unicos = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                unicos.add(id);
            }
        }
        return unicos.stream().sorted().toList();
    }

    // ------------------------------------------------------------------
    // Alcance
    // ------------------------------------------------------------------

    private List<Producto> productosDelAlcance(Ajuste ajuste) {
        return switch (ajuste.alcanceTipo()) {
            case ALCANCE_CATEGORIAS -> productoRepository
                    .findByCategoriaIdInAndActivoTrueOrderByIdAsc(ajuste.categoriaIds());
            case ALCANCE_PRODUCTOS -> productoRepository
                    .findActivosByIds(ajuste.productoIds());
            default -> productoRepository.findByActivoTrueOrderByIdAsc();
        };
    }

    private List<Long> idsDelAlcance(Ajuste ajuste) {
        return productosDelAlcance(ajuste).stream()
                .map(Producto::getId)
                .sorted()
                .toList();
    }

    private String alcanceDescripcion(Ajuste ajuste) {
        return switch (ajuste.alcanceTipo()) {
            case ALCANCE_CATEGORIAS -> "Categorías: " + nombresCategorias(ajuste.categoriaIds());
            case ALCANCE_PRODUCTOS -> ajuste.productoIds().size() + " productos seleccionados";
            default -> "Todos los productos";
        };
    }

    private String nombresCategorias(List<Long> ids) {
        List<Categoria> categorias = categoriaRepository.findAllById(ids);
        Map<Long, String> porId = new LinkedHashMap<>();
        categorias.forEach(categoria -> porId.put(categoria.getId(), categoria.getNombre()));
        return ids.stream()
                .map(id -> porId.getOrDefault(id, "#" + id))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    // ------------------------------------------------------------------
    // Cálculo
    // ------------------------------------------------------------------

    private Calculo calcular(Ajuste ajuste, List<Producto> productos, BigDecimal cotizacion) {
        List<ItemCalculo> items = new ArrayList<>();
        List<PrecioExcluido> excluidos = new ArrayList<>();

        for (Producto producto : productos) {
            BigDecimal usdAntes = producto.getPrecioUsd();
            if (usdAntes == null || usdAntes.compareTo(BigDecimal.ZERO) <= 0) {
                excluidos.add(new PrecioExcluido(producto.getId(),
                        producto.getNombre(), MOTIVO_SIN_PRECIO_USD));
                continue;
            }
            BigDecimal usdDespues = calcularNuevoUsd(usdAntes, ajuste);
            if (usdDespues == null || usdDespues.compareTo(BigDecimal.ZERO) <= 0) {
                excluidos.add(new PrecioExcluido(producto.getId(),
                        producto.getNombre(), MOTIVO_RESULTADO_INVALIDO));
                continue;
            }
            if (usdDespues.compareTo(MAXIMO_PRECIO_USD) > 0
                    || PreciosArs.desdeUsd(usdDespues, cotizacion)
                    .compareTo(MAXIMO_PRECIO_ARS) > 0) {
                excluidos.add(new PrecioExcluido(producto.getId(),
                        producto.getNombre(), MOTIVO_EXCEDE_MAXIMO));
                continue;
            }
            if (usdDespues.compareTo(usdAntes) == 0) {
                continue;
            }
            BigDecimal variacion = usdDespues.subtract(usdAntes)
                    .multiply(CIEN)
                    .divide(usdAntes, 2, RoundingMode.HALF_UP);
            items.add(new ItemCalculo(producto, usdAntes, usdDespues,
                    producto.getPrecio(),
                    PreciosArs.desdeUsd(usdDespues, cotizacion),
                    variacion));
        }

        items.sort(Comparator.comparing(item -> item.producto().getId()));
        excluidos.sort(Comparator.comparing(PrecioExcluido::productoId));
        return new Calculo(items, excluidos, resumen(items, excluidos));
    }

    /**
     * PORCENTAJE: USD × (1 + valor/100). MONTO_USD: USD + valor. Después se
     * redondea (HALF_UP) al múltiplo más cercano de la tolerancia elegida y a
     * 2 decimales.
     */
    private BigDecimal calcularNuevoUsd(BigDecimal usdActual, Ajuste ajuste) {
        BigDecimal crudo;
        if (TIPO_PORCENTAJE.equals(ajuste.tipo())) {
            BigDecimal factor = BigDecimal.ONE.add(
                    ajuste.valor().divide(CIEN, 10, RoundingMode.HALF_UP));
            crudo = usdActual.multiply(factor);
        } else {
            crudo = usdActual.add(ajuste.valor());
        }
        BigDecimal enMultiplos = crudo.divide(ajuste.redondeo(), 0, RoundingMode.HALF_UP)
                .multiply(ajuste.redondeo());
        return enMultiplos.setScale(2, RoundingMode.HALF_UP);
    }

    private PreciosResumenResponse resumen(List<ItemCalculo> items,
                                           List<PrecioExcluido> excluidos) {
        if (items.isEmpty()) {
            return new PreciosResumenResponse(0, excluidos.size(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    false);
        }
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal minima = items.get(0).variacion();
        BigDecimal maxima = items.get(0).variacion();
        boolean alta = false;
        for (ItemCalculo item : items) {
            suma = suma.add(item.variacion());
            if (item.variacion().compareTo(minima) < 0) {
                minima = item.variacion();
            }
            if (item.variacion().compareTo(maxima) > 0) {
                maxima = item.variacion();
            }
            if (item.variacion().abs().compareTo(UMBRAL_VARIACION_ALTA) > 0) {
                alta = true;
            }
        }
        BigDecimal promedio = suma.divide(BigDecimal.valueOf(items.size()), 2,
                RoundingMode.HALF_UP);
        return new PreciosResumenResponse(items.size(), excluidos.size(),
                promedio, minima, maxima, alta);
    }

    // ------------------------------------------------------------------
    // Token de la vista previa
    // ------------------------------------------------------------------

    private String calcularToken(Ajuste ajuste, Calculo calculo, BigDecimal cotizacion) {
        StringBuilder base = new StringBuilder();
        base.append(ajuste.tipo()).append('|')
                .append(ajuste.valor().toPlainString()).append('|')
                .append(ajuste.redondeo().toPlainString()).append('|')
                .append(ajuste.alcanceTipo()).append('|')
                .append(String.join(",", ajuste.categoriaIds().stream()
                        .map(String::valueOf).toList())).append('|')
                .append(String.join(",", ajuste.productoIds().stream()
                        .map(String::valueOf).toList())).append('|')
                .append(cotizacion == null ? "" : cotizacion.toPlainString());
        for (ItemCalculo item : calculo.items()) {
            base.append('|').append(item.producto().getId()).append(':')
                    .append(item.usdAntes().toPlainString());
        }
        return sha256(base.toString());
    }

    private String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el token de la vista previa", e);
        }
    }

    // ------------------------------------------------------------------
    // Lote y auditoría
    // ------------------------------------------------------------------

    private LotePrecios construirLote(Ajuste ajuste, BigDecimal cotizacion, Calculo calculo) {
        LotePrecios lote = LotePrecios.builder()
                .fecha(LocalDateTime.now())
                .usuarioId(usuarioActualId())
                .usuarioUsername(usuarioActual())
                .tipoAjuste(ajuste.tipo())
                .valor(ajuste.valor())
                .redondeo(ajuste.redondeo())
                .alcance(alcanceDescripcion(ajuste))
                .nota(ajuste.nota())
                .cotizacionUsada(cotizacion)
                .cantidadProductos(calculo.items().size())
                .cantidadOmitidos(calculo.excluidos().size())
                .estado(LotePrecios.ESTADO_APLICADO)
                .build();
        for (ItemCalculo item : calculo.items()) {
            lote.agregarItem(LotePreciosItem.builder()
                    .productoId(item.producto().getId())
                    .productoNombre(item.producto().getNombre())
                    .precioUsdAntes(item.usdAntes())
                    .precioUsdDespues(item.usdDespues())
                    .precioArsAntes(item.arsAntes())
                    .precioArsDespues(item.arsDespues())
                    .build());
        }
        return lote;
    }

    private String resumenAuditoriaAplicar(Ajuste ajuste, LotePrecios lote) {
        String accion = TIPO_PORCENTAJE.equals(ajuste.tipo())
                ? (ajuste.valor().compareTo(BigDecimal.ZERO) > 0 ? "Aumentó " : "Bajó ")
                    + ajuste.valor().abs().toPlainString() + "%"
                : (ajuste.valor().compareTo(BigDecimal.ZERO) > 0 ? "Aumentó U$S " : "Bajó U$S ")
                    + ajuste.valor().abs().toPlainString();
        return accion + " el precio de " + lote.getCantidadProductos()
                + " productos (" + lote.getAlcance() + ") · lote #" + lote.getId();
    }

    private Map<String, Object> detalleAuditoriaAplicar(LotePrecios lote, Ajuste ajuste,
                                                        Calculo calculo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("loteId", lote.getId());
        detalle.put("tipo", ajuste.tipo());
        detalle.put("valor", ajuste.valor());
        detalle.put("redondeo", ajuste.redondeo());
        detalle.put("alcance", lote.getAlcance());
        detalle.put("nota", lote.getNota());
        detalle.put("cotizacion", lote.getCotizacionUsada());
        detalle.put("cantidadProductos", lote.getCantidadProductos());
        detalle.put("cantidadOmitidos", lote.getCantidadOmitidos());
        detalle.put("variacionPromedio", calculo.resumen().variacionPromedio());
        detalle.put("variacionMinima", calculo.resumen().variacionMinima());
        detalle.put("variacionMaxima", calculo.resumen().variacionMaxima());
        detalle.put("variacionAlta", calculo.resumen().variacionAlta());
        return detalle;
    }

    // ------------------------------------------------------------------
    // Comunes
    // ------------------------------------------------------------------

    private PreciosParametrosResponse parametros(Ajuste ajuste) {
        return new PreciosParametrosResponse(
                ajuste.tipo(),
                ajuste.valor(),
                ajuste.redondeo(),
                ajuste.alcanceTipo(),
                ajuste.categoriaIds(),
                ajuste.productoIds(),
                alcanceDescripcion(ajuste),
                ajuste.nota());
    }

    private List<PrecioItemPreview> itemsPreview(Calculo calculo) {
        return calculo.items().stream()
                .map(item -> new PrecioItemPreview(
                        item.producto().getId(),
                        item.producto().getNombre(),
                        item.producto().getCategoria() == null
                                ? null : item.producto().getCategoria().getNombre(),
                        item.usdAntes(),
                        item.usdDespues(),
                        item.arsAntes(),
                        item.arsDespues(),
                        item.variacion()))
                .toList();
    }

    private long contarPedidosPendientes(Calculo calculo) {
        if (calculo.items().isEmpty()) {
            return 0;
        }
        List<Long> ids = calculo.items().stream()
                .map(item -> item.producto().getId())
                .toList();
        return pedidoRepository.countPendientesConProductos(
                Pedido.EstadoPedido.PENDIENTE, ids);
    }

    private BigDecimal cotizacionVigente() {
        BigDecimal cotizacion = configuracionService.getCotizacionUsdArs();
        if (cotizacion == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "No hay una cotización configurada: cargá la cotización del dólar antes de ajustar precios");
        }
        return cotizacion;
    }

    private String usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null
                ? null : authentication.getName();
    }

    private Long usuarioActualId() {
        String username = usuarioActual();
        if (username == null || username.isBlank()) {
            return null;
        }
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(Usuario::getId)
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Registros internos
    // ------------------------------------------------------------------

    private record Ajuste(String tipo, BigDecimal valor, BigDecimal redondeo,
                          String alcanceTipo, List<Long> categoriaIds,
                          List<Long> productoIds, String nota) {
    }

    private record ItemCalculo(Producto producto, BigDecimal usdAntes,
                               BigDecimal usdDespues, BigDecimal arsAntes,
                               BigDecimal arsDespues, BigDecimal variacion) {
    }

    private record Calculo(List<ItemCalculo> items, List<PrecioExcluido> excluidos,
                           PreciosResumenResponse resumen) {
    }
}
