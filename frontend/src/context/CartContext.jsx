import React, { createContext, useContext, useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { useCurrency, MONEDAS, PCT_DESCUENTO, UMBRAL_DESCUENTO } from './CurrencyContext';

const CartContext = createContext();

export const useCart = () => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

export const CartProvider = ({ children }) => {
  const { moneda, cotizacion, hayCotizacion, convertir } = useCurrency();
  const [items, setItems] = useState(() => {
    const saved = localStorage.getItem('cart');
    return saved ? JSON.parse(saved) : [];
  });

  useEffect(() => {
    localStorage.setItem('cart', JSON.stringify(items));
  }, [items]);

  const addItem = (producto) => {
    setItems(prev => {
      const existing = prev.find(item => item.id === producto.id);
      if (existing) {
        toast.success(`${producto.nombre} +1 en el carrito`);
        return prev.map(item =>
          item.id === producto.id ? { ...item, cantidad: item.cantidad + 1 } : item
        );
      }
      toast.success(`${producto.nombre} agregado al carrito`);
      return [...prev, { ...producto, cantidad: 1 }];
    });
  };

  const updateQuantity = (productoId, nuevaCantidad) => {
    if (nuevaCantidad < 1) {
      removeItem(productoId);
      return;
    }
    setItems(prev =>
      prev.map(item =>
        item.id === productoId ? { ...item, cantidad: nuevaCantidad } : item
      )
    );
  };

  const removeItem = (productoId) => {
    setItems(prev => prev.filter(item => item.id !== productoId));
    toast.success('Producto eliminado del carrito');
  };

  const clearCart = () => {
    setItems([]);
    toast.success('Carrito vaciado');
  };

  const itemCount = items.length;

  const tieneDescuento = (item) => item.cantidad > UMBRAL_DESCUENTO;

  const precioUnitarioBase = (item) => {
    const usd = item.precioUsd > 0 ? item.precioUsd : null;
    if (usd == null) return null;
    return tieneDescuento(item) ? usd * (1 - PCT_DESCUENTO) : usd;
  };

  const precioUnitario = (item) => convertir(precioUnitarioBase(item));
  const precioLista = (item) => convertir(item.precioUsd > 0 ? item.precioUsd : null);

  const subtotalItem = (item) => {
    const unitario = precioUnitario(item);
    return unitario == null ? null : unitario * item.cantidad;
  };

  const cartTotal = items.reduce((sum, item) => {
    const subtotal = subtotalItem(item);
    return subtotal == null ? sum : sum + subtotal;
  }, 0);

  const hayPreciosSinConfigurar = items.some(item =>
    !(item.precioUsd > 0) || (moneda === MONEDAS.ARS && !hayCotizacion)
  );

  const generateWhatsAppMessage = () => {
    if (items.length === 0) return '';

    let mensaje = 'Hola! Me interesan los siguientes productos:\n\n';
    items.forEach((item, index) => {
      const usdLista = item.precioUsd > 0 ? item.precioUsd : null;
      if (usdLista == null) {
        mensaje += `${index + 1}. ${item.nombre} x${item.cantidad} - Consultar precio\n`;
        return;
      }
      const usdUnitario = tieneDescuento(item) ? usdLista * (1 - PCT_DESCUENTO) : usdLista;
      const monedaLabel = moneda === MONEDAS.USD ? 'U$S' : '$';
      const fmt = (v) => {
        const convertido = convertir(v);
        return convertido == null ? '-' : convertido.toLocaleString('es-AR', { maximumFractionDigits: 2 });
      };
      mensaje += `${index + 1}. ${item.nombre} x${item.cantidad} - ${monedaLabel} ${fmt(usdUnitario)} c/u`;
      if (tieneDescuento(item)) {
        mensaje += ` (precio lista ${monedaLabel} ${fmt(usdLista)}, -${Math.round(PCT_DESCUENTO * 100)}% por cantidad)`;
      }
      mensaje += '\n';
    });

    if (moneda === MONEDAS.USD && hayCotizacion) {
      mensaje += `\n(Referencia pesos: 1 U$S = $${Number(cotizacion).toLocaleString('es-AR')})\n`;
    }
    if (cartTotal > 0) {
      const totalLabel = moneda === MONEDAS.USD ? 'U$S' : '$';
      mensaje += `\nTotal aproximado: ${totalLabel} ${cartTotal.toLocaleString('es-AR', { maximumFractionDigits: 2 })}\n`;
    }
    mensaje += '\n¿Podrían darme más información?';

    return mensaje;
  };

  const contactWhatsApp = () => {
    const mensaje = generateWhatsAppMessage();
    if (!mensaje) {
      toast.error('El carrito está vacío');
      return;
    }
    window.open(`https://wa.me/5491112345678?text=${encodeURIComponent(mensaje)}`, '_blank');
  };

  return (
    <CartContext.Provider value={{
      items,
      addItem,
      removeItem,
      updateQuantity,
      clearCart,
      itemCount,
      contactWhatsApp,
      generateWhatsAppMessage,
      tieneDescuento,
      precioUnitario,
      precioLista,
      subtotalItem,
      cartTotal,
      hayPreciosSinConfigurar
    }}>
      {children}
    </CartContext.Provider>
  );
};
