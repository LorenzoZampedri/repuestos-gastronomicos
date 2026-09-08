import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Save, ArrowLeft } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const ClienteForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditing = !!id;
  
  const [formData, setFormData] = useState({
    nombre: '',
    telefono: '',
    email: '',
    direccion: '',
    cuit: '',
    condicionIva: 'CONSUMIDOR_FINAL',
    observaciones: ''
  });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (isEditing) {
      fetchCliente();
    }
  }, [id]);

  const fetchCliente = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/clientes/${id}`);
      setFormData(response.data);
    } catch (error) {
      toast.error('Error al cargar cliente');
      navigate('/clientes');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    
    try {
      if (isEditing) {
        await api.put(`/api/clientes/${id}`, formData);
        toast.success('Cliente actualizado');
      } else {
        await api.post('/api/clientes', formData);
        toast.success('Cliente creado');
      }
      navigate('/clientes');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al guardar');
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
    <div className="max-w-2xl mx-auto">
      {/* Header */}
      <div className="flex items-center gap-4 mb-6">
        <button
          onClick={() => navigate('/clientes')}
          className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {isEditing ? 'Editar Cliente' : 'Nuevo Cliente'}
          </h1>
          <p className="text-gray-500">
            {isEditing ? 'Modificá los datos del cliente' : 'Agregá un nuevo cliente'}
          </p>
        </div>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="card space-y-6">
        {/* Nombre */}
        <div>
          <label className="label">Nombre / Razón Social *</label>
          <input
            type="text"
            name="nombre"
            value={formData.nombre}
            onChange={handleChange}
            className="input"
            placeholder="Ej: Juan Pérez / Pizzería El Horno"
            required
          />
        </div>

        {/* CUIT y Condición IVA */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="label">CUIT</label>
            <input
              type="text"
              name="cuit"
              value={formData.cuit}
              onChange={handleChange}
              className="input"
              placeholder="XX-XXXXXXXX-X"
            />
          </div>
          <div>
            <label className="label">Condición IVA</label>
            <select
              name="condicionIva"
              value={formData.condicionIva}
              onChange={handleChange}
              className="input"
            >
              <option value="CONSUMIDOR_FINAL">Consumidor Final</option>
              <option value="RESPONSABLE_INSCRIPTO">Responsable Inscripto</option>
              <option value="MONOTRIBUTUTO">Monotributo</option>
              <option value="EXENTO">Exento</option>
            </select>
          </div>
        </div>

        {/* Contacto */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="label">Teléfono</label>
            <input
              type="tel"
              name="telefono"
              value={formData.telefono}
              onChange={handleChange}
              className="input"
              placeholder="+54 11 1234-5678"
            />
          </div>
          <div>
            <label className="label">Email</label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              className="input"
              placeholder="cliente@ejemplo.com"
            />
          </div>
        </div>

        {/* Dirección */}
        <div>
          <label className="label">Dirección</label>
          <input
            type="text"
            name="direccion"
            value={formData.direccion}
            onChange={handleChange}
            className="input"
            placeholder="Av. Corrientes 1234, Buenos Aires"
          />
        </div>

        {/* Observaciones */}
        <div>
          <label className="label">Observaciones</label>
          <textarea
            name="observaciones"
            value={formData.observaciones}
            onChange={handleChange}
            className="input min-h-[80px]"
            placeholder="Notas adicionales sobre el cliente..."
          />
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-4 pt-4 border-t border-gray-200">
          <button
            type="button"
            onClick={() => navigate('/clientes')}
            className="btn-secondary"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={saving}
            className="btn-primary flex items-center gap-2"
          >
            {saving ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                Guardando...
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                {isEditing ? 'Actualizar' : 'Crear Cliente'}
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ClienteForm;
