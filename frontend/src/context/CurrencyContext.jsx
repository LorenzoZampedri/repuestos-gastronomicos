import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import api from '../services/api';

const CurrencyContext = createContext();

export const MONEDAS = { USD: 'USD', ARS: 'ARS' };
export const UMBRAL_DESCUENTO = 10;
export const PCT_DESCUENTO = 0.15;

export const useCurrency = () => {
  const context = useContext(CurrencyContext);
  if (!context) {
    throw new Error('useCurrency must be used within a CurrencyProvider');
  }
  return context;
};

export const CurrencyProvider = ({ children }) => {
  const [moneda, setMonedaState] = useState(() => localStorage.getItem('moneda') || MONEDAS.USD);
  const [cotizacion, setCotizacion] = useState(null);

  useEffect(() => {
    const fetchCotizacion = async () => {
      try {
        const response = await api.get('/api/configuracion/publica');
        setCotizacion(response.data.cotizacionUsdArs || null);
      } catch (error) {
        console.error('Error al cargar cotización:', error);
        setCotizacion(null);
      }
    };
    fetchCotizacion();
  }, []);

  const setMoneda = (nuevaMoneda) => {
    setMonedaState(nuevaMoneda);
    localStorage.setItem('moneda', nuevaMoneda);
  };

  const hayCotizacion = cotizacion !== null && cotizacion > 0;

  const convertir = useCallback((precioUsd) => {
    if (precioUsd == null) return null;
    if (moneda === MONEDAS.USD) return precioUsd;
    if (!hayCotizacion) return null;
    return Number(precioUsd) * Number(cotizacion);
  }, [moneda, cotizacion, hayCotizacion]);

  const formatPrice = useCallback((precioUsd) => {
    const valor = convertir(precioUsd);
    if (valor == null || valor <= 0) return 'Consultar precio';
    const formateado = valor.toLocaleString('es-AR', { maximumFractionDigits: 2 });
    return moneda === MONEDAS.USD ? `U$S ${formateado}` : `$${formateado}`;
  }, [moneda, convertir]);

  const aplicarDescuento = useCallback((precioUsd) => {
    if (precioUsd == null) return null;
    return Number(precioUsd) * (1 - PCT_DESCUENTO);
  }, []);

  const getDescuentoAplicable = (cantidad) => cantidad > UMBRAL_DESCUENTO;

  return (
    <CurrencyContext.Provider value={{
      moneda,
      setMoneda,
      cotizacion,
      hayCotizacion,
      convertir,
      formatPrice,
      aplicarDescuento,
      getDescuentoAplicable
    }}>
      {children}
    </CurrencyContext.Provider>
  );
};
