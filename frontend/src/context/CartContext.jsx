import React, { createContext, useContext, useState, useEffect } from 'react';
import toast from 'react-hot-toast';

const CartContext = createContext();

export const useCart = () => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

export const CartProvider = ({ children }) => {
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

  const generateWhatsAppMessage = () => {
    if (items.length === 0) return '';

    let mensaje = 'Hola! Me interesan los siguientes productos:\n\n';
    items.forEach((item, index) => {
      const precio = item.precio > 0
        ? `$${item.precio.toLocaleString('es-AR')}`
        : item.precioUsd
          ? `U$S ${item.precioUsd}`
          : 'Consultar precio';
      mensaje += `${index + 1}. ${item.nombre} x${item.cantidad} - ${precio}\n`;
    });
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
      generateWhatsAppMessage
    }}>
      {children}
    </CartContext.Provider>
  );
};
