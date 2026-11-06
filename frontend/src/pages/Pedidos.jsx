import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Calendar, Check, ClipboardList, Mail, MapPin, Phone, RefreshCw, Trash2, X } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';
import EmptyState from '../components/EmptyState';
import { formatMoney, whatsappUrl } from '../utils/format';

const ESTADOS = {
  PENDIENTE: { label: 'Pendiente', className: 'bg-amber-100 text-amber-800' },
  CONVERTIDO: { label: 'Convertido', className: 'bg-green-100 text-green-800' },
  RECHAZADO: { label: 'Rechazado', className: 'bg-red-100 text-red-800' },
  CANCELADO: { label: 'Cancelado', className: 'bg-gray-100 text-gray-700' },
};

const METODOS_PAGO = [
  { value: 'EFECTIVO', label: 'Efectivo' },
  { value: 'TARJETA', label: 'Tarjeta' },
  { value: 'TRANSFERENCIA', label: 'Transferencia' },
  { value: 'CUENTA_CORRIENTE', label: 'Cuenta corriente' },
  { value: 'OTRO', label: 'Otro' },
];

const formatearFecha = (fecha) => {
  if (!fecha) return '—';
  return new Date(fecha).toLocaleString('es-AR', {
    dateStyle: 'short',
    timeStyle: 'short',
  });
};

const notificarActualizacion = () => {
  window.dispatchEvent(new Event('pedidos-actualizados'));
};

const DetallePedidoModal = ({ pedido, open, onClose, onResuelto }) => {
  const [items, setItems] = useState([]);
  const [modo, setModo] = useState('detalle');
  const [metodoPago, setMetodoPago] = useState('EFECTIVO');
  const [motivo, setMotivo] = useState('');
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!open || !pedido) return;
    setItems((pedido.detalles || []).map((detalle) => ({
      detalleId: detalle.id,
      nombre: detalle.nombreProducto,
      cantidad: detalle.cantidad,
      cantidadOriginal: detalle.cantidad,
      stockActual: detalle.stockActual,
      productoActivo: detalle.productoActivo,
    })));
    setModo('detalle');
    setMetodoPago('EFECTIVO');
    setMotivo('');
    setGuardando(false);
  }, [open, pedido]);

  const cambiarCantidad = (detalleId, cantidad) => {
    setItems((prev) => prev.map((item) => (
      item.detalleId === detalleId
        ? { ...item, cantidad: Math.max(0, Math.min(9999, cantidad)) }
        : item
    )));
  };

  const quitarLinea = (detalleId) => cambiarCantidad(detalleId, 0);

  const lineasValidas = items.filter((item) => item.cantidad > 0);
  const lineasConProblema = items.filter((item) => item.cantidad > 0
    && (item.productoActivo === false || item.cantidad > (item.stockActual ?? 0)));

  const convertir = async (e) => {
    e.preventDefault();
    if (lineasValidas.length === 0) {
      toast.error('El pedido debe tener al menos una línea para convertir');
      return;
    }
    if (lineasConProblema.length > 0) {
      toast.error(`Revisá stock o disponibilidad de: ${lineasConProblema[0].nombre}`);
      return;
    }

    setGuardando(true);
    try {
      await api.post(`/api/pedidos/${pedido.id}/convertir`, {
        metodoPago,
        items: items.map((item) => ({
          detalleId: item.detalleId,
          cantidad: item.cantidad,
        })),
      });
      notificarActualizacion();
      toast.success(
        (t) => (
          <span>
            Pedido #{pedido.id} convertido en venta.{' '}
            <Link
              to="/ventas"
              onClick={() => toast.dismiss(t.id)}
              className="font-semibold underline"
            >
              Ver ventas
            </Link>
          </span>
        ),
        { duration: 6000 }
      );
      onResuelto();
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo convertir el pedido');
    } finally {
      setGuardando(false);
    }
  };

  const rechazar = async (e) => {
    e.preventDefault();
    if (!motivo.trim()) {
      toast.error('Ingresá el motivo del rechazo');
      return;
    }

    setGuardando(true);
    try {
      await api.post(`/api/pedidos/${pedido.id}/rechazar`, { motivo: motivo.trim() });
      toast.success(`Pedido #${pedido.id} rechazado`);
      notificarActualizacion();
      onResuelto();
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo rechazar el pedido');
    } finally {
      setGuardando(false);
    }
  };

  const cliente = pedido?.cliente;
  const estado = ESTADOS[pedido?.estado] || ESTADOS.PENDIENTE;
  const esPendiente = pedido?.estado === 'PENDIENTE';

  return (
    <Modal open={open && !!pedido} onClose={onClose} labelledBy="detalle-pedido-titulo">
      <div className="p-6 space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h3 id="detalle-pedido-titulo" className="text-lg font-semibold">
                Pedido #{pedido?.id}
              </h3>
              <span className={`px-2 py-1 text-xs font-medium rounded-full ${estado.className}`}>
                {estado.label}
              </span>
            </div>
            <p className="text-sm text-gray-500 flex items-center gap-2 mt-1">
              <Calendar className="w-4 h-4" aria-hidden="true" />
              {formatearFecha(pedido?.fecha)}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
            aria-label="Cerrar detalle"
          >
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        <div className="bg-gray-50 rounded-lg p-4 space-y-1 text-sm">
          <p className="font-medium text-gray-900">{cliente?.nombre || 'Cliente'}</p>
          <p className="flex items-center gap-2 text-gray-600">
            <Phone className="w-4 h-4 text-gray-400" aria-hidden="true" />
            {cliente?.telefono ? (
              <a
                href={whatsappUrl(cliente.telefono)}
                target="_blank"
                rel="noopener noreferrer"
                className="text-green-700 hover:underline"
              >
                {cliente.telefono}
              </a>
            ) : 'Sin teléfono'}
          </p>
          {cliente?.email && (
            <p className="flex items-center gap-2 text-gray-600">
              <Mail className="w-4 h-4 text-gray-400" aria-hidden="true" />
              {cliente.email}
            </p>
          )}
          {cliente?.direccion && (
            <p className="flex items-center gap-2 text-gray-600">
              <MapPin className="w-4 h-4 text-gray-400" aria-hidden="true" />
              {cliente.direccion}
            </p>
          )}
          {pedido?.observaciones && (
            <p className="text-gray-600 pt-1">
              <span className="font-medium text-gray-900">Observaciones:</span> {pedido.observaciones}
            </p>
          )}
          {pedido?.motivoRechazo && (
            <p className="text-red-700">
              <span className="font-medium">Motivo de rechazo:</span> {pedido.motivoRechazo}
            </p>
          )}
          {pedido?.ventaId && (
            <p className="text-green-700">
              <span className="font-medium">Venta:</span> #{pedido.ventaId}
            </p>
          )}
        </div>

        {modo === 'detalle' && (
          <>
            <div className="space-y-2">
              {items.map((item) => {
                const sinStock = item.productoActivo === false
                  || item.cantidad > (item.stockActual ?? 0);
                const quitada = item.cantidad === 0;
                return (
                  <div
                    key={item.detalleId}
                    className={`flex items-center gap-3 rounded-lg p-3 ${quitada ? 'bg-gray-50 opacity-60' : 'bg-white border border-gray-100'}`}
                  >
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{item.nombre}</p>
                      {quitada ? (
                        <p className="text-xs text-gray-500">Quitada del pedido</p>
                      ) : item.productoActivo === false ? (
                        <p className="text-xs text-red-600 font-medium">Producto inactivo</p>
                      ) : (
                        <p className={`text-xs ${sinStock ? 'text-red-600 font-medium' : 'text-gray-500'}`}>
                          Stock disponible: {item.stockActual}
                          {sinStock && ' — no alcanza'}
                        </p>
                      )}
                    </div>
                    {esPendiente && (
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min="0"
                          max="9999"
                          value={item.cantidad}
                          onChange={(e) => cambiarCantidad(item.detalleId, Number(e.target.value))}
                          disabled={quitada}
                          className="input w-20 text-center"
                          aria-label={`Cantidad de ${item.nombre}`}
                        />
                        {quitada ? (
                          <button
                            type="button"
                            onClick={() => cambiarCantidad(item.detalleId, item.cantidadOriginal)}
                            className="text-xs text-primary-800 hover:underline"
                          >
                            Restaurar
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => quitarLinea(item.detalleId)}
                            className="p-2 text-gray-400 hover:text-red-600 rounded-lg"
                            title="Quitar línea"
                            aria-label={`Quitar ${item.nombre} del pedido`}
                          >
                            <Trash2 className="w-4 h-4" aria-hidden="true" />
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <div className="flex justify-between text-lg font-bold border-t border-gray-100 pt-3">
              <span>Total estimado</span>
              <span className="text-primary-800">{formatMoney(pedido?.totalEstimado, 'ARS')}</span>
            </div>

            {esPendiente && (
              <div className="flex flex-wrap justify-end gap-2">
                <button
                  onClick={() => setModo('rechazar')}
                  className="btn-secondary flex items-center gap-2 text-red-700"
                >
                  <X className="w-4 h-4" aria-hidden="true" />
                  Rechazar
                </button>
                <button
                  onClick={() => setModo('convertir')}
                  className="btn-primary flex items-center gap-2"
                >
                  <Check className="w-4 h-4" aria-hidden="true" />
                  Convertir en venta
                </button>
              </div>
            )}
          </>
        )}

        {modo === 'convertir' && (
          <form onSubmit={convertir} className="space-y-4">
            <p className="text-sm text-gray-500">
              El total se recalcula en el servidor con los precios y descuentos vigentes.
            </p>
            <div>
              <label htmlFor="metodo-pago-pedido" className="label">Método de pago</label>
              <select
                id="metodo-pago-pedido"
                value={metodoPago}
                onChange={(e) => setMetodoPago(e.target.value)}
                className="input"
              >
                {METODOS_PAGO.map((metodo) => (
                  <option key={metodo.value} value={metodo.value}>{metodo.label}</option>
                ))}
              </select>
              {metodoPago === 'CUENTA_CORRIENTE' && (
                <p className="text-xs text-amber-700 mt-1">
                  La deuda quedará registrada en la cuenta corriente del cliente.
                </p>
              )}
            </div>
            <div className="flex justify-end gap-3">
              <button type="button" onClick={() => setModo('detalle')} className="btn-secondary">
                Volver
              </button>
              <button type="submit" disabled={guardando} className="btn-primary">
                {guardando ? 'Convirtiendo...' : 'Confirmar conversión'}
              </button>
            </div>
          </form>
        )}

        {modo === 'rechazar' && (
          <form onSubmit={rechazar} className="space-y-4">
            <div>
              <label htmlFor="motivo-rechazo" className="label">Motivo del rechazo</label>
              <textarea
                id="motivo-rechazo"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                className="input"
                rows={3}
                maxLength={500}
                required
                placeholder="Ej.: no hay stock del producto solicitado"
              />
              <p className="text-xs text-gray-500 mt-1">
                El cliente va a ver este motivo en “Mis pedidos”.
              </p>
            </div>
            <div className="flex justify-end gap-3">
              <button type="button" onClick={() => setModo('detalle')} className="btn-secondary">
                Volver
              </button>
              <button type="submit" disabled={guardando} className="btn-primary">
                {guardando ? 'Rechazando...' : 'Rechazar pedido'}
              </button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
};

const Pedidos = () => {
  const [pedidos, setPedidos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filtro, setFiltro] = useState('PENDIENTE');
  const [pedidoAbierto, setPedidoAbierto] = useState(null);

  const fetchPedidos = async (estado = filtro) => {
    setLoading(true);
    try {
      const response = await api.get('/api/pedidos', { params: { estado } });
      setPedidos(response.data);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al cargar pedidos');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPedidos(filtro);
  }, [filtro]);

  const recargar = () => fetchPedidos(filtro);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-lg sm:text-2xl font-bold text-gray-900">Pedidos online</h1>
          <p className="text-sm sm:text-base text-gray-500">
            Revisá los pedidos del catálogo y convertilos en ventas.
          </p>
        </div>
        <button onClick={recargar} className="btn-secondary flex items-center gap-2 self-start">
          <RefreshCw className="w-4 h-4" aria-hidden="true" />
          Actualizar
        </button>
      </div>

      <div className="flex flex-wrap gap-2">
        {Object.entries(ESTADOS).map(([estado, info]) => (
          <button
            key={estado}
            onClick={() => setFiltro(estado)}
            className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
              filtro === estado
                ? 'bg-primary-800 text-white border-primary-800'
                : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
            }`}
          >
            {info.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-32">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
        </div>
      ) : pedidos.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={ClipboardList}
            title={`No hay pedidos ${ESTADOS[filtro].label.toLowerCase()}s`}
            description="Los pedidos enviados desde el catálogo aparecen acá."
          />
        </div>
      ) : (
        <div className="card overflow-x-auto p-0">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-gray-500">N°</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">Fecha</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">Cliente</th>
                <th className="px-4 py-3 text-right font-medium text-gray-500">Ítems</th>
                <th className="px-4 py-3 text-right font-medium text-gray-500">Total estimado</th>
                <th className="px-4 py-3 text-left font-medium text-gray-500">Estado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {pedidos.map((pedido) => {
                const estado = ESTADOS[pedido.estado] || ESTADOS.PENDIENTE;
                return (
                  <tr
                    key={pedido.id}
                    onClick={() => setPedidoAbierto(pedido)}
                    className="hover:bg-gray-50 cursor-pointer"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') setPedidoAbierto(pedido);
                    }}
                  >
                    <td className="px-4 py-3 font-medium text-gray-900">#{pedido.id}</td>
                    <td className="px-4 py-3 text-gray-600 whitespace-nowrap">
                      {formatearFecha(pedido.fecha)}
                    </td>
                    <td className="px-4 py-3 text-gray-900">
                      {pedido.cliente?.nombre}
                      <span className="block text-xs text-gray-500">
                        {pedido.cliente?.telefono || 'Sin teléfono'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right text-gray-600">
                      {pedido.detalles?.length || 0}
                    </td>
                    <td className="px-4 py-3 text-right font-medium text-gray-900 whitespace-nowrap">
                      {formatMoney(pedido.totalEstimado, 'ARS')}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-1 text-xs font-medium rounded-full ${estado.className}`}>
                        {estado.label}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <DetallePedidoModal
        pedido={pedidoAbierto}
        open={!!pedidoAbierto}
        onClose={() => setPedidoAbierto(null)}
        onResuelto={recargar}
      />
    </div>
  );
};

export default Pedidos;
