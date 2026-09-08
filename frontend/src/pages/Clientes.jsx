import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Search, Plus, Edit2, Trash2, Users, Phone, Mail } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const Clientes = () => {
  const [clientes, setClientes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');

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
              onKeyPress={(e) => e.key === 'Enter' && handleBuscar()}
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
                  className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors"
                >
                  <Edit2 className="w-4 h-4" />
                </Link>
                <button
                  onClick={() => handleEliminar(cliente.id)}
                  className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
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

            {cliente.saldoCorriente > 0 && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-500">Saldo pendiente</span>
                  <span className="font-semibold text-red-600">
                    ${cliente.saldoCorriente?.toLocaleString('es-AR')}
                  </span>
                </div>
              </div>
            )}
          </div>
        ))}
        
        {clientes.length === 0 && (
          <div className="col-span-full text-center py-12 text-gray-500">
            No se encontraron clientes
          </div>
        )}
      </div>
    </div>
  );
};

export default Clientes;
