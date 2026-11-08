import React, { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Search, Plus, Edit2, Trash2, Package, Filter, AlertTriangle } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import EmptyState from '../components/EmptyState';
import { formatMoney } from '../utils/format';

const esStockBajo = (p) => p.stockMinimo != null && p.stockActual <= p.stockMinimo;

const ProductoImagen = ({ producto }) => {
  const [error, setError] = useState(false);

  if (!producto.imagenUrl || error) {
    return (
      <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center">
        <Package className="w-5 h-5 text-gray-400" />
      </div>
    );
  }

  return (
    <div className="w-10 h-10 bg-gray-100 rounded-lg overflow-hidden flex-shrink-0">
      <img
        src={producto.imagenUrl}
        alt={producto.nombre}
        className="w-full h-full object-cover"
        onError={() => setError(true)}
      />
    </div>
  );
};

const Productos = () => {
  const [productos, setProductos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [categorias, setCategorias] = useState([]);
  const [searchParams, setSearchParams] = useSearchParams();
  const { isAdmin } = useAuth();

  const filtroStockBajo = searchParams.get('stock') === 'bajo';

  const toggleStockBajo = () => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (next.get('stock') === 'bajo') {
        next.delete('stock');
      } else {
        next.set('stock', 'bajo');
      }
      return next;
    }, { replace: true });
  };

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
    if (filtroStockBajo && !esStockBajo(p)) return false;
    return true;
  });

  const productosVisibles = filtroStockBajo
    ? [...productosFiltrados].sort(
        (a, b) => (a.stockActual - a.stockMinimo) - (b.stockActual - b.stockMinimo)
          || a.id - b.id
      )
    : productosFiltrados;

  const cantidadStockBajo = productos.filter(esStockBajo).length;

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
          <p className="text-gray-500">
            {filtroStockBajo
              ? `${productosVisibles.length} productos con stock bajo`
              : `${productos.length} productos en inventario`}
          </p>
        </div>
        <Link to="/productos/nuevo" className="btn-primary flex items-center gap-2">
          <Plus className="w-5 h-5" />
          Nuevo Producto
        </Link>
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
              onKeyDown={(e) => e.key === 'Enter' && handleBuscar()}
              aria-label="Buscar productos"
              className="input pl-10"
            />
          </div>
          <div className="flex gap-2">
            <select
              value={filtroCategoria}
              onChange={(e) => setFiltroCategoria(e.target.value)}
              aria-label="Filtrar por categoría"
              className="input w-auto"
            >
              <option value="">Todas las categorías</option>
              {categorias.map(cat => (
                <option key={cat.id} value={cat.id}>{cat.nombre}</option>
              ))}
            </select>
            <button
              type="button"
              onClick={toggleStockBajo}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg border text-sm font-medium transition-colors duration-200 ${
                filtroStockBajo
                  ? 'bg-red-50 border-red-200 text-red-700 hover:bg-red-100'
                  : 'bg-white border-gray-300 text-gray-700 hover:bg-gray-50'
              }`}
            >
              <AlertTriangle className="w-4 h-4" />
              Stock bajo ({cantidadStockBajo})
            </button>
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
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Precio (USD)</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Stock</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {productosVisibles.map((producto) => (
                <tr key={producto.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-3">
                      <ProductoImagen producto={producto} />
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
                      {producto.precioUsd > 0
                        ? formatMoney(producto.precioUsd, 'USD')
                        : <span className="text-sm text-gray-400 font-normal">Sin precio USD</span>}
                    </span>
                    {producto.precioUsd > 0 && producto.precio > 0 && (
                      <p className="text-xs text-gray-500 mt-0.5">
                        {formatMoney(producto.precio, 'ARS')}
                      </p>
                    )}
                  </td>
                  <td className="py-3 px-4 text-right">
                    <span className={`font-semibold ${
                      esStockBajo(producto)
                        ? 'text-red-600'
                        : 'text-green-600'
                    }`}>
                      {producto.stockActual}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <Link
                        to={`/productos/${producto.id}`}
                        className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                        aria-label={`Editar ${producto.nombre}`}
                      >
                        <Edit2 className="w-4 h-4" aria-hidden="true" />
                      </Link>
                      {isAdmin() && (
                        <button
                          onClick={() => handleEliminar(producto.id)}
                          className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                          aria-label={`Eliminar ${producto.nombre}`}
                        >
                          <Trash2 className="w-4 h-4" aria-hidden="true" />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {productosVisibles.length === 0 && (
                <tr>
                  <td colSpan="6">
                    <EmptyState
                      icon={Package}
                      title={filtroStockBajo ? 'No hay productos con stock bajo' : 'No se encontraron productos'}
                      description={
                        filtroStockBajo
                          ? 'Todos los productos tienen stock suficiente.'
                          : 'Probá con otro término de búsqueda o cambiá la categoría.'
                      }
                    />
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
