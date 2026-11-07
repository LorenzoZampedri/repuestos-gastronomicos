import React, { useCallback, useEffect, useState } from 'react';
import {
  Percent,
  Sparkles,
  AlertTriangle,
  RefreshCw,
  Eye,
  Undo2,
  History,
  ChevronLeft,
  ChevronRight,
  Info,
} from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';
import EmptyState from '../components/EmptyState';
import { formatMoney, formatNumber } from '../utils/format';

const POR_PAGINA = 10;

const TIPOS = [
  { codigo: 'PORCENTAJE', etiqueta: 'Porcentaje (%)' },
  { codigo: 'MONTO_USD', etiqueta: 'Monto en USD' },
];

const REDONDEOS = ['0.01', '0.10', '0.50', '1.00'];

const ALCANCES = [
  { codigo: 'TODOS', etiqueta: 'Todos los productos' },
  { codigo: 'CATEGORIAS', etiqueta: 'Por categorías' },
  { codigo: 'PRODUCTOS', etiqueta: 'Productos seleccionados' },
];

const FORM_INICIAL = {
  tipo: 'PORCENTAJE',
  valor: '',
  redondeo: '0.01',
  alcanceTipo: 'TODOS',
  categoriaIds: [],
  productoIds: [],
  nota: '',
};

const etiquetaAjuste = (lote) => {
  const valor = formatNumber(lote.valor);
  return lote.tipoAjuste === 'PORCENTAJE'
    ? `${lote.valor > 0 ? '+' : ''}${valor}%`
    : `U$S ${lote.valor > 0 ? '+' : ''}${valor}`;
};

const Precios = () => {
  const [estado, setEstado] = useState(null);
  const [estadoLoading, setEstadoLoading] = useState(true);
  const [recalculando, setRecalculando] = useState(false);

  const [categorias, setCategorias] = useState([]);
  const [productos, setProductos] = useState([]);
  const [busquedaProducto, setBusquedaProducto] = useState('');

  const [form, setForm] = useState(FORM_INICIAL);
  const [preview, setPreview] = useState(null);
  const [generando, setGenerando] = useState(false);
  const [aplicando, setAplicando] = useState(false);
  const [confirmacion, setConfirmacion] = useState('');

  const [lotes, setLotes] = useState([]);
  const [pagina, setPagina] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [total, setTotal] = useState(0);
  const [lotesLoading, setLotesLoading] = useState(true);
  const [revertiendo, setRevertiendo] = useState(false);
  const [loteDetalle, setLoteDetalle] = useState(null);
  const [detalleAbierto, setDetalleAbierto] = useState(false);
  const [detalleLoading, setDetalleLoading] = useState(false);

  const fetchEstado = useCallback(async () => {
    setEstadoLoading(true);
    try {
      const response = await api.get('/api/precios/estado');
      setEstado(response.data);
    } catch (error) {
      toast.error('Error al cargar el estado de los precios');
    } finally {
      setEstadoLoading(false);
    }
  }, []);

  const fetchLotes = useCallback(async () => {
    setLotesLoading(true);
    try {
      const response = await api.get('/api/precios/lotes', {
        params: { page: pagina, size: POR_PAGINA },
      });
      const data = response.data || {};
      setLotes(data.content || []);
      setTotalPaginas(data.totalPages || 0);
      setTotal(data.totalElements || 0);
    } catch (error) {
      toast.error('Error al cargar el historial de lotes');
      setLotes([]);
      setTotalPaginas(0);
      setTotal(0);
    } finally {
      setLotesLoading(false);
    }
  }, [pagina]);

  useEffect(() => {
    fetchEstado();
    fetchLotes();
    api.get('/api/categorias')
      .then((response) => setCategorias(response.data || []))
      .catch(() => setCategorias([]));
    api.get('/api/productos')
      .then((response) => setProductos(response.data || []))
      .catch(() => setProductos([]));
  }, [fetchEstado, fetchLotes]);

  const cambiar = (patch) => {
    setForm((prev) => ({ ...prev, ...patch }));
    setPreview(null);
    setConfirmacion('');
  };

  const alternar = (campo, id) => {
    const actual = form[campo];
    const siguiente = actual.includes(id)
      ? actual.filter((x) => x !== id)
      : [...actual, id];
    cambiar({ [campo]: siguiente });
  };

  const cuerpo = () => ({
    tipo: form.tipo,
    valor: Number(form.valor),
    redondeo: form.redondeo,
    alcance: {
      tipo: form.alcanceTipo,
      categoriaIds: form.alcanceTipo === 'CATEGORIAS' ? form.categoriaIds : null,
      productoIds: form.alcanceTipo === 'PRODUCTOS' ? form.productoIds : null,
    },
    nota: form.nota.trim() || null,
  });

  const generarPreview = async (event) => {
    event?.preventDefault();
    if (form.valor === '' || Number.isNaN(Number(form.valor))) {
      toast.error('Ingresá un valor de ajuste');
      return;
    }
    if (form.alcanceTipo === 'CATEGORIAS' && form.categoriaIds.length === 0) {
      toast.error('Elegí al menos una categoría');
      return;
    }
    if (form.alcanceTipo === 'PRODUCTOS' && form.productoIds.length === 0) {
      toast.error('Elegí al menos un producto');
      return;
    }
    setGenerando(true);
    try {
      const response = await api.post('/api/precios/vista-previa', cuerpo());
      setPreview(response.data);
      setConfirmacion('');
    } catch (error) {
      setPreview(null);
      toast.error(error.response?.data?.message || 'No se pudo generar la vista previa');
    } finally {
      setGenerando(false);
    }
  };

  const limpiarPreview = () => {
    setPreview(null);
    setConfirmacion('');
  };

  const aplicar = async () => {
    if (!preview) return;
    const alta = preview.resumen?.variacionAlta;
    if (alta && confirmacion !== 'CONFIRMAR') {
      toast.error('Escribí CONFIRMAR para continuar');
      return;
    }
    setAplicando(true);
    try {
      const response = await api.post('/api/precios/aplicar', {
        ...cuerpo(),
        token: preview.token,
        confirmarVariacionAlta: alta ? true : null,
      });
      const resumen = response.data.resumen;
      toast.success(
        `Lote #${response.data.loteId} aplicado: ${resumen.productosAfectados} productos actualizados`
      );
      limpiarPreview();
      setPagina(0);
      fetchEstado();
      fetchLotes();
    } catch (error) {
      const status = error.response?.status;
      toast.error(error.response?.data?.message || 'No se pudo aplicar el ajuste');
      if (status === 409) limpiarPreview();
    } finally {
      setAplicando(false);
    }
  };

  const recalcularArs = async () => {
    setRecalculando(true);
    try {
      const response = await api.post('/api/precios/recalcular-ars');
      const cambiados = response.data?.cambiados || 0;
      toast.success(
        cambiados > 0
          ? `Se recalcularon ${cambiados} precios en pesos`
          : 'Todos los precios en pesos ya están actualizados'
      );
      fetchEstado();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudieron recalcular los precios');
    } finally {
      setRecalculando(false);
    }
  };

  const revertir = async (lote) => {
    const confirmado = window.confirm(
      `¿Revertir el lote #${lote.id}? Los precios de sus productos volverán a los valores anteriores.`
    );
    if (!confirmado) return;
    setRevertiendo(true);
    try {
      const response = await api.post(`/api/precios/lotes/${lote.id}/revertir`);
      const omitidos = response.data.omitidos?.length || 0;
      toast.success(
        `Lote revertido: ${response.data.revertidos} productos restaurados` +
          (omitidos > 0 ? ` y ${omitidos} omitidos` : '')
      );
      fetchEstado();
      fetchLotes();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo revertir el lote');
    } finally {
      setRevertiendo(false);
    }
  };

  const abrirDetalle = async (lote) => {
    setDetalleAbierto(true);
    setDetalleLoading(true);
    setLoteDetalle(null);
    try {
      const response = await api.get(`/api/precios/lotes/${lote.id}`);
      setLoteDetalle(response.data);
    } catch (error) {
      toast.error('No se pudo cargar el detalle del lote');
      setDetalleAbierto(false);
    } finally {
      setDetalleLoading(false);
    }
  };

  const productosFiltrados = productos.filter((p) =>
    p.nombre.toLowerCase().includes(busquedaProducto.trim().toLowerCase())
  );

  const variacionAlta = !!preview?.resumen?.variacionAlta;
  const confirmacionOk = !variacionAlta || confirmacion === 'CONFIRMAR';

  const colorVariacion = (valor) => {
    if (valor == null) return 'text-gray-500';
    if (valor > 0) return 'text-green-700';
    if (valor < 0) return 'text-red-700';
    return 'text-gray-500';
  };

  const paginaActual = pagina + 1;
  const totalPaginasSeguro = Math.max(totalPaginas, total > 0 ? 1 : 0);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Precios</h1>
        <p className="text-gray-500">
          Actualización masiva en dólares con vista previa, historial en lotes y precios en pesos
        </p>
      </div>

      {/* Estado de los precios en pesos */}
      <div className="card">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center">
              <Percent className="w-5 h-5 text-primary-800" aria-hidden="true" />
            </div>
            <div>
              <h2 className="font-semibold text-gray-900">Precios en pesos</h2>
              <p className="text-sm text-gray-500">
                Precio en pesos = precio en USD × cotización vigente
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={recalcularArs}
            disabled={recalculando || estadoLoading}
            className="btn-secondary flex items-center justify-center gap-2 disabled:opacity-60"
          >
            <RefreshCw
              className={`w-4 h-4 ${recalculando ? 'animate-spin' : ''}`}
              aria-hidden="true"
            />
            {recalculando ? 'Recalculando…' : 'Recalcular en pesos'}
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-4">
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-sm text-gray-500">Cotización vigente</p>
            <p className="text-xl font-bold text-gray-900 mt-1">
              {estadoLoading || !estado ? '…' : `$${formatNumber(estado.cotizacion)}`}
            </p>
            <p className="text-xs text-gray-500 mt-1">por 1 USD</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-sm text-gray-500">Desactualizados</p>
            <p
              className={`text-xl font-bold mt-1 ${
                estado?.productosDesactualizados > 0 ? 'text-amber-600' : 'text-gray-900'
              }`}
            >
              {estadoLoading || !estado ? '…' : estado.productosDesactualizados}
            </p>
            <p className="text-xs text-gray-500 mt-1">no coinciden con la cotización</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-sm text-gray-500">Sin precio en dólares</p>
            <p className="text-xl font-bold text-gray-900 mt-1">
              {estadoLoading || !estado ? '…' : estado.productosSinPrecioUsd}
            </p>
            <p className="text-xs text-gray-500 mt-1">no entran a los ajustes masivos</p>
          </div>
        </div>
      </div>

      {/* Formulario de ajuste */}
      <form onSubmit={generarPreview} className="card space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center">
            <Sparkles className="w-5 h-5 text-primary-800" aria-hidden="true" />
          </div>
          <div>
            <h2 className="font-semibold text-gray-900">Nuevo ajuste masivo</h2>
            <p className="text-sm text-gray-500">
              El precio de referencia es siempre el precio en dólares
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label className="label" htmlFor="precios-tipo">Tipo de ajuste</label>
            <select
              id="precios-tipo"
              className="input"
              value={form.tipo}
              onChange={(e) => cambiar({ tipo: e.target.value })}
            >
              {TIPOS.map((t) => (
                <option key={t.codigo} value={t.codigo}>{t.etiqueta}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="precios-valor">Valor</label>
            <input
              id="precios-valor"
              type="number"
              className="input"
              value={form.valor}
              onChange={(e) => cambiar({ valor: e.target.value })}
              placeholder={form.tipo === 'PORCENTAJE' ? 'Ej: 10 o -5' : 'Ej: 1.50'}
              step="0.01"
              required
            />
          </div>
          <div>
            <label className="label" htmlFor="precios-redondeo">Redondeo</label>
            <select
              id="precios-redondeo"
              className="input"
              value={form.redondeo}
              onChange={(e) => cambiar({ redondeo: e.target.value })}
            >
              {REDONDEOS.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="precios-alcance">Alcance</label>
            <select
              id="precios-alcance"
              className="input"
              value={form.alcanceTipo}
              onChange={(e) => cambiar({ alcanceTipo: e.target.value })}
            >
              {ALCANCES.map((a) => (
                <option key={a.codigo} value={a.codigo}>{a.etiqueta}</option>
              ))}
            </select>
          </div>
        </div>

        {form.alcanceTipo === 'CATEGORIAS' && (
          <div>
            <p className="label">Categorías</p>
            <div className="flex flex-wrap gap-2">
              {categorias.map((cat) => {
                const activa = form.categoriaIds.includes(cat.id);
                return (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => alternar('categoriaIds', cat.id)}
                    className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-colors ${
                      activa
                        ? 'bg-primary-800 text-white border-primary-800'
                        : 'bg-white text-gray-700 border-gray-300 hover:border-primary-500'
                    }`}
                    aria-pressed={activa}
                  >
                    {cat.nombre}
                  </button>
                );
              })}
              {categorias.length === 0 && (
                <p className="text-sm text-gray-500">No hay categorías cargadas</p>
              )}
            </div>
          </div>
        )}

        {form.alcanceTipo === 'PRODUCTOS' && (
          <div>
            <label className="label" htmlFor="precios-buscar-producto">Productos</label>
            <input
              id="precios-buscar-producto"
              type="search"
              className="input mb-2"
              value={busquedaProducto}
              onChange={(e) => setBusquedaProducto(e.target.value)}
              placeholder="Buscar producto…"
            />
            <div className="border border-gray-200 rounded-lg max-h-56 overflow-y-auto divide-y divide-gray-50">
              {productosFiltrados.map((p) => {
                const activo = form.productoIds.includes(p.id);
                return (
                  <label
                    key={p.id}
                    className="flex items-center gap-3 px-3 py-2 text-sm hover:bg-gray-50 cursor-pointer"
                  >
                    <input
                      type="checkbox"
                      checked={activo}
                      onChange={() => alternar('productoIds', p.id)}
                      className="rounded border-gray-300 text-primary-800 focus:ring-primary-500"
                    />
                    <span className="flex-1 text-gray-800">{p.nombre}</span>
                    {p.precioUsd != null && (
                      <span className="text-gray-500 font-mono">
                        {formatMoney(p.precioUsd, 'USD')}
                      </span>
                    )}
                  </label>
                );
              })}
              {productosFiltrados.length === 0 && (
                <p className="text-sm text-gray-500 px-3 py-3">Sin productos</p>
              )}
            </div>
            <p className="text-xs text-gray-500 mt-1">
              {form.productoIds.length} seleccionados
            </p>
          </div>
        )}

        <div>
          <label className="label" htmlFor="precios-nota">Nota (opcional)</label>
          <input
            id="precios-nota"
            type="text"
            className="input"
            value={form.nota}
            onChange={(e) => cambiar({ nota: e.target.value })}
            placeholder="Ej: Revisión mensual de lista"
            maxLength={200}
          />
        </div>

        <div className="flex justify-end">
          <button type="submit" disabled={generando} className="btn-primary flex items-center gap-2 disabled:opacity-60">
            {generando ? (
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
            ) : (
              <Sparkles className="w-4 h-4" aria-hidden="true" />
            )}
            Generar vista previa
          </button>
        </div>
      </form>

      {/* Vista previa */}
      {preview && (
        <div className="card space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="font-semibold text-gray-900">Vista previa</h2>
              <p className="text-sm text-gray-500">
                {preview.parametros?.alcanceDescripcion} · cotización $
                {formatNumber(preview.cotizacion)} al generar
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <span className="px-3 py-1 rounded-full text-sm font-medium bg-green-100 text-green-800">
                {preview.resumen.productosAfectados} a modificar
              </span>
              <span className="px-3 py-1 rounded-full text-sm font-medium bg-gray-100 text-gray-700">
                {preview.resumen.productosExcluidos} excluidos
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-gray-500">Variación promedio</p>
              <p className={`font-semibold ${colorVariacion(preview.resumen.variacionPromedio)}`}>
                {formatNumber(preview.resumen.variacionPromedio)}%
              </p>
            </div>
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-gray-500">Mínima</p>
              <p className={`font-semibold ${colorVariacion(preview.resumen.variacionMinima)}`}>
                {formatNumber(preview.resumen.variacionMinima)}%
              </p>
            </div>
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-gray-500">Máxima</p>
              <p className={`font-semibold ${colorVariacion(preview.resumen.variacionMaxima)}`}>
                {formatNumber(preview.resumen.variacionMaxima)}%
              </p>
            </div>
            <div className="bg-gray-50 rounded-lg p-3">
              <p className="text-gray-500">Pedidos pendientes</p>
              <p className="font-semibold text-gray-900">
                {preview.pedidosPendientesAfectados}
              </p>
            </div>
          </div>

          {preview.pedidosPendientesAfectados > 0 && (
            <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-800">
              <Info className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
              Hay {preview.pedidosPendientesAfectados} pedidos pendientes con productos afectados.
            </div>
          )}

          <div className="overflow-x-auto border border-gray-100 rounded-lg">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100">
                  <th className="text-left py-2 px-3 font-medium text-gray-500">Producto</th>
                  <th className="text-left py-2 px-3 font-medium text-gray-500">Categoría</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-500">USD antes</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-500">USD después</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-500">ARS antes</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-500">ARS después</th>
                  <th className="text-right py-2 px-3 font-medium text-gray-500">Variación</th>
                </tr>
              </thead>
              <tbody>
                {preview.items.map((item) => (
                  <tr key={item.productoId} className="border-b border-gray-50 last:border-0">
                    <td className="py-2 px-3 text-gray-900">{item.productoNombre}</td>
                    <td className="py-2 px-3 text-gray-500">{item.categoria || '—'}</td>
                    <td className="py-2 px-3 text-right font-mono text-gray-500">
                      {formatMoney(item.precioUsdAntes, 'USD')}
                    </td>
                    <td className="py-2 px-3 text-right font-mono font-semibold text-gray-900">
                      {formatMoney(item.precioUsdDespues, 'USD')}
                    </td>
                    <td className="py-2 px-3 text-right font-mono text-gray-500">
                      {formatMoney(item.precioArsAntes, 'ARS')}
                    </td>
                    <td className="py-2 px-3 text-right font-mono font-semibold text-gray-900">
                      {formatMoney(item.precioArsDespues, 'ARS')}
                    </td>
                    <td className={`py-2 px-3 text-right font-medium ${colorVariacion(item.variacionPorcentaje)}`}>
                      {formatNumber(item.variacionPorcentaje)}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {preview.excluidos.length > 0 && (
            <div>
              <p className="text-sm font-medium text-gray-700 mb-2">Excluidos del ajuste</p>
              <div className="flex flex-wrap gap-2">
                {preview.excluidos.map((excluido) => (
                  <span
                    key={excluido.productoId}
                    className="px-2 py-1 rounded-full text-xs bg-gray-100 text-gray-700"
                    title={excluido.motivo}
                  >
                    {excluido.productoNombre} · {excluido.motivo}
                  </span>
                ))}
              </div>
            </div>
          )}

          {variacionAlta && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 space-y-3">
              <div className="flex items-start gap-2 text-sm text-amber-800">
                <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
                <span>
                  La variación supera el 25%. Escribí <strong>CONFIRMAR</strong> para habilitar la
                  aplicación.
                </span>
              </div>
              <input
                type="text"
                className="input max-w-xs font-mono uppercase"
                value={confirmacion}
                onChange={(e) => setConfirmacion(e.target.value)}
                placeholder="CONFIRMAR"
                aria-label="Confirmación de variación alta"
              />
            </div>
          )}

          <div className="flex flex-col sm:flex-row justify-end gap-3">
            <button type="button" onClick={limpiarPreview} className="btn-secondary">
              Descartar
            </button>
            <button
              type="button"
              onClick={aplicar}
              disabled={aplicando || !confirmacionOk}
              className="btn-primary flex items-center justify-center gap-2 disabled:opacity-60"
            >
              {aplicando ? (
                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
              ) : (
                <Sparkles className="w-4 h-4" aria-hidden="true" />
              )}
              Aplicar ajuste
            </button>
          </div>
        </div>
      )}

      {/* Historial de lotes */}
      <div className="card overflow-hidden">
        <div className="flex items-center justify-between px-4 pt-4 pb-2">
          <div className="flex items-center gap-2">
            <History className="w-5 h-5 text-gray-400" aria-hidden="true" />
            <h2 className="font-semibold text-gray-900">Historial de lotes</h2>
            {total > 0 && (
              <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded-full">
                {total}
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={fetchLotes}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
            aria-label="Actualizar historial"
            title="Actualizar"
          >
            <RefreshCw className="w-4 h-4" aria-hidden="true" />
          </button>
        </div>

        {lotesLoading ? (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
          </div>
        ) : lotes.length === 0 ? (
          <EmptyState
            icon={History}
            title="Todavía no hay lotes"
            description="Generá una vista previa y aplicá un ajuste para ver el historial."
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Fecha</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Usuario</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Ajuste</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Alcance</th>
                    <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Productos</th>
                    <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Cotización</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Estado</th>
                    <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {lotes.map((lote) => (
                    <tr key={lote.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="py-3 px-4 text-sm text-gray-600 whitespace-nowrap">
                        {new Date(lote.fecha).toLocaleString('es-AR')}
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-900">{lote.usuarioUsername || '—'}</td>
                      <td className="py-3 px-4 text-sm font-medium text-gray-900 whitespace-nowrap">
                        {etiquetaAjuste(lote)}
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-600 max-w-[12rem]">
                        <span className="line-clamp-2" title={lote.alcance}>{lote.alcance}</span>
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-600 text-right whitespace-nowrap">
                        {lote.cantidadProductos}
                        {lote.cantidadOmitidos > 0 && (
                          <span className="text-gray-400"> / {lote.cantidadOmitidos} om.</span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-600 text-right font-mono">
                        ${formatNumber(lote.cotizacionUsada)}
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`px-2 py-1 text-xs font-medium rounded-full ${
                            lote.estado === 'APLICADO'
                              ? 'bg-green-100 text-green-800'
                              : 'bg-gray-100 text-gray-600'
                          }`}
                        >
                          {lote.estado === 'APLICADO' ? 'Aplicado' : 'Revertido'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <button
                          type="button"
                          onClick={() => abrirDetalle(lote)}
                          className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors"
                          aria-label={`Ver detalle del lote #${lote.id}`}
                          title="Ver detalle"
                        >
                          <Eye className="w-4 h-4" aria-hidden="true" />
                        </button>
                        {lote.estado === 'APLICADO' && (
                          <button
                            type="button"
                            onClick={() => revertir(lote)}
                            disabled={revertiendo}
                            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50"
                            aria-label={`Revertir el lote #${lote.id}`}
                            title="Revertir lote"
                          >
                            <Undo2 className="w-4 h-4" aria-hidden="true" />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPaginasSeguro > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
                <p className="text-sm text-gray-500">
                  Página {paginaActual} de {totalPaginasSeguro}
                </p>
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setPagina((p) => Math.max(0, p - 1))}
                    disabled={pagina === 0}
                    className="p-2 rounded-lg text-gray-500 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    title="Página anterior"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setPagina((p) => Math.min(totalPaginasSeguro - 1, p + 1))}
                    disabled={paginaActual >= totalPaginasSeguro}
                    className="p-2 rounded-lg text-gray-500 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    title="Página siguiente"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Detalle del lote */}
      <Modal
        open={detalleAbierto && !!loteDetalle}
        onClose={() => setDetalleAbierto(false)}
        labelledBy="lote-detalle-titulo"
      >
        {loteDetalle && (
          <div className="p-6 max-h-[80vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 id="lote-detalle-titulo" className="text-lg font-semibold">
                Lote #{loteDetalle.lote.id}
              </h3>
              <button
                type="button"
                onClick={() => setDetalleAbierto(false)}
                className="p-2 text-gray-400 hover:text-gray-600 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                aria-label="Cerrar detalle del lote"
              >
                ✕
              </button>
            </div>

            {detalleLoading ? (
              <div className="flex items-center justify-center h-24">
                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-800"></div>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <p className="text-gray-500">Fecha</p>
                    <p className="font-medium">
                      {new Date(loteDetalle.lote.fecha).toLocaleString('es-AR')}
                    </p>
                  </div>
                  <div>
                    <p className="text-gray-500">Usuario</p>
                    <p className="font-medium">{loteDetalle.lote.usuarioUsername || '—'}</p>
                  </div>
                  <div>
                    <p className="text-gray-500">Ajuste</p>
                    <p className="font-medium">{etiquetaAjuste(loteDetalle.lote)}</p>
                  </div>
                  <div>
                    <p className="text-gray-500">Cotización usada</p>
                    <p className="font-medium font-mono">
                      ${formatNumber(loteDetalle.lote.cotizacionUsada)}
                    </p>
                  </div>
                  <div>
                    <p className="text-gray-500">Alcance</p>
                    <p className="font-medium">{loteDetalle.lote.alcance}</p>
                  </div>
                  <div>
                    <p className="text-gray-500">Estado</p>
                    <p className="font-medium">
                      {loteDetalle.lote.estado === 'APLICADO' ? 'Aplicado' : 'Revertido'}
                      {loteDetalle.lote.fechaReversion &&
                        ` el ${new Date(loteDetalle.lote.fechaReversion).toLocaleString('es-AR')}`}
                    </p>
                  </div>
                </div>

                {loteDetalle.lote.nota && (
                  <div>
                    <p className="text-sm text-gray-500">Nota</p>
                    <p className="text-sm font-medium">{loteDetalle.lote.nota}</p>
                  </div>
                )}

                <div className="overflow-x-auto border border-gray-100 rounded-lg">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-100">
                        <th className="text-left py-2 px-3 font-medium text-gray-500">Producto</th>
                        <th className="text-right py-2 px-3 font-medium text-gray-500">USD antes</th>
                        <th className="text-right py-2 px-3 font-medium text-gray-500">USD después</th>
                        <th className="text-right py-2 px-3 font-medium text-gray-500">ARS antes</th>
                        <th className="text-right py-2 px-3 font-medium text-gray-500">ARS después</th>
                      </tr>
                    </thead>
                    <tbody>
                      {loteDetalle.items.map((item) => (
                        <tr key={item.productoId} className="border-b border-gray-50 last:border-0">
                          <td className="py-2 px-3 text-gray-900">{item.productoNombre}</td>
                          <td className="py-2 px-3 text-right font-mono text-gray-500">
                            {formatMoney(item.precioUsdAntes, 'USD')}
                          </td>
                          <td className="py-2 px-3 text-right font-mono font-semibold text-gray-900">
                            {formatMoney(item.precioUsdDespues, 'USD')}
                          </td>
                          <td className="py-2 px-3 text-right font-mono text-gray-500">
                            {formatMoney(item.precioArsAntes, 'ARS')}
                          </td>
                          <td className="py-2 px-3 text-right font-mono font-semibold text-gray-900">
                            {formatMoney(item.precioArsDespues, 'ARS')}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Precios;
