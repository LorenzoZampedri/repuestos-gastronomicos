import React, { useState, useEffect } from 'react';
import { Search, ShoppingCart, Phone, Package, Check } from 'lucide-react';
import api from '../services/api';
import { useCart } from '../context/CartContext';

const Catalogo = () => {
  const [productos, setProductos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [categorias, setCategorias] = useState([]);
  const { addItem, items } = useCart();

  useEffect(() => {
    fetchProductos();
    fetchCategorias();
  }, []);

  const fetchProductos = async () => {
    try {
      const response = await api.get('/api/productos/catalogo');
      setProductos(response.data);
    } catch (error) {
      console.error('Error:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchCategorias = async () => {
    try {
      const response = await api.get('/api/categorias');
      setCategorias(response.data);
    } catch (error) {
      console.error('Error:', error);
    }
  };

  const productosFiltrados = productos.filter(p => {
    const coincideBusqueda = p.nombre.toLowerCase().includes(busqueda.toLowerCase()) ||
                            p.descripcion?.toLowerCase().includes(busqueda.toLowerCase());
    const coincideCategoria = !filtroCategoria || p.categoria?.id === parseInt(filtroCategoria);
    return coincideBusqueda && coincideCategoria;
  });

  const isInCart = (productId) => items.some(item => item.id === productId);

  const contactarWhatsApp = (producto) => {
    const precio = producto.precio > 0
      ? `$${producto.precio.toLocaleString('es-AR')}`
      : `U$S ${producto.precioUsd || 'Consultar'}`;
    const mensaje = `Hola! Me interesa el producto: ${producto.nombre} - ${precio}`;
    window.open(`https://wa.me/5491112345678?text=${encodeURIComponent(mensaje)}`, '_blank');
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
      {/* Hero Section */}
      <div className="text-center py-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          Catálogo de Productos
        </h1>
        <p className="text-gray-500 max-w-2xl mx-auto">
          Repuestos para gastronomía pesada. Hornos pizzeros, freidoras industriales, 
          anafes, cafeteras y más.
        </p>
      </div>

      {/* Search and Filters */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder="Buscar productos..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="input pl-10"
          />
        </div>
        <select
          value={filtroCategoria}
          onChange={(e) => setFiltroCategoria(e.target.value)}
          className="input w-full sm:w-auto"
        >
          <option value="">Todas las categorías</option>
          {categorias.map(cat => (
            <option key={cat.id} value={cat.id}>{cat.nombre}</option>
          ))}
        </select>
      </div>

      {/* Products Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {productosFiltrados.map((producto) => (
          <div key={producto.id} className="card group hover:shadow-lg transition-all flex flex-col">
            {/* Product Image */}
            <div className="aspect-square bg-gray-100 rounded-xl mb-4 overflow-hidden">
              {producto.imagenUrl ? (
                <img 
                  src={producto.imagenUrl} 
                  alt={producto.nombre}
                  className="w-full h-full object-contain p-2 group-hover:scale-105 transition-transform duration-300"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center">
                  <Package className="w-16 h-16 text-gray-300" />
                </div>
              )}
            </div>

            {/* Product Info */}
            <div className="flex-1">
              <span className="text-xs text-primary-600 font-medium">
                {producto.categoria?.nombre || 'Sin categoría'}
              </span>
              <h3 className="font-semibold text-gray-900 mt-1">{producto.nombre}</h3>
              {producto.descripcion && (
                <p className="text-sm text-gray-500 mt-1 line-clamp-2">
                  {producto.descripcion}
                </p>
              )}
            </div>

            {/* Price and Stock */}
            <div className="mt-4">
              <div className="flex items-baseline gap-2">
                {producto.precio > 0 && (
                  <span className="text-2xl font-bold text-primary-800">
                    ${producto.precio.toLocaleString('es-AR')}
                  </span>
                )}
                {producto.precioUsd > 0 && (
                  <span className="text-lg font-semibold text-green-700">
                    U$S {producto.precioUsd}
                  </span>
                )}
                {!producto.precio && !producto.precioUsd && (
                  <span className="text-sm text-gray-500">Consultar precio</span>
                )}
              </div>
              <p className={`text-xs mt-1 ${producto.stockActual > 0 ? 'text-green-600' : 'text-red-600'}`}>
                {producto.stockActual > 0 ? `Stock: ${producto.stockActual}` : 'Sin stock'}
              </p>
            </div>

            {/* Action Buttons */}
            <div className="mt-4 flex gap-2">
              <button
                onClick={() => addItem(producto)}
                disabled={isInCart(producto.id)}
                className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg font-medium text-sm transition-colors ${
                  isInCart(producto.id)
                    ? 'bg-green-100 text-green-700 cursor-default'
                    : 'bg-primary-800 text-white hover:bg-primary-700'
                }`}
              >
                {isInCart(producto.id) ? (
                  <>
                    <Check className="w-4 h-4" />
                    Agregado
                  </>
                ) : (
                  <>
                    <ShoppingCart className="w-4 h-4" />
                    Agregar
                  </>
                )}
              </button>
              <button
                onClick={() => contactarWhatsApp(producto)}
                disabled={producto.stockActual <= 0}
                className="p-2 text-green-600 hover:bg-green-50 rounded-lg transition-colors"
                title="Consultar por WhatsApp"
              >
                <Phone className="w-5 h-5" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Empty State */}
      {productosFiltrados.length === 0 && (
        <div className="text-center py-12">
          <Package className="w-16 h-16 mx-auto text-gray-300 mb-4" />
          <h3 className="text-lg font-medium text-gray-900 mb-2">
            No se encontraron productos
          </h3>
          <p className="text-gray-500">
            Intentá con otros términos de búsqueda
          </p>
        </div>
      )}

      {/* Contact CTA */}
      <div className="bg-primary-50 rounded-2xl p-8 text-center mt-8">
        <h3 className="text-xl font-semibold text-gray-900 mb-2">
          ¿No encontrás lo que buscás?
        </h3>
        <p className="text-gray-600 mb-4">
          Contactanos y te ayudamos a encontrar el repuesto que necesitás
        </p>
        <a
          href="https://wa.me/5491112345678?text=Hola! Necesito ayuda con un repuesto"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 bg-green-600 text-white px-6 py-3 rounded-xl font-medium hover:bg-green-700 transition-colors"
        >
          <Phone className="w-5 h-5" />
          Contactar por WhatsApp
        </a>
      </div>
    </div>
  );
};

export default Catalogo;
