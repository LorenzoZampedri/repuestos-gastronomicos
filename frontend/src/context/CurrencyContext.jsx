import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import api from '../services/api';
import { formatMoney, whatsappUrl } from '../utils/format';

const CurrencyContext = createContext();

export const MONEDAS = { USD: 'USD', ARS: 'ARS' };
export const UMBRAL_DESCUENTO = 10;
export const PCT_DESCUENTO = 0.15;
export const WHATSAPP_DEFAULT = '5491112345678';

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
  const [whatsappNumber, setWhatsappNumber] = useState(WHATSAPP_DEFAULT);
  const [datosTransferencia, setDatosTransferencia] = useState(null);

  const recargarConfiguracion = useCallback(async () => {
    try {
      const response = await api.get('/api/configuracion/publica');
      setCotizacion(response.data.cotizacionUsdArs || null);
      if (response.data.whatsappNumber) {
        setWhatsappNumber(response.data.whatsappNumber);
      }
      setDatosTransferencia(response.data.datosTransferencia || null);
    } catch (error) {
      console.error('Error al cargar configuración:', error);
      setCotizacion(null);
    }
  }, []);

  useEffect(() => {
    recargarConfiguracion();
  }, [recargarConfiguracion]);

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
    return formatMoney(valor, moneda);
  }, [moneda, convertir]);

  const getWhatsAppUrl = useCallback(
    (message) => whatsappUrl(whatsappNumber, message),
    [whatsappNumber]
  );

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
      whatsappNumber,
      datosTransferencia,
      recargarConfiguracion,
      getWhatsAppUrl,
      convertir,
      formatPrice,
      aplicarDescuento,
      getDescuentoAplicable
    }}>
      {children}
    </CurrencyContext.Provider>
  );
};
