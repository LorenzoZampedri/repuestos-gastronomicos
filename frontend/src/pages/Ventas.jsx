import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Search, Plus, Eye, Calendar, Filter, Trash2, ChevronLeft, ChevronRight, User, Users, X } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import FiltroDesplegable from '../components/FiltroDesplegable';
import { formatMoney } from '../utils/format';
import {
  construirOpcionesClientes,
  construirOpcionesUsuarios,
  filtrarVentas,
  sumarTotales,
} from '../utils/ventasFiltros';

const POR_PAGINA = 10;
const FILTROS_VALIDOS = ['hoy', 'ayer', 'semana', 'mes', 'todo'];

const leerFiltroInicial = () => {
  if (/^#venta-\d+$/.test(window.location.hash)) return 'todo';
  const filtro = new URLSearchParams(window.location.search).get('filtro');
  return FILTROS_VALIDOS.includes(filtro) ? filtro : 'mes';
};

const leerSeleccionInicial = () => {
  if (/^#venta-\d+$/.test(window.location.hash)) return { clientes: {}, usuarios: {} };
  const params = new URLSearchParams(window.location.search);
  const cliente = params.get('cliente');
  const usuario = params.get('usuario');
  return {
    clientes: cliente ? { [cliente]: '' } : {},
    usuarios: usuario ? { [usuario]: '' } : {},
  };
};

const Ventas = () => {
  const { isAdmin } = useAuth();
  const [ventas, setVentas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [fechaInicio, setFechaInicio] = useState('');
  const [fechaFin, setFechaFin] = useState('');
  const [filtroInicial] = useState(leerFiltroInicial);
  const [filtroActivo, setFiltroActivo] = useState(filtroInicial);
  const [ventaSeleccionada, setVentaSeleccionada] = useState(null);
  const [modalAbierto, setModalAbierto] = useState(false);
  const [pagina, setPagina] = useState(1);
  const [ventaResaltada, setVentaResaltada] = useState(null);
  const [seleccionInicial] = useState(leerSeleccionInicial);
  const [clientesSel, setClientesSel] = useState(seleccionInicial.clientes);
  const [usuariosSel, setUsuariosSel] = useState(seleccionInicial.usuarios);
  const [desplegableAbierto, setDesplegableAbierto] = useState(null);

  useEffect(() => {
    aplicarFiltro(filtroInicial);

    const params = new URLSearchParams(window.location.search);
    const parametrosIniciales = ['filtro', 'cliente', 'usuario'];
    if (parametrosIniciales.some((clave) => params.has(clave))) {
      parametrosIniciales.forEach((clave) => params.delete(clave));
      const search = params.toString();
      window.history.replaceState(
        null,
        '',
        window.location.pathname + (search ? `?${search}` : '') + window.location.hash
      );
    }
  }, []);

  useEffect(() => {
    if (fechaInicio && fechaFin) fetchVentas();
  }, [fechaInicio, fechaFin]);

  useEffect(() => {
    if (loading) return;

    const resolver = (seleccion, tipo) => {
      let cambio = false;
      const siguiente = { ...seleccion };
      for (const [id, nombre] of Object.entries(seleccion)) {
        if (nombre) continue;
        const venta = ventas.find((v) =>
          tipo === 'cliente'
            ? String(v.cliente?.id) === id
            : String(v.usuario?.id) === id
        );
        if (venta) {
          const dato = tipo === 'cliente' ? venta.cliente : venta.usuario;
          siguiente[id] =
            dato?.nombre ||
            dato?.username ||
            `${tipo === 'cliente' ? 'Cliente' : 'Personal'} #${id}`;
        } else {
          delete siguiente[id];
        }
        cambio = true;
      }
      return cambio ? siguiente : seleccion;
    };

    setClientesSel((previos) => resolver(previos, 'cliente'));
    setUsuariosSel((previos) => resolver(previos, 'usuario'));
  }, [loading, ventas]);

  useEffect(() => {
    if (loading) return;
    const match = window.location.hash.match(/^#venta-(\d+)$/);
    if (!match) return;
    const el = document.getElementById(`venta-${match[1]}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setVentaResaltada(match[1]);
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
  }, [loading, pagina]);

  useEffect(() => {
    if (!ventaResaltada) return;
    const timer = setTimeout(() => setVentaResaltada(null), 4000);
    return () => clearTimeout(timer);
  }, [ventaResaltada]);

  const hoy = () => new Date().toISOString().split('T')[0];

  const ayer = () => {
    const d = new Date(); d.setDate(d.getDate() - 1);
    return d.toISOString().split('T')[0];
  };

  const semana = () => {
    const d = new Date(); d.setDate(d.getDate() - 7);
    return d.toISOString().split('T')[0];
  };

  const mes = () => {
    const d = new Date(); d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
  };

  const aplicarFiltro = (tipo) => {
    setFiltroActivo(tipo);
    setPagina(1);
    const fin = hoy();
    let inicio;
    switch (tipo) {
      case 'hoy': inicio = fin; break;
      case 'ayer': inicio = ayer(); break;
      case 'semana': inicio = semana(); break;
      case 'mes': inicio = mes(); break;
      case 'todo': inicio = '2020-01-01'; break;
      default: inicio = mes();
    }
    setFechaInicio(inicio);
    setFechaFin(fin);
  };

  const fetchVentas = async () => {
    setLoading(true);
    try {
      const inicio = `${fechaInicio}T00:00:00`;
      const fin = `${fechaFin}T23:59:59`;
      const response = await api.get(`/api/ventas?inicio=${inicio}&fin=${fin}`);
      setVentas(response.data);
      const match = window.location.hash.match(/^#venta-(\d+)$/);
      if (match) {
        const idx = response.data.findIndex((v) => String(v.id) === match[1]);
        if (idx >= 0) {
          setPagina(Math.floor(idx / POR_PAGINA) + 1);
          setVentaResaltada(match[1]);
        }
      }
    } catch (error) {
      toast.error('Error al cargar ventas');
    } finally {
      setLoading(false);
    }
  };

  const getEstadoBadge = (estado) => {
    const styles = {
      PENDIENTE: 'bg-yellow-100 text-yellow-800',
      PARCIAL: 'bg-orange-100 text-orange-800',
      PAGADA: 'bg-green-100 text-green-800',
      ANULADA: 'bg-gray-100 text-gray-800'
    };
    return (
      <span className={`px-2 py-1 text-xs font-medium rounded-full ${styles[estado] || styles.PENDIENTE}`}>
        {estado}
      </span>
    );
  };

  const ventasParaClientes = useMemo(
    () => filtrarVentas(ventas, { clientes: {}, usuarios: usuariosSel }),
    [ventas, usuariosSel]
  );
  const ventasParaUsuarios = useMemo(
    () => filtrarVentas(ventas, { clientes: clientesSel, usuarios: {} }),
    [ventas, clientesSel]
  );
  const opcionesClientes = useMemo(
    () => construirOpcionesClientes(ventas, ventasParaClientes, clientesSel),
    [ventas, ventasParaClientes, clientesSel]
  );
  const opcionesUsuarios = useMemo(
    () => construirOpcionesUsuarios(ventas, ventasParaUsuarios, usuariosSel),
    [ventas, ventasParaUsuarios, usuariosSel]
  );
  const ventasFiltradas = useMemo(
    () => filtrarVentas(ventas, { clientes: clientesSel, usuarios: usuariosSel }),
    [ventas, clientesSel, usuariosSel]
  );

  const filtrosActivos =
    Object.keys(clientesSel).length > 0 || Object.keys(usuariosSel).length > 0;
  const totalVentas = sumarTotales(ventasFiltradas);

  const totalPaginas = Math.max(1, Math.ceil(ventasFiltradas.length / POR_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas);
  const inicioSlice = (paginaActual - 1) * POR_PAGINA;
  const ventasPagina = ventasFiltradas.slice(inicioSlice, inicioSlice + POR_PAGINA);
  const desdeMostrar = ventasFiltradas.length === 0 ? 0 : inicioSlice + 1;
  const hastaMostrar = inicioSlice + ventasPagina.length;

  const alternarDesplegable = (nombre) =>
    setDesplegableAbierto((previo) => (previo === nombre ? null : nombre));

  const cambiarClientes = (siguientes) => {
    setClientesSel(siguientes);
    setPagina(1);
  };

  const cambiarUsuarios = (siguientes) => {
    setUsuariosSel(siguientes);
    setPagina(1);
  };

  const quitarCliente = (id) => {
    setClientesSel((previos) => {
      const siguientes = { ...previos };
      delete siguientes[id];
      return siguientes;
    });
    setPagina(1);
  };

  const quitarUsuario = (id) => {
    setUsuariosSel((previos) => {
      const siguientes = { ...previos };
      delete siguientes[id];
      return siguientes;
    });
    setPagina(1);
  };

  const limpiarFiltros = () => {
    setClientesSel({});
    setUsuariosSel({});
    setPagina(1);
  };

  const paginasVisibles = () => {
    const pages = [];
    for (let p = 1; p <= totalPaginas; p++) {
      if (p === 1 || p === totalPaginas || Math.abs(p - paginaActual) <= 1) {
        pages.push(p);
      } else if (pages[pages.length - 1] !== '...') {
        pages.push('...');
      }
    }
    return pages;
  };

  const anularVenta = async (id) => {
    if (!confirm('¿Anular esta venta? Se devolverá el stock.')) return;
    try {
      await api.put(`/api/ventas/${id}/anular`);
      toast.success('Venta anulada');
      fetchVentas();
      if (ventaSeleccionada?.id === id) setModalAbierto(false);
    } catch (error) {
      toast.error('Error al anular venta');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Ventas</h1>
          <p className="text-gray-500">Historial de ventas realizadas</p>
        </div>
        <Link to="/ventas/nueva" className="btn-primary flex items-center gap-2">
          <Plus className="w-5 h-5" />
          Nueva Venta
        </Link>
      </div>

      {/* Filters */}
      <div className="card">
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            {[
              { key: 'todo', label: 'Todas' },
              { key: 'mes', label: 'Último mes' },
              { key: 'semana', label: 'Última semana' },
              { key: 'ayer', label: 'Ayer' },
              { key: 'hoy', label: 'Hoy' },
            ].map((f) => (
              <button
                key={f.key}
                onClick={() => aplicarFiltro(f.key)}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  filtroActivo === f.key
                    ? 'bg-primary-800 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>

          <div className="flex flex-col sm:flex-row gap-2">
            <FiltroDesplegable
              etiqueta="Cliente"
              icono={User}
              titulo="Filtrar por cliente"
              opciones={opcionesClientes}
              seleccionados={clientesSel}
              onChange={cambiarClientes}
              abierto={desplegableAbierto === 'clientes'}
              onToggle={() => alternarDesplegable('clientes')}
              textoVacio="No hay clientes con ventas en este período"
              aviso={
                Object.keys(usuariosSel).length > 0
                  ? 'Los números cuentan solo las ventas del personal elegido'
                  : ''
              }
            />
            <FiltroDesplegable
              etiqueta="Personal"
              icono={Users}
              titulo="Filtrar por personal"
              ayuda="Personal que registró la venta (columna «Vendedor»). En pedidos online, quien los aprobó."
              opciones={opcionesUsuarios}
              seleccionados={usuariosSel}
              onChange={cambiarUsuarios}
              abierto={desplegableAbierto === 'usuarios'}
              onToggle={() => alternarDesplegable('usuarios')}
              textoVacio="No hay personal con ventas en este período"
              aviso={
                Object.keys(clientesSel).length > 0
                  ? 'Los números cuentan solo las ventas de los clientes elegidos'
                  : ''
              }
            />
          </div>

          {filtrosActivos && (
            <div className="flex flex-wrap items-center gap-2">
              {Object.entries(clientesSel).map(([id, nombre]) => (
                <span
                  key={`cliente-${id}`}
                  className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-primary-50 text-primary-800 text-sm border border-primary-200"
                >
                  Cliente: {nombre || `Cliente #${id}`}
                  <button
                    type="button"
                    onClick={() => quitarCliente(id)}
                    className="p-0.5 rounded-full hover:bg-primary-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                    aria-label={`Quitar filtro de cliente ${nombre}`}
                  >
                    <X className="w-3 h-3" aria-hidden="true" />
                  </button>
                </span>
              ))}
              {Object.entries(usuariosSel).map(([id, nombre]) => (
                <span
                  key={`usuario-${id}`}
                  className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-primary-50 text-primary-800 text-sm border border-primary-200"
                >
                  Personal: {nombre || `Personal #${id}`}
                  <button
                    type="button"
                    onClick={() => quitarUsuario(id)}
                    className="p-0.5 rounded-full hover:bg-primary-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                    aria-label={`Quitar filtro de personal ${nombre}`}
                  >
                    <X className="w-3 h-3" aria-hidden="true" />
                  </button>
                </span>
              ))}
              <button
                type="button"
                onClick={limpiarFiltros}
                className="text-sm font-medium text-primary-800 hover:text-primary-700 px-2 py-1 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
              >
                Limpiar filtros
              </button>
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-4 items-end">
            <div className="flex-1">
              <label className="label">Fecha inicio</label>
              <input type="date" value={fechaInicio} onChange={(e) => { setFechaInicio(e.target.value); setFiltroActivo(''); setPagina(1); }} className="input" />
            </div>
            <div className="flex-1">
              <label className="label">Fecha fin</label>
              <input type="date" value={fechaFin} onChange={(e) => { setFechaFin(e.target.value); setFiltroActivo(''); setPagina(1); }} className="input" />
            </div>
          </div>
          <div className="text-right">
            <p className="text-sm text-gray-500">
              {filtrosActivos
                ? `Total de las ventas filtradas · ${ventasFiltradas.length} de ${ventas.length} ventas`
                : 'Total del período'}
            </p>
            <p className="text-2xl font-bold text-primary-800">
              {formatMoney(totalVentas, 'ARS')}
            </p>
            {filtrosActivos && (
              <p className="text-xs text-gray-400">
                Mostrando {desdeMostrar}–{hastaMostrar} de {ventasFiltradas.length} ventas del período
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Sales Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
          </div>
        ) : (
          <>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">#</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Fecha</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Cliente</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Vendedor</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Total</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Estado</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {ventasPagina.map((venta) => (
                   <tr key={venta.id} id={`venta-${venta.id}`} className={`border-b border-gray-100 transition-colors ${String(venta.id) === ventaResaltada ? 'bg-yellow-50 ring-2 ring-inset ring-primary-500' : 'hover:bg-gray-50'}`}>
                     <td className="py-3 px-4 text-sm font-mono text-gray-600">
                       #{venta.id}
                     </td>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2 text-sm text-gray-600">
                        <Calendar className="w-4 h-4 text-gray-400" />
                        {new Date(venta.fecha).toLocaleDateString('es-AR')}
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-sm text-gray-900">
                        {venta.cliente?.nombre || 'Venta anónima'}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-sm text-gray-600">
                        {venta.usuario?.nombre || '-'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <span className="font-semibold text-gray-900">
                        {formatMoney(venta.total, 'ARS')}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      {getEstadoBadge(venta.estado)}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => { setVentaSeleccionada(venta); setModalAbierto(true); }}
                          className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                          aria-label={`Ver detalle de la venta #${venta.id}`}
                        >
                          <Eye className="w-4 h-4" aria-hidden="true" />
                        </button>
                        {isAdmin() && venta.estado !== 'ANULADA' && (
                          <button
                            onClick={() => anularVenta(venta.id)}
                            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                            title="Anular venta"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {ventasFiltradas.length === 0 && (
                  <tr>
                    <td colSpan="7" className="py-12 text-center text-gray-500">
                      {filtrosActivos ? (
                        <div className="flex flex-col items-center gap-3">
                          <span>No hay ventas que coincidan con los filtros</span>
                          <button
                            type="button"
                            onClick={limpiarFiltros}
                            className="px-4 py-2 rounded-lg bg-primary-800 text-white text-sm font-medium hover:bg-primary-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                          >
                            Limpiar filtros
                          </button>
                        </div>
                      ) : (
                        'No se encontraron ventas en este período'
                      )}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Paginación */}
          {ventasFiltradas.length > POR_PAGINA && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 border-t border-gray-100">
              <p className="text-sm text-gray-500">
                Mostrando {desdeMostrar}–{hastaMostrar} de {ventasFiltradas.length} ventas
              </p>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setPagina(paginaActual - 1)}
                  disabled={paginaActual === 1}
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
                      onClick={() => setPagina(p)}
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
                  onClick={() => setPagina(paginaActual + 1)}
                  disabled={paginaActual === totalPaginas}
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

      {/* Sale Detail Modal */}
      <Modal
        open={modalAbierto && !!ventaSeleccionada}
        onClose={() => setModalAbierto(false)}
        labelledBy="venta-detalle-titulo"
      >
        {ventaSeleccionada && (
          <div className="p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 id="venta-detalle-titulo" className="text-lg font-semibold">
                Detalle de Venta #{ventaSeleccionada.id}
              </h3>
              <button
                onClick={() => setModalAbierto(false)}
                className="p-2 text-gray-400 hover:text-gray-600 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                aria-label="Cerrar detalle de venta"
              >
                ✕
              </button>
            </div>

            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm text-gray-500">Fecha</p>
                  <p className="font-medium">
                    {new Date(ventaSeleccionada.fecha).toLocaleString('es-AR')}
                  </p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">Estado</p>
                  {getEstadoBadge(ventaSeleccionada.estado)}
                </div>
                <div>
                  <p className="text-sm text-gray-500">Cliente</p>
                  <p className="font-medium">
                    {ventaSeleccionada.cliente?.nombre || 'Anónimo'}
                  </p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">Vendedor</p>
                  <p className="font-medium">
                    {ventaSeleccionada.usuario?.nombre}
                  </p>
                </div>
              </div>

              {ventaSeleccionada.detalles && ventaSeleccionada.detalles.length > 0 && (
                <div>
                  <p className="text-sm font-medium text-gray-700 mb-2">Productos</p>
                  <div className="space-y-2">
                    {ventaSeleccionada.detalles.map((detalle, idx) => (
                      <div key={idx} className="flex justify-between text-sm bg-gray-50 p-2 rounded">
                        <span>{detalle.producto?.nombre} x{detalle.cantidad}</span>
                        <span className="font-medium">
                          {formatMoney(detalle.subtotal, 'ARS')}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {isAdmin() && ventaSeleccionada.pagos && ventaSeleccionada.pagos.length > 0 && (
                <div>
                  <p className="text-sm font-medium text-gray-700 mb-2">Pagos registrados</p>
                  <div className="space-y-2">
                    {ventaSeleccionada.pagos.map((pago) => (
                      <div key={pago.id} className="flex justify-between text-sm bg-green-50 p-2 rounded">
                        <div>
                          <p className="font-medium text-gray-900">
                            {formatMoney(pago.monto, 'ARS')} · {pago.metodoPago}
                          </p>
                          <p className="text-xs text-gray-500">
                            {new Date(pago.fecha).toLocaleString('es-AR')} · Registrado por{' '}
                            {pago.usuario?.nombre || pago.usuario?.username || 'usuario no registrado'}
                          </p>
                        </div>
                        <span className="text-xs text-gray-500">#{pago.id}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="border-t pt-4">
                <div className="flex justify-between items-center">
                  <span className="text-lg font-bold">Total</span>
                  <span className="text-2xl font-bold text-primary-800">
                    {formatMoney(ventaSeleccionada.total, 'ARS')}
                  </span>
                </div>
                {isAdmin() && ventaSeleccionada.estado !== 'ANULADA' && (
                  <div className="mt-2 space-y-1 text-sm text-gray-600">
                    <div className="flex justify-between">
                      <span>Pagado</span>
                      <span className="font-medium text-green-700">
                        {formatMoney(ventaSeleccionada.montoPagado, 'ARS')}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span>Saldo pendiente</span>
                      <span className="font-medium text-orange-700">
                        {formatMoney(ventaSeleccionada.saldoPendiente, 'ARS')}
                      </span>
                    </div>
                  </div>
                )}
                {isAdmin() && ventaSeleccionada.estado !== 'ANULADA' && (
                  <button
                    onClick={() => anularVenta(ventaSeleccionada.id)}
                    className="mt-4 w-full btn-danger flex items-center justify-center gap-2"
                  >
                    <Trash2 className="w-4 h-4" aria-hidden="true" />
                    Anular venta
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Ventas;
