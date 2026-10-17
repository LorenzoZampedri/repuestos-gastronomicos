import React, { useState, useEffect } from 'react';
import { Save, DollarSign, MessageCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const Configuracion = () => {
  const [cotizacion, setCotizacion] = useState('');
  const [whatsapp, setWhatsapp] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchConfiguracion();
  }, []);

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
