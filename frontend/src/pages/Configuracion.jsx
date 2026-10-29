import React, { useState, useEffect } from 'react';
import { Save, DollarSign, MessageCircle, History, ChevronDown, ChevronUp } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const Configuracion = () => {
  const [cotizacion, setCotizacion] = useState('');
  const [whatsapp, setWhatsapp] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [historialAbierto, setHistorialAbierto] = useState(false);
  const [historial, setHistorial] = useState([]);
  const [historialLoading, setHistorialLoading] = useState(false);

  useEffect(() => {
    fetchConfiguracion();
  }, []);

  const fetchHistorial = async () => {
    setHistorialLoading(true);
    try {
      const response = await api.get('/api/configuracion/historial', {
        params: { clave: 'cotizacion_usd_ars' },
      });
      setHistorial(response.data);
    } catch (error) {
      toast.error('Error al cargar el historial');
    } finally {
      setHistorialLoading(false);
    }
  };

  const toggleHistorial = () => {
    const abierto = !historialAbierto;
    setHistorialAbierto(abierto);
    if (abierto) fetchHistorial();
  };

  const fetchConfiguracion = async () => {
    try {
      const response = await api.get('/api/configuracion');
      const configs = response.data;
      const cot = configs.find(c => c.clave === 'cotizacion_usd_ars');
      const wa = configs.find(c => c.clave === 'whatsapp_number');
      setCotizacion(cot?.valor || '');
      setWhatsapp(wa?.valor || '');
    } catch (error) {
      toast.error('Error al cargar la configuración');
    } finally {
      setLoading(false);
    }
  };

  const guardarCotizacion = async (e) => {
    e.preventDefault();
    const valor = parseFloat(cotizacion);
    if (!valor || valor <= 0) {
      toast.error('Ingresá una cotización válida mayor a 0');
      return;
    }
    setSaving(true);
    try {
      await api.put('/api/configuracion/cotizacion_usd_ars', { valor: String(valor) });
      toast.success('Cotización actualizada. Los precios en pesos se recalculan al guardar productos.');
      if (historialAbierto) fetchHistorial();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al guardar la cotización');
    } finally {
      setSaving(false);
    }
  };

  const guardarWhatsapp = async (e) => {
    e.preventDefault();
    if (!whatsapp.trim()) {
      toast.error('Ingresá un número de WhatsApp');
      return;
    }
    setSaving(true);
    try {
      await api.put('/api/configuracion/whatsapp_number', { valor: whatsapp.trim() });
      toast.success('Número de WhatsApp actualizado');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al guardar el número');
    } finally {
      setSaving(false);
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
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Configuración</h1>
        <p className="text-gray-500">
          Ajustes generales del sistema: cotización del dólar y datos de contacto
        </p>
      </div>

      {/* Cotización USD → ARS */}
      <form onSubmit={guardarCotizacion} className="card space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center">
            <DollarSign className="w-5 h-5 text-primary-800" />
          </div>
          <div>
            <h2 className="font-semibold text-gray-900">Cotización del dólar</h2>
            <p className="text-sm text-gray-500">
              Cuántos pesos vale 1 dólar. Se usa para convertir los precios en USD a pesos
              en el catálogo público.
            </p>
          </div>
        </div>
        <div>
          <label className="label">1 USD = ARS $</label>
          <input
            type="number"
            value={cotizacion}
            onChange={(e) => setCotizacion(e.target.value)}
            className="input"
            placeholder="Ej: 1200"
            min="0.01"
            step="0.01"
            required
          />
        </div>
        <div className="flex justify-end">
          <button type="submit" disabled={saving} className="btn-primary flex items-center gap-2">
            {saving ? (
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
            ) : (
              <Save className="w-4 h-4" />
            )}
            Guardar cotización
          </button>
        </div>

        {/* Historial de cambios (desplegable) */}
        <div className="border-t border-gray-100 pt-3">
          <button
            type="button"
            onClick={toggleHistorial}
            className="w-full flex items-center justify-between text-sm font-medium text-gray-700 hover:text-primary-800 transition-colors"
          >
            <span className="flex items-center gap-2">
              <History className="w-4 h-4" />
              Historial de cambios de cotización
              {historial.length > 0 && (
                <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded-full">
                  {historial.length}
                </span>
              )}
            </span>
            {historialAbierto ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>

          {historialAbierto && (
            <div className="mt-3">
              {historialLoading ? (
                <div className="flex items-center justify-center h-16">
                  <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-800"></div>
                </div>
              ) : historial.length === 0 ? (
                <p className="text-sm text-gray-500 text-center py-4">
                  Todavía no hay cambios registrados
                </p>
              ) : (
                <div className="overflow-x-auto border border-gray-100 rounded-lg">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-100">
                        <th className="text-left py-2 px-3 font-medium text-gray-500">Fecha</th>
                        <th className="text-right py-2 px-3 font-medium text-gray-500">Anterior</th>
                        <th className="text-right py-2 px-3 font-medium text-gray-500">Nuevo</th>
                        <th className="text-left py-2 px-3 font-medium text-gray-500">Usuario</th>
                      </tr>
                    </thead>
                    <tbody>
                      {historial.map((item) => (
                        <tr key={item.id} className="border-b border-gray-50 last:border-0">
                          <td className="py-2 px-3 text-gray-600 whitespace-nowrap">
                            {new Date(item.fecha).toLocaleString('es-AR')}
                          </td>
                          <td className="py-2 px-3 text-right font-mono text-gray-500">
                            ${Number(item.valorAnterior).toLocaleString('es-AR')}
                          </td>
                          <td className="py-2 px-3 text-right font-mono font-semibold text-primary-800">
                            ${Number(item.valorNuevo).toLocaleString('es-AR')}
                          </td>
                          <td className="py-2 px-3 text-gray-600">{item.usuario || '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </form>

      {/* WhatsApp */}
      <form onSubmit={guardarWhatsapp} className="card space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
            <MessageCircle className="w-5 h-5 text-green-700" />
          </div>
          <div>
            <h2 className="font-semibold text-gray-900">Número de WhatsApp</h2>
            <p className="text-sm text-gray-500">
              Número al que se dirigén las consultas desde el catálogo público
            </p>
          </div>
        </div>
        <div>
          <label className="label">Número con código de país</label>
          <input
            type="text"
            value={whatsapp}
            onChange={(e) => setWhatsapp(e.target.value)}
            className="input"
            placeholder="Ej: +5491112345678"
            required
          />
        </div>
        <div className="flex justify-end">
          <button type="submit" disabled={saving} className="btn-primary flex items-center gap-2">
            {saving ? (
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
            ) : (
              <Save className="w-4 h-4" />
            )}
            Guardar número
          </button>
        </div>
      </form>
    </div>
  );
};

export default Configuracion;
