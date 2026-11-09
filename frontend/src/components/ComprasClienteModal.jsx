import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Receipt, AlertCircle } from 'lucide-react';
import api from '../services/api';
import Modal from './Modal';
import EmptyState from './EmptyState';
import { formatMoney } from '../utils/format';
import { agruparCompras } from '../utils/deuda';

const COLORES = {
  realizadas: 'bg-green-100 text-green-800',
  pendientes: 'bg-orange-100 text-orange-800',
  canceladas: 'bg-gray-100 text-gray-700',
};

const formatearFecha = (fecha) => {
  if (!fecha) return '';
  const valor = new Date(fecha);
  if (Number.isNaN(valor.getTime())) return '';
  return valor.toLocaleDateString('es-AR');
};

const ComprasClienteModal = ({ open, onClose, cliente }) => {
  const navigate = useNavigate();
  const [compras, setCompras] = useState(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open || !cliente?.id) return undefined;

    let activo = true;
    setCargando(true);
    setError('');
    setCompras(null);

    api.get(`/api/clientes/${cliente.id}/compras`, { params: { limite: 100 } })
      .then(({ data }) => {
        if (activo) setCompras(data);
      })
      .catch((err) => {
        if (activo) setError(err.response?.data?.message || 'No se pudieron cargar las compras');
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    return () => {
      activo = false;
    };
  }, [open, cliente?.id]);

  const grupos = agruparCompras(compras);

  return (
    <Modal open={open} onClose={onClose} labelledBy="compras-cliente-titulo">
      <div className="p-6">
        <div className="flex items-start justify-between gap-4 mb-5">
          <div>
            <h2 id="compras-cliente-titulo" className="text-lg font-semibold text-gray-900">
              Compras de {cliente?.nombre || 'cliente'}
            </h2>
            <p className="text-sm text-gray-500">
              Historial de compras registradas en el sistema.
            </p>
          </div>
          <button type="button" onClick={onClose} className="btn-secondary text-sm">
            Cerrar
          </button>
        </div>

        {cargando && (
          <div className="flex items-center justify-center py-10">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
          </div>
        )}

        {!cargando && error && (
          <div className="flex items-center gap-2 rounded-lg bg-red-50 text-red-700 p-4">
            <AlertCircle className="w-5 h-5" />
            <p>{error}</p>
          </div>
        )}

        {!cargando && !error && grupos.length === 0 && (
          <EmptyState
            icon={Receipt}
            title="Sin compras registradas"
            description="Este cliente todavía no tiene movimientos de venta."
          />
        )}

        {!cargando && !error && grupos.map(({ clave, titulo, grupo }) => (
          <section key={clave} className="mb-5 last:mb-0">
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-medium text-gray-900">{titulo}</h3>
              <span className={`text-xs px-2 py-1 rounded-full ${COLORES[clave]}`}>
                {grupo.cantidad} {grupo.cantidad === 1 ? 'compra' : 'compras'}
              </span>
            </div>

            {grupo.items?.length ? (
              <div className="space-y-2">
                {grupo.items.map((venta) => (
                  <button
                    key={venta.id}
                    type="button"
                    onClick={() => {
                      onClose();
                      navigate(`/ventas?filtro=todo#venta-${venta.id}`);
                    }}
                    className="w-full text-left p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <p className="font-medium text-gray-900">Venta #{venta.id}</p>
                        <p className="text-sm text-gray-500">
                          {formatearFecha(venta.fecha)}
                          {venta.cantidadItems != null
                            ? ` · ${venta.cantidadItems} ${venta.cantidadItems === 1 ? 'ítem' : 'ítems'}`
                            : ''}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-semibold text-gray-900">{formatMoney(venta.total, 'ARS')}</p>
                        <p className="text-xs text-gray-500">{venta.estado}</p>
                        {venta.saldoPendiente != null && (
                          <p className="text-xs text-orange-600">
                            Pendiente {formatMoney(venta.saldoPendiente, 'ARS')}
                          </p>
                        )}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-500 py-2">
                No hay compras {titulo.toLowerCase()} para mostrar.
              </p>
            )}
          </section>
        ))}
      </div>
    </Modal>
  );
};

export default ComprasClienteModal;
