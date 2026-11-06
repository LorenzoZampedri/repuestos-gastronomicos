import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Calendar, ClipboardList, Package, RefreshCw, XCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import EmptyState from '../components/EmptyState';
import DatosTransferencia from '../components/DatosTransferencia';
import { formatMoney } from '../utils/format';

const METODOS_PAGO = {
  EFECTIVO: 'Efectivo',
  TARJETA: 'Tarjeta',
  TRANSFERENCIA: 'Transferencia',
};

const ESTADOS = {
  PENDIENTE: {
    label: 'Pendiente',
    className: 'bg-amber-100 text-amber-800',
    descripcion: 'Estamos revisando disponibilidad y coordinando el pago.',
  },
  CONVERTIDO: {
    label: 'Confirmado',
    className: 'bg-green-100 text-green-800',
    descripcion: 'El pedido ya se registró como venta.',
  },
  RECHAZADO: {
    label: 'Rechazado',
    className: 'bg-red-100 text-red-800',
    descripcion: 'El local no pudo procesar este pedido.',
  },
  CANCELADO: {
    label: 'Cancelado',
    className: 'bg-gray-100 text-gray-700',
    descripcion: 'Cancelaste este pedido.',
  },
};

const formatearFecha = (fecha) => {
  if (!fecha) return '—';
  return new Date(fecha).toLocaleString('es-AR', {
    dateStyle: 'short',
    timeStyle: 'short',
  });
};

const MisPedidos = () => {
  const [pedidos, setPedidos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [cancelando, setCancelando] = useState(null);

  const fetchPedidos = async () => {
    try {
      const response = await api.get('/api/pedidos/mios');
      setPedidos(response.data);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al cargar tus pedidos');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPedidos();
  }, []);

  const cancelarPedido = async (pedido) => {
    if (!window.confirm(`¿Cancelar el pedido #${pedido.id}?`)) return;

    setCancelando(pedido.id);
    try {
      await api.put(`/api/pedidos/mios/${pedido.id}/cancelar`);
      toast.success('Pedido cancelado');
      await fetchPedidos();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo cancelar el pedido');
    } finally {
      setCancelando(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Mis pedidos</h1>
          <p className="text-gray-500">Seguí el estado de los pedidos que enviaste desde el catálogo.</p>
        </div>
        <div className="flex gap-2">
          <button onClick={fetchPedidos} className="btn-secondary flex items-center gap-2">
            <RefreshCw className="w-4 h-4" aria-hidden="true" />
            Actualizar
          </button>
          <Link to="/catalogo" className="btn-primary flex items-center gap-2">
            <Package className="w-4 h-4" aria-hidden="true" />
            Hacer otro pedido
          </Link>
        </div>
      </div>

      {pedidos.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={ClipboardList}
            title="Todavía no hiciste pedidos"
            description="Agregá productos al carrito desde el catálogo y confirmá tu primer pedido."
            action={<Link to="/catalogo" className="btn-primary">Ir al catálogo</Link>}
          />
        </div>
      ) : (
        <div className="space-y-4">
          {pedidos.map((pedido) => {
            const estado = ESTADOS[pedido.estado] || ESTADOS.PENDIENTE;
            return (
              <div key={pedido.id} className="card">
                <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <h2 className="text-lg font-semibold text-gray-900">Pedido #{pedido.id}</h2>
                      <span className={`px-2 py-1 text-xs font-medium rounded-full ${estado.className}`}>
                        {estado.label}
                      </span>
                    </div>
                    <p className="text-sm text-gray-500 flex items-center gap-2 mt-1">
                      <Calendar className="w-4 h-4" aria-hidden="true" />
                      {formatearFecha(pedido.fecha)}
                    </p>
                    <p className="text-sm text-gray-500 mt-1">{estado.descripcion}</p>
                    {pedido.metodoPago && (
                      <p className="text-sm text-gray-600 mt-1">
                        <span className="font-medium">Método de pago:</span>{' '}
                        {METODOS_PAGO[pedido.metodoPago] || pedido.metodoPago}
                      </p>
                    )}
                  </div>
                  <div className="text-left sm:text-right">
                    <p className="text-sm text-gray-500">Total estimado</p>
                    <p className="text-lg font-bold text-primary-800">
                      {formatMoney(pedido.totalEstimado, 'ARS')}
                    </p>
                  </div>
                </div>

                <div className="mt-4 border-t border-gray-100 pt-4 space-y-2">
                  {pedido.detalles?.map((detalle) => (
                    <div key={detalle.id} className="flex justify-between gap-3 text-sm">
                      <span className="text-gray-700">
                        {detalle.nombreProducto} x {detalle.cantidad}
                      </span>
                      <span className="text-gray-900 font-medium whitespace-nowrap">
                        {formatMoney(detalle.subtotalEstimado, 'ARS')}
                      </span>
                    </div>
                  ))}
                </div>

                {pedido.motivoRechazo && (
                  <p className="mt-3 text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                    Motivo: {pedido.motivoRechazo}
                  </p>
                )}

                {pedido.ventaId && (
                  <p className="mt-3 text-sm text-green-700 bg-green-50 border border-green-100 rounded-lg px-3 py-2">
                    Venta registrada: #{pedido.ventaId}
                  </p>
                )}

                {pedido.metodoPago === 'TRANSFERENCIA' && pedido.estado === 'PENDIENTE' && (
                  <DatosTransferencia className="mt-3" />
                )}

                {pedido.observaciones && (
                  <p className="mt-3 text-sm text-gray-600">
                    <span className="font-medium">Observaciones:</span> {pedido.observaciones}
                  </p>
                )}

                {pedido.estado === 'PENDIENTE' && (
                  <div className="mt-4 flex justify-end">
                    <button
                      onClick={() => cancelarPedido(pedido)}
                      disabled={cancelando === pedido.id}
                      className="btn-secondary flex items-center gap-2 text-red-700"
                    >
                      <XCircle className="w-4 h-4" aria-hidden="true" />
                      {cancelando === pedido.id ? 'Cancelando...' : 'Cancelar pedido'}
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default MisPedidos;
