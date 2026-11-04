import React, { useState, useEffect, useLayoutEffect, useRef } from 'react';
import { Search, ShoppingCart, Phone, Package } from 'lucide-react';
import { gsap, ScrollTrigger, prefersReducedMotion } from '../lib/animations';
import api from '../services/api';
import { useCart } from '../context/CartContext';
import { useCurrency, MONEDAS, UMBRAL_DESCUENTO, PCT_DESCUENTO } from '../context/CurrencyContext';
import EmptyState from '../components/EmptyState';

const Catalogo = () => {
  const [productos, setProductos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [categorias, setCategorias] = useState([]);
  const { addItem, items } = useCart();
  const { moneda, setMoneda, formatPrice, hayCotizacion, getWhatsAppUrl } = useCurrency();
  const gridRef = useRef(null);
  const heroRef = useRef(null);
  const ctaRef = useRef(null);

  useEffect(() => {
    fetchProductos();
    fetchCategorias();
  }, []);

  useLayoutEffect(() => {
    if (loading || prefersReducedMotion()) return;

    const hero = heroRef.current;
    const cta = ctaRef.current;
    const tweens = [];
    const triggers = [];

    if (hero) {
      tweens.push(
        gsap.from(Array.from(hero.children), {
          opacity: 0,
          y: 24,
          duration: 0.6,
          stagger: 0.12,
          ease: 'power3.out',
        })
      );
    }

    if (cta) {
      const ctaTween = gsap.from(cta, {
        opacity: 0,
        y: 24,
        duration: 0.5,
        ease: 'power3.out',
        scrollTrigger: { trigger: cta, start: 'top 90%', once: true },
      });
      tweens.push(ctaTween);
      if (ctaTween.scrollTrigger) triggers.push(ctaTween.scrollTrigger);
    }

    ScrollTrigger.refresh();

    return () => {
      tweens.forEach((tween) => tween.kill());
      triggers.forEach((trigger) => trigger.kill());
    };
  }, [loading]);

  useLayoutEffect(() => {
    if (loading || prefersReducedMotion()) return;

    const cards = gridRef.current
      ? Array.from(gridRef.current.querySelectorAll('.producto-card'))
      : [];
    if (!cards.length) return;

    gsap.set(cards, { opacity: 0, y: 28 });
    const triggers = ScrollTrigger.batch(cards, {
      start: 'top 88%',
      once: true,
      onEnter: (batch) =>
        gsap.to(batch, {
          opacity: 1,
          y: 0,
          duration: 0.55,
          stagger: 0.08,
          ease: 'power3.out',
          overwrite: true,
        }),
    });

    ScrollTrigger.refresh();

    return () => triggers.forEach((trigger) => trigger.kill());
  }, [loading, busqueda, filtroCategoria, productos.length]);

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
    const precio = formatPrice(producto.precioUsd ?? producto.precio);
    const mensaje = `Hola! Me interesa el producto: ${producto.nombre} - ${precio}`;
    window.open(getWhatsAppUrl(mensaje), '_blank');
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
      <div ref={heroRef} className="text-center py-8">
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
            aria-label="Buscar productos"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="input pl-10"
          />
        </div>
        <select
          value={filtroCategoria}
          onChange={(e) => setFiltroCategoria(e.target.value)}
          aria-label="Filtrar por categoría"
          className="input w-full sm:w-auto"
        >
          <option value="">Todas las categorías</option>
          {categorias.map(cat => (
            <option key={cat.id} value={cat.id}>{cat.nombre}</option>
          ))}
        </select>
        {/* Toggle de moneda */}
        <div className="flex items-center gap-2 bg-gray-100 p-1 rounded-lg self-start">
          <button
            onClick={() => setMoneda(MONEDAS.USD)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
              moneda === MONEDAS.USD
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            U$S Dólares
          </button>
          <button
            onClick={() => setMoneda(MONEDAS.ARS)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
              moneda === MONEDAS.ARS
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            $ Pesos
          </button>
        </div>
      </div>

      {moneda === MONEDAS.ARS && !hayCotizacion && (
        <p className="text-sm text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-4 py-2">
          No hay cotización configurada. Los precios en pesos no están disponibles por el momento.
        </p>
      )}

      {/* Products Grid */}
      <div ref={gridRef} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {productosFiltrados.map((producto) => (
          <div key={producto.id} className="producto-card card group hover:shadow-lg transition-shadow duration-300 flex flex-col">
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
                <span className="text-2xl font-bold text-primary-800">
                  {formatPrice(producto.precioUsd ?? producto.precio)}
                </span>
              </div>
              <p className="text-xs text-green-700 font-medium mt-1">
                +{UMBRAL_DESCUENTO} unidades: -{Math.round(PCT_DESCUENTO * 100)}% de descuento
              </p>
              <p className={`text-xs mt-1 ${producto.stockActual > 0 ? 'text-green-600' : 'text-red-600'}`}>
                {producto.stockActual > 0 ? `Stock: ${producto.stockActual}` : 'Sin stock'}
              </p>
            </div>

            {/* Action Buttons */}
            <div className="mt-4 flex gap-2">
              <button
                onClick={() => addItem(producto)}
                disabled={producto.stockActual <= 0}
                className="flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg font-medium text-sm bg-primary-800 text-white hover:bg-primary-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ShoppingCart className="w-4 h-4" aria-hidden="true" />
                {producto.stockActual <= 0
                  ? 'Sin stock'
                  : isInCart(producto.id) ? 'Agregar otro' : 'Agregar'}
              </button>
              <button
                onClick={() => contactarWhatsApp(producto)}
                className="p-2 text-green-600 hover:bg-green-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-green-500"
                aria-label={`Consultar por WhatsApp: ${producto.nombre}`}
              >
                <Phone className="w-5 h-5" aria-hidden="true" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Empty State */}
      {productosFiltrados.length === 0 && (
        <EmptyState
          icon={Package}
          title="No se encontraron productos"
          description="Probá con otros términos de búsqueda o cambiá la categoría."
        />
      )}

      {/* Contact CTA */}
      <div ref={ctaRef} className="bg-primary-50 rounded-2xl p-8 text-center mt-8">
        <h3 className="text-xl font-semibold text-gray-900 mb-2">
          ¿No encontrás lo que buscás?
        </h3>
        <p className="text-gray-600 mb-4">
          Contactanos y te ayudamos a encontrar el repuesto que necesitás
        </p>
        <a
          href={getWhatsAppUrl('Hola! Necesito ayuda con un repuesto')}
          target="_blank"
          rel="noopener noreferrer"
          className="btn-whatsapp px-6 py-3"
        >
          <Phone className="w-5 h-5" aria-hidden="true" />
          Contactar por WhatsApp
        </a>
      </div>
    </div>
  );
};

export default Catalogo;
