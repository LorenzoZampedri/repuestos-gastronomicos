import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Search,
  Download,
  Eye,
  Filter,
  ChevronLeft,
  ChevronRight,
  ScrollText,
  RefreshCw,
} from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';
import EmptyState from '../components/EmptyState';

const POR_PAGINA = 20;

const aISOLocal = (fecha) => {
  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
};

const hoyISO = () => aISOLocal(new Date());

const haceDiasISO = (dias) => {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() - dias);
  return aISOLocal(fecha);
};

const FILTROS_INICIALES = {
  desde: haceDiasISO(7),
  hasta: hoyISO(),
  usuario: '',
  rol: '',
  modulo: '',
  accion: '',
  resultado: '',
  q: '',
};

const FILTROS_VACIOS = {
  desde: '',
  hasta: '',
  usuario: '',
  rol: '',
  modulo: '',
  accion: '',
  resultado: '',
  q: '',
};

const RESULTADOS = [
  { codigo: 'OK', etiqueta: 'Correcto' },
  { codigo: 'FALLIDO', etiqueta: 'Fallido' },
];

const paramsLimpios = (filtros) => {
  const params = {};
  Object.entries(filtros).forEach(([clave, valor]) => {
    if (valor !== '' && valor != null) params[clave] = valor;
  });
  return params;
};

const etiquetaResultado = (resultado) => {
  if (resultado === 'OK') return 'Correcto';
  if (resultado === 'FALLIDO') return 'Fallido';
  return resultado || '—';
};

const Logs = () => {
  const [borrador, setBorrador] = useState(FILTROS_INICIALES);
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [pagina, setPagina] = useState(0);
  const [contenido, setContenido] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [size, setSize] = useState(POR_PAGINA);
  const [loading, setLoading] = useState(true);
  const [exportando, setExportando] = useState(false);
  const [opciones, setOpciones] = useState({ modulos: [], acciones: [], roles: [] });
  const [detalle, setDetalle] = useState(null);
  const [detalleAbierto, setDetalleAbierto] = useState(false);
  const [detalleLoading, setDetalleLoading] = useState(false);

  useEffect(() => {
    let activo = true;
    api.get('/api/logs/filtros')
      .then((response) => {
        if (activo) setOpciones(response.data || { modulos: [], acciones: [], roles: [] });
      })
      .catch(() => {
        if (activo) setOpciones({ modulos: [], acciones: [], roles: [] });
      });
    return () => { activo = false; };
  }, []);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.get('/api/logs', {
        params: { ...paramsLimpios(filtros), page: pagina, size: POR_PAGINA },
      });
      const data = response.data || {};
      setContenido(data.content || []);
      setTotal(data.total || 0);
      setTotalPaginas(data.totalPaginas || 0);
      setSize(data.size || POR_PAGINA);
    } catch (error) {
      toast.error('Error al cargar los logs');
      setContenido([]);
      setTotal(0);
      setTotalPaginas(0);
    } finally {
      setLoading(false);
    }
  }, [filtros, pagina]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const mapaAcciones = useMemo(() => {
    const mapa = {};
    (opciones.acciones || []).forEach((a) => { mapa[a.codigo] = a; });
    return mapa;
  }, [opciones.acciones]);

  const mapaModulos = useMemo(() => {
    const mapa = {};
    (opciones.modulos || []).forEach((m) => { mapa[m.codigo] = m.etiqueta; });
    return mapa;
  }, [opciones.modulos]);

  const mapaRoles = useMemo(() => {
    const mapa = {};
    (opciones.roles || []).forEach((r) => { mapa[r.codigo] = r.etiqueta; });
    return mapa;
  }, [opciones.roles]);

  const accionesDisponibles = useMemo(() => {
    const acciones = opciones.acciones || [];
    if (!borrador.modulo) return acciones;
    return acciones.filter((a) => a.modulo === borrador.modulo);
  }, [opciones.acciones, borrador.modulo]);

  const cambiar = (campo, valor) => {
    setBorrador((prev) => {
      const siguiente = { ...prev, [campo]: valor };
      if (campo === 'modulo') {
        const accion = mapaAcciones[siguiente.accion];
        if (accion && valor && accion.modulo !== valor) siguiente.accion = '';
      }
      return siguiente;
    });
  };

  const aplicarFiltros = (event) => {
    event?.preventDefault();
    setPagina(0);
    setFiltros({ ...borrador });
  };

  const limpiarFiltros = () => {
    setBorrador(FILTROS_VACIOS);
    setFiltros(FILTROS_VACIOS);
    setPagina(0);
  };

  const abrirDetalle = async (log) => {
    setDetalle(log);
    setDetalleAbierto(true);
    setDetalleLoading(true);
    try {
      const response = await api.get(`/api/logs/${log.id}`);
      setDetalle(response.data);
    } catch (error) {
      toast.error('No se pudo cargar el detalle del log');
    } finally {
      setDetalleLoading(false);
    }
  };

  const exportar = async () => {
    setExportando(true);
    try {
      const response = await api.get('/api/logs/export', {
        params: paramsLimpios(filtros),
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }));
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = `logs-${hoyISO()}.csv`;
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      window.URL.revokeObjectURL(url);

      const truncado = response.headers?.['x-logs-truncados'] === 'true';
      if (truncado) {
        toast('Se exportaron los primeros 5000 registros. Acotá los filtros para obtener el resto.', {
          icon: '⚠️',
        });
      } else {
        toast.success('Exportación descargada');
      }
    } catch (error) {
      toast.error('Error al exportar los logs');
    } finally {
      setExportando(false);
    }
  };

  const totalPaginasSeguro = Math.max(totalPaginas, total > 0 ? 1 : 0);
  const paginaActual = pagina + 1;
  const desdeMostrar = total === 0 ? 0 : pagina * size + 1;
  const hastaMostrar = Math.min((pagina + 1) * size, total);

  const paginasVisibles = () => {
    const paginas = [];
    for (let p = 1; p <= totalPaginasSeguro; p++) {
      if (p === 1 || p === totalPaginasSeguro || Math.abs(p - paginaActual) <= 1) {
        paginas.push(p);
      } else if (paginas[paginas.length - 1] !== '...') {
        paginas.push('...');
      }
    }
    return paginas;
  };

  const colorResultado = (resultado) => {
    if (resultado === 'OK') return 'bg-green-100 text-green-800';
    if (resultado === 'FALLIDO') return 'bg-red-100 text-red-800';
    return 'bg-gray-100 text-gray-700';
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Logs de actividad</h1>
          <p className="text-gray-500">Auditoría de acciones realizadas en el sistema</p>
        </div>
        <button
          type="button"
          onClick={exportar}
          disabled={exportando}
          className="btn-secondary flex items-center justify-center gap-2 disabled:opacity-60"
        >
          <Download className="w-5 h-5" aria-hidden="true" />
          {exportando ? 'Exportando…' : 'Exportar CSV'}
        </button>
      </div>

      <form onSubmit={aplicarFiltros} className="card space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label className="label" htmlFor="logs-desde">Desde</label>
            <input
              id="logs-desde"
              type="date"
              value={borrador.desde}
              onChange={(e) => cambiar('desde', e.target.value)}
              className="input"
            />
          </div>
          <div>
            <label className="label" htmlFor="logs-hasta">Hasta</label>
            <input
              id="logs-hasta"
              type="date"
              value={borrador.hasta}
              onChange={(e) => cambiar('hasta', e.target.value)}
              className="input"
            />
          </div>
          <div>
            <label className="label" htmlFor="logs-usuario">Usuario</label>
            <input
              id="logs-usuario"
              type="text"
              value={borrador.usuario}
              onChange={(e) => cambiar('usuario', e.target.value)}
              placeholder="Nombre de usuario"
              className="input"
            />
          </div>
          <div>
            <label className="label" htmlFor="logs-rol">Rol</label>
            <select
              id="logs-rol"
              value={borrador.rol}
              onChange={(e) => cambiar('rol', e.target.value)}
              className="input"
            >
              <option value="">Todos</option>
              {(opciones.roles || []).map((r) => (
                <option key={r.codigo} value={r.codigo}>{r.etiqueta}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="logs-modulo">Módulo</label>
            <select
              id="logs-modulo"
              value={borrador.modulo}
              onChange={(e) => cambiar('modulo', e.target.value)}
              className="input"
            >
              <option value="">Todos</option>
              {(opciones.modulos || []).map((m) => (
                <option key={m.codigo} value={m.codigo}>{m.etiqueta}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="logs-accion">Acción</label>
            <select
              id="logs-accion"
              value={borrador.accion}
              onChange={(e) => cambiar('accion', e.target.value)}
              className="input"
            >
              <option value="">Todas</option>
              {accionesDisponibles.map((a) => (
                <option key={a.codigo} value={a.codigo}>{a.etiqueta}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="logs-resultado">Resultado</label>
            <select
              id="logs-resultado"
              value={borrador.resultado}
              onChange={(e) => cambiar('resultado', e.target.value)}
              className="input"
            >
              <option value="">Todos</option>
              {RESULTADOS.map((r) => (
                <option key={r.codigo} value={r.codigo}>{r.etiqueta}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="logs-q">Buscar</label>
            <div className="relative">
              <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" aria-hidden="true" />
              <input
                id="logs-q"
                type="search"
                value={borrador.q}
                onChange={(e) => cambiar('q', e.target.value)}
                placeholder="Resumen o detalle"
                className="input pl-9"
              />
            </div>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-sm text-gray-500">
            {total > 0
              ? `Mostrando ${desdeMostrar}–${hastaMostrar} de ${total} registros`
              : 'Sin registros para los filtros seleccionados'}
          </p>
          <div className="flex items-center gap-2">
            <button type="button" onClick={limpiarFiltros} className="btn-secondary">
              Limpiar
            </button>
            <button type="submit" className="btn-primary flex items-center gap-2">
              <Filter className="w-4 h-4" aria-hidden="true" />
              Filtrar
            </button>
          </div>
        </div>
      </form>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
          </div>
        ) : contenido.length === 0 ? (
          <EmptyState
            icon={ScrollText}
            title="No hay registros"
            description="Probá ampliar el rango de fechas o quitar filtros."
            action={
              <button type="button" onClick={limpiarFiltros} className="btn-secondary flex items-center gap-2">
                <RefreshCw className="w-4 h-4" aria-hidden="true" />
                Limpiar filtros
              </button>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Fecha</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Usuario</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Acción</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Módulo</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Entidad</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Resumen</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Resultado</th>
                    <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">IP</th>
                    <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Detalle</th>
                  </tr>
                </thead>
                <tbody>
                  {contenido.map((log) => {
                    const accion = mapaAcciones[log.accion];
                    return (
                      <tr key={log.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                        <td className="py-3 px-4 text-sm text-gray-600 whitespace-nowrap">
                          {new Date(log.fecha).toLocaleString('es-AR')}
                        </td>
                        <td className="py-3 px-4">
                          <p className="text-sm font-medium text-gray-900">{log.usuarioUsername || 'Sistema'}</p>
                          <p className="text-xs text-gray-500">
                            {mapaRoles[log.usuarioRol] || log.usuarioRol || '—'}
                          </p>
                        </td>
                        <td className="py-3 px-4 text-sm text-gray-900">
                          {accion?.etiqueta || log.accion}
                        </td>
                        <td className="py-3 px-4 text-sm text-gray-600">
                          {mapaModulos[log.modulo] || log.modulo}
                        </td>
                        <td className="py-3 px-4 text-sm text-gray-600 whitespace-nowrap">
                          {log.entidadTipo ? `${log.entidadTipo}${log.entidadId ? ` #${log.entidadId}` : ''}` : '—'}
                        </td>
                        <td className="py-3 px-4 text-sm text-gray-600 max-w-xs">
                          <span className="line-clamp-2" title={log.resumen || ''}>{log.resumen || '—'}</span>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`px-2 py-1 text-xs font-medium rounded-full ${colorResultado(log.resultado)}`}>
                            {etiquetaResultado(log.resultado)}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-sm text-gray-600 whitespace-nowrap">{log.ip || '—'}</td>
                        <td className="py-3 px-4 text-right">
                          <button
                            type="button"
                            onClick={() => abrirDetalle(log)}
                            className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                            aria-label={`Ver detalle del log #${log.id}`}
                          >
                            <Eye className="w-4 h-4" aria-hidden="true" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {totalPaginasSeguro > 1 && (
              <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 border-t border-gray-100">
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
                  {paginasVisibles().map((p, idx) =>
                    p === '...' ? (
                      <span key={`gap-${idx}`} className="px-1 text-sm text-gray-400">…</span>
                    ) : (
                      <button
                        key={p}
                        type="button"
                        onClick={() => setPagina(p - 1)}
                        className={`min-w-[32px] h-8 px-2 rounded-lg text-sm font-medium transition-colors ${
                          p === paginaActual
                            ? 'bg-primary-800 text-white'
                            : 'text-gray-600 hover:bg-gray-100'
                        }`}
                      >
                        {p}
                      </button>
                    )
                  )}
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

      <Modal
        open={detalleAbierto && !!detalle}
        onClose={() => setDetalleAbierto(false)}
        labelledBy="log-detalle-titulo"
      >
        {detalle && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 id="log-detalle-titulo" className="text-lg font-semibold">
                Detalle del log #{detalle.id}
              </h3>
              <button
                type="button"
                onClick={() => setDetalleAbierto(false)}
                className="p-2 text-gray-400 hover:text-gray-600 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                aria-label="Cerrar detalle del log"
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
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-gray-500">Fecha</p>
                    <p className="font-medium">{new Date(detalle.fecha).toLocaleString('es-AR')}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Resultado</p>
                    <span className={`inline-block mt-1 px-2 py-1 text-xs font-medium rounded-full ${colorResultado(detalle.resultado)}`}>
                      {etiquetaResultado(detalle.resultado)}
                    </span>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Usuario</p>
                    <p className="font-medium">{detalle.usuarioUsername || 'Sistema'}</p>
                    <p className="text-xs text-gray-500">
                      {mapaRoles[detalle.usuarioRol] || detalle.usuarioRol || '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Acción</p>
                    <p className="font-medium">
                      {mapaAcciones[detalle.accion]?.etiqueta || detalle.accion}
                    </p>
                    <p className="text-xs text-gray-500">
                      {mapaModulos[detalle.modulo] || detalle.modulo}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Entidad</p>
                    <p className="font-medium">
                      {detalle.entidadTipo ? `${detalle.entidadTipo}${detalle.entidadId ? ` #${detalle.entidadId}` : ''}` : '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">IP</p>
                    <p className="font-medium">{detalle.ip || '—'}</p>
                  </div>
                </div>

                <div>
                  <p className="text-sm text-gray-500">Resumen</p>
                  <p className="font-medium text-gray-900">{detalle.resumen || '—'}</p>
                </div>

                <div>
                  <p className="text-sm text-gray-500 mb-1">Navegador</p>
                  <p className="text-sm text-gray-600 break-all">{detalle.userAgent || '—'}</p>
                </div>

                <div>
                  <p className="text-sm text-gray-500 mb-1">Detalle adicional</p>
                  <pre className="bg-gray-50 border border-gray-200 rounded-lg p-3 text-xs text-gray-700 overflow-auto max-h-64 whitespace-pre-wrap break-words">
                    {detalle.detalle == null
                      ? 'Sin detalle adicional'
                      : JSON.stringify(detalle.detalle, null, 2)}
                  </pre>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Logs;
