import React, { useState, useEffect, useLayoutEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Package, Phone, ShoppingCart, X, Trash2, MessageCircle, Plus, Minus, LogOut, ClipboardList } from 'lucide-react';
import { gsap, prefersReducedMotion } from '../lib/animations';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { useCurrency, PCT_DESCUENTO, UMBRAL_DESCUENTO } from '../context/CurrencyContext';
import { formatMoney, normalizePhone } from '../utils/format';
import PageTransition from './PageTransition';
import PedidoModal from './PedidoModal';
import Avatar from './Avatar';
import SelectorFoto from './SelectorFoto';

const CartDrawer = ({ isOpen, onClose, onHacerPedido }) => {
  const {
    items, removeItem, updateQuantity, clearCart, contactWhatsApp,
    tieneDescuento, precioUnitario, precioLista, subtotalItem, cartTotal
  } = useCart();
  const { moneda, hayCotizacion } = useCurrency();
  const { user } = useAuth();

  const overlayRef = useRef(null);
  const panelRef = useRef(null);
  const closeButtonRef = useRef(null);

  const fmt = (valor) => {
    if (valor == null || valor <= 0) return 'Consultar';
    return formatMoney(valor, moneda);
  };

  useLayoutEffect(() => {
    if (overlayRef.current) gsap.set(overlayRef.current, { opacity: 0 });
    if (panelRef.current) gsap.set(panelRef.current, { xPercent: 100 });
  }, []);

  useEffect(() => {
    const overlay = overlayRef.current;
    const panel = panelRef.current;
    if (!overlay || !panel) return;

    if (prefersReducedMotion()) {
      gsap.set(overlay, { opacity: isOpen ? 1 : 0 });
      gsap.set(panel, { xPercent: isOpen ? 0 : 100 });
      return;
    }

    const tl = gsap.timeline({ defaults: { ease: 'power3.out' } });
    if (isOpen) {
      tl.to(overlay, { opacity: 1, duration: 0.3 }, 0)
        .to(panel, { xPercent: 0, duration: 0.4 }, 0);
    } else {
      tl.to(overlay, { opacity: 0, duration: 0.22 }, 0)
        .to(panel, { xPercent: 100, duration: 0.32 }, 0);
    }

    return () => tl.kill();
  }, [isOpen]);

  useEffect(() => {
    if (panelRef.current) panelRef.current.inert = !isOpen;
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    closeButtonRef.current?.focus();

    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [isOpen, onClose]);

  return (
    <div className={`fixed inset-0 z-50 ${isOpen ? '' : 'pointer-events-none'}`} aria-hidden={!isOpen}>
      <div ref={overlayRef} className="absolute inset-0 bg-gray-900/50" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-title"
        className="absolute right-0 top-0 h-full w-full max-w-md bg-white shadow-xl flex flex-col"
      >
        <div className="flex items-center justify-between p-4 border-b">
          <h2 id="cart-title" className="text-lg font-bold text-gray-900">Mi Carrito ({items.length})</h2>
          <button
            ref={closeButtonRef}
            onClick={onClose}
            className="p-2 hover:bg-gray-100 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="Cerrar carrito"
          >
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {items.length === 0 ? (
            <div className="text-center py-12">
              <ShoppingCart className="w-12 h-12 mx-auto text-gray-300 mb-4" aria-hidden="true" />
              <p className="text-gray-500">El carrito está vacío</p>
            </div>
          ) : (
            <div className="space-y-4">
              {items.map(item => (
                <div key={item.id} className="flex gap-3 p-3 bg-gray-50 rounded-lg">
                  <div className="w-16 h-16 flex-shrink-0 bg-white rounded-lg overflow-hidden">
                    {item.imagenUrl ? (
                      <img src={item.imagenUrl} alt={item.nombre} className="w-full h-full object-contain p-1" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center">
                        <Package className="w-6 h-6 text-gray-300" aria-hidden="true" />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <h4 className="text-sm font-medium text-gray-900 truncate">{item.nombre}</h4>
                    <p className="text-sm text-gray-500">{item.categoria?.nombre}</p>
                    <p className="text-sm font-bold text-primary-800">
                      {fmt(precioUnitario(item))} c/u
                      {tieneDescuento(item) && (
                        <span className="ml-1 text-xs font-normal text-gray-400 line-through">
                          {fmt(precioLista(item))}
                        </span>
                      )}
                    </p>
                    {tieneDescuento(item) && (
                      <p className="text-xs font-medium text-green-700">
                        -{Math.round(PCT_DESCUENTO * 100)}% descuento por +{UMBRAL_DESCUENTO} unidades
                      </p>
                    )}
                    <p className="text-xs text-gray-500">
                      Subtotal: {fmt(subtotalItem(item))}
                    </p>
                  </div>
                  <div className="flex flex-col items-end justify-between">
                    <button
                      onClick={() => removeItem(item.id)}
                      className="p-1 text-gray-400 hover:text-red-600 transition-colors rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                      aria-label={`Quitar ${item.nombre} del carrito`}
                    >
                      <Trash2 className="w-4 h-4" aria-hidden="true" />
                    </button>
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => updateQuantity(item.id, item.cantidad - 1)}
                        className="w-6 h-6 flex items-center justify-center rounded bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                        aria-label={`Quitar una unidad de ${item.nombre}`}
                      >
                        <Minus className="w-3 h-3" aria-hidden="true" />
                      </button>
                      <span className="w-6 text-center text-sm font-medium text-gray-900">{item.cantidad}</span>
                      <button
                        onClick={() => updateQuantity(item.id, item.cantidad + 1)}
                        className="w-6 h-6 flex items-center justify-center rounded bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                        aria-label={`Agregar una unidad de ${item.nombre}`}
                      >
                        <Plus className="w-3 h-3" aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {items.length > 0 && (
          <div className="p-4 border-t space-y-3">
            {moneda === 'ARS' && !hayCotizacion ? (
              <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                No hay cotización configurada para convertir a pesos.
              </p>
            ) : (
              <div className="flex justify-between items-center text-lg font-bold">
                <span className="text-gray-900">Total</span>
                <span className="text-primary-800">{fmt(cartTotal)}</span>
              </div>
            )}
            <button
              onClick={clearCart}
              className="w-full text-sm text-gray-500 hover:text-gray-700"
            >
              Vaciar carrito
            </button>
            {user?.rol === 'CLIENTE' ? (
              <button
                onClick={onHacerPedido}
                className="btn-primary w-full flex items-center justify-center gap-2"
              >
                <ShoppingCart className="w-5 h-5" aria-hidden="true" />
                Comprar
              </button>
            ) : !user && (
              <div className="text-center text-sm text-gray-600 space-y-1">
                <p>Ingresá o registrate para hacer tu pedido</p>
                <div className="flex justify-center gap-3">
                  <Link
                    to="/login"
                    onClick={onClose}
                    className="text-primary-800 font-medium hover:underline"
                  >
                    Iniciar sesión
                  </Link>
                  <Link
                    to="/registro"
                    onClick={onClose}
                    className="text-primary-800 font-medium hover:underline"
                  >
                    Crear cuenta
                  </Link>
                </div>
              </div>
            )}
            <button
              onClick={contactWhatsApp}
              className="btn-whatsapp w-full"
            >
              <MessageCircle className="w-5 h-5" aria-hidden="true" />
              Consultar por WhatsApp
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

const PublicLayout = ({ children }) => {
  const [cartOpen, setCartOpen] = useState(false);
  const [pedidoOpen, setPedidoOpen] = useState(false);
  const [fotoOpen, setFotoOpen] = useState(false);
  const { itemCount } = useCart();
  const { getWhatsAppUrl, whatsappNumber } = useCurrency();
  const { user, logout, homePorRol, isCliente, actualizarFoto } = useAuth();

  const handleLogout = () => {
    logout();
  };

  const abrirPedido = () => {
    setCartOpen(false);
    setPedidoOpen(true);
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary-800 rounded-lg flex items-center justify-center">
                <Package className="w-6 h-6 text-white" aria-hidden="true" />
              </div>
              <div>
                <h1 className="text-lg font-bold text-gray-900">Repuestos Gastronómicos</h1>
                <p className="text-xs text-gray-500">Repuestos para gastronomía pesada</p>
              </div>
            </div>

            <div className="flex items-center gap-4">
              {user?.rol === 'CLIENTE' && (
                <Link
                  to="/mis-pedidos"
                  className="flex items-center gap-2 text-sm text-gray-600 hover:text-primary-800"
                >
                  <ClipboardList className="w-4 h-4" aria-hidden="true" />
                  <span className="hidden sm:inline">Mis pedidos</span>
                </Link>
              )}
              <a
                href={getWhatsAppUrl()}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-green-600 hover:text-green-700"
              >
                <Phone className="w-4 h-4" aria-hidden="true" />
                <span className="hidden sm:inline">WhatsApp</span>
              </a>
              <button
                onClick={() => setCartOpen(true)}
                className="relative p-2 text-gray-600 hover:text-primary-800 hover:bg-gray-100 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                aria-label={`Abrir carrito${itemCount > 0 ? `, ${itemCount} productos` : ''}`}
              >
                <ShoppingCart className="w-5 h-5" aria-hidden="true" />
                {itemCount > 0 && (
                  <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold">
                    {itemCount}
                  </span>
                )}
              </button>
              {user ? (
                <>
                  <button
                    type="button"
                    onClick={() => setFotoOpen(true)}
                    className="rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                    title="Cambiar foto de perfil"
                    aria-label="Cambiar foto de perfil"
                  >
                    <Avatar fotoUrl={user.fotoUrl} nombre={user.nombre} size="sm" />
                  </button>
                  <Link
                    to={homePorRol()}
                    className="hidden sm:flex items-center gap-2 text-sm text-gray-600 hover:text-primary-800"
                  >
                    {user.nombre}
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="flex items-center gap-2 text-sm text-gray-600 hover:text-primary-800"
                    title="Cerrar sesión"
                  >
                    <LogOut className="w-4 h-4" aria-hidden="true" />
                    <span className="hidden sm:inline">Salir</span>
                  </button>
                </>
              ) : (
                <>
                  <Link
                    to="/registro"
                    className="text-sm text-gray-600 hover:text-primary-800"
                  >
                    Crear cuenta
                  </Link>
                  <Link
                    to="/login"
                    className="text-sm text-gray-600 hover:text-primary-800"
                  >
                    Iniciar Sesión
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 flex-1 w-full">
        <PageTransition>{children}</PageTransition>
      </main>

      <footer className="bg-white border-t border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-sm text-gray-500">
              © {new Date().getFullYear()} Repuestos Gastronómicos. Todos los derechos reservados.
            </p>
            <div className="flex items-center gap-4 text-sm text-gray-500">
              <span>Once, Buenos Aires</span>
              <span aria-hidden="true">•</span>
              <a href={`tel:+${normalizePhone(whatsappNumber)}`} className="hover:text-primary-800">
                +{normalizePhone(whatsappNumber)}
              </a>
            </div>
          </div>
        </div>
      </footer>

      <CartDrawer
        isOpen={cartOpen}
        onClose={() => setCartOpen(false)}
        onHacerPedido={abrirPedido}
      />
      <PedidoModal open={pedidoOpen} onClose={() => setPedidoOpen(false)} />

      <SelectorFoto
        open={fotoOpen}
        onClose={() => setFotoOpen(false)}
        nombre={user?.nombre}
        fotoUrl={user?.fotoUrl}
        endpoint="/api/auth/foto"
        compartida={isCliente()}
        onActualizada={actualizarFoto}
      />
    </div>
  );
};

export default PublicLayout;
