import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  Calendar,
  CreditCard,
  FileText,
  Mail,
  MapPin,
  Phone,
  Receipt,
} from 'lucide-react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import Avatar from '../components/Avatar';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import CargarDeuda from '../components/CargarDeuda';
import ComprasClienteModal from '../components/ComprasClienteModal';
import { formatMoney } from '../utils/format';

const formatearFecha = (fecha) => {
  if (!fecha) return '';
  const valor = new Date(fecha);
  if (Number.isNaN(valor.getTime())) return '';
  return valor.toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const FichaCliente = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const [cliente, setCliente] = useState(null);
  const [compras, setCompras] = useState(null);
  const [cargos, setCargos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [comprasAbiertas, setComprasAbiertas] = useState(false);
  const [cargarDeudaAbierto, setCargarDeudaAbierto] = useState(false);

  const cargarDatos = async () => {
    setCargando(true);
    setError('');
    try {
      const peticiones = [
        api.get(`/api/clientes/${id}`),
        api.get(`/api/clientes/${id}/compras`, { params: { limite: 100 } }),
      ];
      if (isAdmin()) peticiones.push(api.get(`/api/clientes/${id}/cargos`));

      const [clienteResp, comprasResp, cargosResp] = await Promise.all(peticiones);
      setCliente(clienteResp.data);
      setCompras(comprasResp.data);
      setCargos(isAdmin() ? (cargosResp?.data || []) : []);
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo cargar la ficha del cliente');
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => {
    cargarDatos();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isAdmin()]);

  if (cargando) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
      </div>
    );
  }

  if (error || !cliente) {
    return (
      <div className="card max-w-xl">
        <div className="flex items-center gap-2 text-red-700">
          <AlertCircle className="w-5 h-5" />
          <p>{error || 'Cliente no encontrado'}</p>
        </div>
        <button type="button" onClick={() => navigate('/clientes')} className="btn-secondary mt-4">
          Volver a clientes
        </button>
      </div>
    );
  }

  const saldo = cliente.saldoCorriente ?? 0;
  const totalCompras = compras?.realizadas?.cantidad || 0;
  const pendientes = compras?.pendientes?.cantidad || 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => navigate('/clientes')}
          className="inline-flex items-center gap-2 text-sm text-gray-600 hover:text-primary-800"
        >
          <ArrowLeft className="w-4 h-4" />
          Volver a clientes
        </button>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => setComprasAbiertas(true)} className="btn-secondary">
            <Receipt className="w-4 h-4" />
            Ver compras
          </button>
          {isAdmin() && (
            <>
              <Link to={`/clientes/${cliente.id}`} className="btn-secondary">
                Editar cliente
              </Link>
              <button
                type="button"
                onClick={() => setCargarDeudaAbierto(true)}
                className="btn-primary"
              >
                <CreditCard className="w-4 h-4" />
                Cargar deuda
              </button>
            </>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="card lg:col-span-1">
          <div className="flex items-center gap-4">
            <Avatar fotoUrl={cliente.fotoUrl} nombre={cliente.nombre} size="xl" tipo="cliente" />
            <div className="min-w-0">
              <h1 className="text-xl font-bold text-gray-900 truncate">{cliente.nombre}</h1>
              <div className="flex items-center gap-2 mt-1">
                <span className={`text-xs px-2 py-1 rounded-full ${
                  cliente.activo ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-700'
                }`}>
                  {cliente.activo ? 'Activo' : 'Dado de baja'}
                </span>
                {cliente.cuentaVinculada && (
                  <span className="text-xs px-2 py-1 rounded-full bg-blue-100 text-blue-800">
                    Con cuenta web
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="mt-5 space-y-3 text-sm text-gray-600">
            {cliente.telefono && (
              <p className="flex items-center gap-2">
                <Phone className="w-4 h-4 text-gray-400" />
                {cliente.telefono}
              </p>
            )}
            {cliente.email && (
              <p className="flex items-center gap-2">
                <Mail className="w-4 h-4 text-gray-400" />
                {cliente.email}
              </p>
            )}
            {cliente.direccion && (
              <p className="flex items-center gap-2">
                <MapPin className="w-4 h-4 text-gray-400" />
                {cliente.direccion}
              </p>
            )}
            {cliente.cuit && (
              <p className="flex items-center gap-2">
                <FileText className="w-4 h-4 text-gray-400" />
                CUIT {cliente.cuit}
              </p>
            )}
            {cliente.condicionIva && (
              <p className="flex items-center gap-2">
                <FileText className="w-4 h-4 text-gray-400" />
                {cliente.condicionIva}
              </p>
            )}
            {cliente.createdAt && (
              <p className="flex items-center gap-2">
                <Calendar className="w-4 h-4 text-gray-400" />
                Cliente desde {formatearFecha(cliente.createdAt)}
              </p>
            )}
          </div>

          {cliente.observaciones && (
            <div className="mt-4 rounded-lg bg-gray-50 p-3 text-sm text-gray-600">
              <p className="font-medium text-gray-700 mb-1">Observaciones</p>
              <p>{cliente.observaciones}</p>
            </div>
          )}
        </div>

        <div className="lg:col-span-2 space-y-6">
          {isAdmin() && (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="card">
                <p className="text-sm text-gray-500">
                  {saldo > 0 ? 'Saldo pendiente' : saldo < 0 ? 'Saldo a favor' : 'Saldo'}
                </p>
                <p className={`text-2xl font-bold mt-1 ${
                  saldo > 0 ? 'text-red-600' : saldo < 0 ? 'text-green-600' : 'text-gray-900'
                }`}>
                  {formatMoney(Math.abs(saldo), 'ARS')}
                </p>
              </div>
              <div className="card">
                <p className="text-sm text-gray-500">Compras realizadas</p>
                <p className="text-2xl font-bold mt-1 text-gray-900">{totalCompras}</p>
              </div>
              <div className="card">
                <p className="text-sm text-gray-500">Compras pendientes</p>
                <p className="text-2xl font-bold mt-1 text-orange-600">{pendientes}</p>
              </div>
            </div>
          )}

          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-900">Compras</h2>
              <button
                type="button"
                onClick={() => setComprasAbiertas(true)}
                className="text-sm text-primary-700 hover:text-primary-900"
              >
                Ver historial completo
              </button>
            </div>

            {compras?.realizadas?.items?.length ? (
              <div className="space-y-2">
                {compras.realizadas.items.slice(0, 5).map((venta) => (
                  <button
                    key={venta.id}
                    type="button"
                    onClick={() => navigate(`/ventas?filtro=todo#venta-${venta.id}`)}
                    className="w-full flex items-center justify-between p-3 rounded-lg bg-gray-50 hover:bg-gray-100 text-left"
                  >
                    <div>
                      <p className="font-medium text-gray-900">Venta #{venta.id}</p>
                      <p className="text-sm text-gray-500">{formatearFecha(venta.fecha)}</p>
                    </div>
                    <div className="text-right">
                      <p className="font-semibold text-gray-900">
                        {formatMoney(venta.total, 'ARS')}
                      </p>
                      <p className="text-xs text-gray-500">{venta.estado}</p>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-500 py-4">No hay compras realizadas.</p>
            )}
          </div>

          {isAdmin() && (
            <div className="card">
              <h2 className="font-semibold text-gray-900 mb-4">Cargos manuales</h2>
              {cargos.length ? (
                <div className="space-y-2">
                  {cargos.map((cargo) => (
                    <div key={cargo.id} className="p-3 rounded-lg border border-gray-100">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-medium text-gray-900">{cargo.motivo}</p>
                          <p className="text-sm text-gray-500">
                            {formatearFecha(cargo.fecha)}
                            {cargo.usuario?.nombre || cargo.usuario?.username
                              ? ` · ${cargo.usuario.nombre || cargo.usuario.username}`
                              : ''}
                          </p>
                        </div>
                        <p className="font-semibold text-primary-800 whitespace-nowrap">
                          +{formatMoney(cargo.monto, 'ARS')}
                        </p>
                      </div>
                      <p className="text-xs text-gray-500 mt-2">
                        Saldo antes {formatMoney(cargo.saldoAntes, 'ARS')}
                        {' · '}
                        Saldo después {formatMoney(cargo.saldoDespues, 'ARS')}
                      </p>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="py-4">
                  <EmptyState
                    icon={CreditCard}
                    title="Sin cargos manuales"
                    description="Las deudas cargadas desde el sistema aparecerán acá."
                  />
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <ComprasClienteModal
        open={comprasAbiertas}
        onClose={() => setComprasAbiertas(false)}
        cliente={cliente}
      />

      {isAdmin() && (
        <Modal
          open={cargarDeudaAbierto}
          onClose={() => setCargarDeudaAbierto(false)}
          labelledBy="cargar-deuda-titulo"
        >
          <div className="p-4">
            <h2 id="cargar-deuda-titulo" className="sr-only">Cargar deuda</h2>
            <CargarDeuda
              clienteInicial={cliente}
              onCancelar={() => setCargarDeudaAbierto(false)}
              onCargada={() => {
                setCargarDeudaAbierto(false);
                cargarDatos();
              }}
            />
          </div>
        </Modal>
      )}
    </div>
  );
};

export default FichaCliente;
