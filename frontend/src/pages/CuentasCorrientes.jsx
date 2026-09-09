import React, { useState, useEffect } from 'react';
import { Search, DollarSign, AlertCircle, CheckCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const CuentasCorrientes = () => {
  const [clientes, setClientes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [clienteSeleccionado, setClienteSeleccionado] = useState(null);
  const [ventas, setVentas] = useState([]);
  const [ajustandoSaldo, setAjustandoSaldo] = useState(null);
  const [montoAjuste, setMontoAjuste] = useState('');

  useEffect(() => {
    fetchClientesConDeuda();
  }, []);

  const fetchClientesConDeuda = async () => {
    try {
      const response = await api.get('/api/clientes/deudores');
      setClientes(response.data);
    } catch (error) {
      toast.error('Error al cargar clientes');
    } finally {
      setLoading(false);
    }
  };

  const fetchVentasCliente = async (clienteId) => {
    try {
      const response = await api.get(`/api/ventas/cliente/${clienteId}`);
      setVentas(response.data);
    } catch (error) {
      toast.error('Error al cargar ventas');
    }
  };

  const handleSeleccionarCliente = (cliente) => {
    setClienteSeleccionado(cliente);
    fetchVentasCliente(cliente.id);
  };

  const handleRegistrarPago = async (clienteId) => {
    const monto = parseFloat(montoAjuste);
    if (!monto || monto <= 0) {
      toast.error('Ingresá un monto válido');
      return;
    }
    try {
      await api.patch(`/api/clientes/${clienteId}/saldo`, { monto, tipo: 'restar' });
      toast.success('Pago registrado');
      setAjustandoSaldo(null);
      setMontoAjuste('');
      const response = await api.get('/api/clientes/deudores');
      setClientes(response.data);
      if (clienteSeleccionado?.id === clienteId) {
        const updated = response.data.find(c => c.id === clienteId);
        if (updated) setClienteSeleccionado(updated);
      }
    } catch (error) {
      toast.error('Error al ajustar saldo');
    }
  };

  const totalDeuda = clientes.reduce((sum, c) => sum + (c.saldoCorriente || 0), 0);

  const clientesFiltrados = clientes.filter(c =>
    c.nombre.toLowerCase().includes(busqueda.toLowerCase())
  );

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
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Cuentas Corrientes</h1>
        <p className="text-gray-500">Gestión de deudas y pagos de clientes</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center">
              <AlertCircle className="w-6 h-6 text-red-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Clientes con deuda</p>
              <p className="text-2xl font-bold text-gray-900">{clientes.length}</p>
            </div>
          </div>
        </div>
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 bg-orange-100 rounded-xl flex items-center justify-center">
              <DollarSign className="w-6 h-6 text-orange-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Total adeudado</p>
              <p className="text-2xl font-bold text-orange-600">
                ${totalDeuda.toLocaleString('es-AR')}
              </p>
            </div>
          </div>
        </div>
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center">
              <CheckCircle className="w-6 h-6 text-green-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Clientes al día</p>
              <p className="text-2xl font-bold text-green-600">--</p>
            </div>
          </div>
        </div>
      </div>

      {/* Search */}
      <div className="card">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar cliente..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="input pl-10"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Clients List */}
        <div className="lg:col-span-1">
          <div className="card">
            <h3 className="font-semibold text-gray-900 mb-4">Clientes con Deuda</h3>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {clientesFiltrados.map((cliente) => (
                <button
                  key={cliente.id}
                  onClick={() => handleSeleccionarCliente(cliente)}
                  className={`w-full text-left p-3 rounded-lg transition-colors ${
                    clienteSeleccionado?.id === cliente.id
                      ? 'bg-primary-50 border border-primary-200'
                      : 'hover:bg-gray-50 border border-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-gray-900">{cliente.nombre}</p>
                      <p className="text-sm text-gray-500">{cliente.telefono}</p>
                    </div>
                    <span className="font-semibold text-red-600">
                      ${cliente.saldoCorriente?.toLocaleString('es-AR')}
                    </span>
                  </div>
                </button>
              ))}
              {clientesFiltrados.length === 0 && (
                <p className="text-center text-gray-500 py-4">
                  No se encontraron clientes con deuda
                </p>
              )}
            </div>
          </div>
        </div>

        {/* Client Details */}
        <div className="lg:col-span-2">
          {clienteSeleccionado ? (
            <div className="card">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">
                    {clienteSeleccionado.nombre}
                  </h3>
                  <p className="text-gray-500">{clienteSeleccionado.telefono}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-gray-500">Saldo pendiente</p>
                  <p className="text-2xl font-bold text-red-600">
                    ${clienteSeleccionado.saldoCorriente?.toLocaleString('es-AR')}
                  </p>
                  <button
                    onClick={() => {
                      setAjustandoSaldo(clienteSeleccionado.id);
                      setMontoAjuste('');
                    }}
                    className="mt-2 text-sm text-blue-600 hover:text-blue-800"
                  >
                    Registrar pago
                  </button>
                  {ajustandoSaldo === clienteSeleccionado.id && (
                    <div className="mt-2 flex gap-2 justify-end">
                      <input
                        type="number"
                        value={montoAjuste}
                        onChange={(e) => setMontoAjuste(e.target.value)}
                        placeholder="Monto"
                        className="w-24 px-2 py-1 border rounded text-sm"
                      />
                      <button
                        onClick={() => handleRegistrarPago(clienteSeleccionado.id)}
                        className="px-3 py-1 bg-green-600 text-white text-sm rounded hover:bg-green-700"
                      >
                        Pagar
                      </button>
                      <button
                        onClick={() => { setAjustandoSaldo(null); setMontoAjuste(''); }}
                        className="px-3 py-1 bg-gray-300 text-gray-700 text-sm rounded hover:bg-gray-400"
                      >
                        Cancelar
                      </button>
                    </div>
                  )}
                </div>
              </div>

              <h4 className="font-medium text-gray-700 mb-3">Historial de ventas</h4>
              <div className="space-y-3">
                {ventas.map((venta) => (
                  <div 
                    key={venta.id} 
                    className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                  >
                    <div>
                      <p className="font-medium text-gray-900">
                        Venta #{venta.id}
                      </p>
                      <p className="text-sm text-gray-500">
                        {new Date(venta.fecha).toLocaleDateString('es-AR')}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="font-semibold text-gray-900">
                        ${venta.total?.toLocaleString('es-AR')}
                      </p>
                      <span className={`text-xs px-2 py-1 rounded-full ${
                        venta.estado === 'PAGADA' 
                          ? 'bg-green-100 text-green-800'
                          : 'bg-yellow-100 text-yellow-800'
                      }`}>
                        {venta.estado}
                      </span>
                    </div>
                  </div>
                ))}
                {ventas.length === 0 && (
                  <p className="text-center text-gray-500 py-4">
                    No hay ventas registradas
                  </p>
                )}
              </div>
            </div>
          ) : (
            <div className="card flex items-center justify-center h-64">
              <div className="text-center text-gray-500">
                <DollarSign className="w-12 h-12 mx-auto text-gray-300 mb-2" />
                <p>Seleccioná un cliente para ver sus detalles</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CuentasCorrientes;
