import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Search, Plus, Edit2, Trash2, Package, Filter } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

const Productos = () => {
  const [productos, setProductos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [categorias, setCategorias] = useState([]);
  const { isAdmin } = useAuth();

  useEffect(() => {
    fetchProductos();
    fetchCategorias();
  }, []);

  const fetchProductos = async () => {
    try {
      const response = await api.get('/api/productos');
      setProductos(response.data);
    } catch (error) {
      toast.error('Error al cargar productos');
    } finally {
      setLoading(false);
    }
  };

  const fetchCategorias = async () => {
    try {
      const response = await api.get('/api/categorias');
      setCategorias(response.data);
    } catch (error) {
      console.error('Error fetching categories:', error);
    }
  };

  const handleBuscar = async () => {
    if (!busqueda.trim()) {
      fetchProductos();
      return;
    }
    try {
      const response = await api.get(`/api/productos/buscar?q=${busqueda}`);
      setProductos(response.data);
    } catch (error) {
      toast.error('Error al buscar');
    }
  };

  const handleEliminar = async (id) => {
    if (!window.confirm('¿Estás seguro de eliminar este producto?')) return;
    
    try {
      await api.delete(`/api/productos/${id}`);
      toast.success('Producto eliminado');
      fetchProductos();
    } catch (error) {
      toast.error('Error al eliminar');
    }
  };

  const productosFiltrados = productos.filter(p => {
    if (filtroCategoria && p.categoria?.id !== parseInt(filtroCategoria)) return false;
    return true;
  });

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
          <h1 className="text-2xl font-bold text-gray-900">Productos</h1>
          <p className="text-gray-500">{productos.length} productos en inventario</p>
        </div>
        {isAdmin() && (
          <Link to="/productos/nuevo" className="btn-primary flex items-center gap-2">
            <Plus className="w-5 h-5" />
            Nuevo Producto
          </Link>
        )}
      </div>

      {/* Search and Filters */}
      <div className="card">
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Buscar por nombre, código o descripción..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleBuscar()}
              className="input pl-10"
            />
          </div>
          <div className="flex gap-2">
            <select
              value={filtroCategoria}
              onChange={(e) => setFiltroCategoria(e.target.value)}
              className="input w-auto"
            >
              <option value="">Todas las categorías</option>
              {categorias.map(cat => (
                <option key={cat.id} value={cat.id}>{cat.nombre}</option>
              ))}
            </select>
            <button onClick={handleBuscar} className="btn-primary">
              Buscar
            </button>
          </div>
        </div>
      </div>

      {/* Products Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Producto</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Código</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Categoría</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Precio</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Stock</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {productosFiltrados.map((producto) => (
                <tr key={producto.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center">
                        <Package className="w-5 h-5 text-gray-400" />
                      </div>
                      <div>
                        <p className="font-medium text-gray-900">{producto.nombre}</p>
                        <p className="text-sm text-gray-500 truncate max-w-xs">
                          {producto.descripcion}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <span className="text-sm text-gray-600 font-mono">
                      {producto.codigoBarras || '-'}
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <span className="px-2 py-1 bg-gray-100 text-gray-700 text-sm rounded-full">
                      {producto.categoria?.nombre || 'Sin categoría'}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <span className="font-semibold text-gray-900">
                      ${producto.precio?.toLocaleString('es-AR')}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <span className={`font-semibold ${
                      producto.stockActual <= (producto.stockMinimo || 5) 
                        ? 'text-red-600' 
                        : 'text-green-600'
                    }`}>
                      {producto.stockActual}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end gap-2">
                      {isAdmin() && (
                        <>
                          <Link
                            to={`/productos/${producto.id}`}
                            className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors"
                          >
                            <Edit2 className="w-4 h-4" />
                          </Link>
                          <button
                            onClick={() => handleEliminar(producto.id)}
                            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {productosFiltrados.length === 0 && (
                <tr>
                  <td colSpan="6" className="py-12 text-center text-gray-500">
                    No se encontraron productos
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default Productos;
