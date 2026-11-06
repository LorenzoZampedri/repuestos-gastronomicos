import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Search, Plus, Edit2, Trash2, Users, Phone, Mail, DollarSign } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import EmptyState from '../components/EmptyState';
import { formatMoney } from '../utils/format';

const Clientes = () => {
  const { isAdmin } = useAuth();
  const [clientes, setClientes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [ajustandoSaldo, setAjustandoSaldo] = useState(null);
  const [montoAjuste, setMontoAjuste] = useState('');
  const [passwordAjuste, setPasswordAjuste] = useState('');

  useEffect(() => {
    fetchClientes();
  }, []);

  const fetchClientes = async () => {
    try {
      const response = await api.get('/api/clientes');
      setClientes(response.data);
    } catch (error) {
      toast.error('Error al cargar clientes');
    } finally {
      setLoading(false);
    }
  };

  const handleBuscar = async () => {
    if (!busqueda.trim()) {
      fetchClientes();
      return;
    }
    try {
      const response = await api.get(`/api/clientes/buscar?q=${busqueda}`);
      setClientes(response.data);
    } catch (error) {
      toast.error('Error al buscar');
    }
  };

  const handleEliminar = async (id) => {
    if (!window.confirm('¿Estás seguro de eliminar este cliente?')) return;
    
    try {
      await api.delete(`/api/clientes/${id}`);
      toast.success('Cliente eliminado');
      fetchClientes();
    } catch (error) {
      toast.error('Error al eliminar');
    }
  };

  const handleAjustarSaldo = async (clienteId, tipo) => {
    const monto = parseFloat(montoAjuste);
    if (!monto || monto <= 0) {
      toast.error('Ingresá un monto válido');
      return;
    }
    if (tipo === 'restar' && !passwordAjuste.trim()) {
      toast.error('Ingresá tu contraseña para autorizar el pago');
      return;
    }
    
    try {
      await api.patch(`/api/clientes/${clienteId}/saldo`, {
        monto,
        tipo,
        password: tipo === 'restar' ? passwordAjuste : undefined
      });
      toast.success(tipo === 'restar' ? 'Pago registrado' : 'Deuda aumentada');
      setAjustandoSaldo(null);
      setMontoAjuste('');
      setPasswordAjuste('');
      fetchClientes();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al ajustar saldo');
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
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Clientes</h1>
          <p className="text-gray-500">{clientes.length} clientes registrados</p>
        </div>
        <Link to="/clientes/nuevo" className="btn-primary flex items-center gap-2">
          <Plus className="w-5 h-5" />
          Nuevo Cliente
        </Link>
      </div>

      {/* Search */}
      <div className="card">
        <div className="flex gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Buscar por nombre o teléfono..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleBuscar()}
              aria-label="Buscar clientes"
              className="input pl-10"
            />
          </div>
          <button onClick={handleBuscar} className="btn-primary">
            Buscar
          </button>
        </div>
      </div>

      {/* Clients Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {clientes.map((cliente) => (
          <div key={cliente.id} className="card hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 bg-primary-100 rounded-full flex items-center justify-center">
                  <Users className="w-6 h-6 text-primary-800" />
                </div>
                <div>
                  <h3 className="font-semibold text-gray-900">{cliente.nombre}</h3>
                  <p className="text-sm text-gray-500">{cliente.condicionIva}</p>
                </div>
              </div>
              <div className="flex gap-1">
                <Link
                  to={`/clientes/${cliente.id}`}
                  className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                  aria-label={`Editar ${cliente.nombre}`}
                >
                  <Edit2 className="w-4 h-4" aria-hidden="true" />
                </Link>
                {isAdmin() && (
                  <button
                    onClick={() => handleEliminar(cliente.id)}
                    className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                    aria-label={`Eliminar ${cliente.nombre}`}
                  >
                    <Trash2 className="w-4 h-4" aria-hidden="true" />
                  </button>
                )}
              </div>
            </div>
            
            <div className="mt-4 space-y-2">
              {cliente.telefono && (
                <div className="flex items-center gap-2 text-sm text-gray-600">
                  <Phone className="w-4 h-4 text-gray-400" />
                  {cliente.telefono}
                </div>
              )}
              {cliente.email && (
                <div className="flex items-center gap-2 text-sm text-gray-600">
                  <Mail className="w-4 h-4 text-gray-400" />
                  {cliente.email}
                </div>
              )}
            </div>

            {isAdmin() && cliente.saldoCorriente !== 0 && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-500">
                    {cliente.saldoCorriente > 0 ? 'Saldo pendiente' : 'Saldo a favor'}
                  </span>
                  <span className={`font-semibold ${cliente.saldoCorriente > 0 ? 'text-red-600' : 'text-green-600'}`}>
                    {formatMoney(Math.abs(cliente.saldoCorriente), 'ARS')}
                  </span>
                </div>
                {cliente.saldoCorriente > 0 && (ajustandoSaldo === cliente.id ? (
                  <div className="flex flex-col gap-2">
                    <input
                      type="number"
                      value={montoAjuste}
                      onChange={(e) => setMontoAjuste(e.target.value)}
                      placeholder="Monto"
                      className="input text-sm"
                      min="0"
                      step="0.01"
                      autoFocus
                    />
                    <input
                      type="password"
                      value={passwordAjuste}
                      onChange={(e) => setPasswordAjuste(e.target.value)}
                      placeholder="Tu contraseña"
                      autoComplete="current-password"
                      className="input text-sm"
                    />
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleAjustarSaldo(cliente.id, 'restar')}
                        className="px-3 py-1 text-xs bg-green-600 text-white rounded-lg hover:bg-green-700"
                      >
                        Autorizar pago
                      </button>
                      <button
                        onClick={() => {
                          setAjustandoSaldo(null);
                          setMontoAjuste('');
                          setPasswordAjuste('');
                        }}
                        className="px-3 py-1 text-xs bg-gray-200 text-gray-600 rounded-lg hover:bg-gray-300"
                      >
                        Cancelar
                      </button>
                    </div>
                    {parseFloat(montoAjuste) > cliente.saldoCorriente && (
                      <p className="text-xs text-orange-600">
                        El monto supera la deuda: quedará un saldo a favor de{' '}
                        {formatMoney(parseFloat(montoAjuste) - cliente.saldoCorriente, 'ARS')}.
                      </p>
                    )}
                  </div>
                ) : (
                  <button
                    onClick={() => {
                      setAjustandoSaldo(cliente.id);
                      setMontoAjuste('');
                      setPasswordAjuste('');
                    }}
                    className="w-full flex items-center justify-center gap-1 text-xs text-primary-600 hover:text-primary-800 py-1"
                  >
                    <DollarSign className="w-3 h-3" />
                    Registrar pago
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
        
        {clientes.length === 0 && (
          <div className="col-span-full">
            <EmptyState
              icon={Users}
              title="No se encontraron clientes"
              description="Probá con otro término de búsqueda o agregá un cliente nuevo."
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default Clientes;
