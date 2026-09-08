import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Save, ArrowLeft } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const ProductoForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditing = !!id;
  
  const [formData, setFormData] = useState({
    nombre: '',
    descripcion: '',
    codigoBarras: '',
    precio: '',
    stockActual: '',
    stockMinimo: '5',
    categoriaId: '',
    imagenUrl: ''
  });
  const [categorias, setCategorias] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchCategorias();
    if (isEditing) {
      fetchProducto();
    }
  }, [id]);

  const fetchCategorias = async () => {
    try {
      const response = await api.get('/api/categorias');
      setCategorias(response.data);
    } catch (error) {
      console.error('Error:', error);
    }
  };

  const fetchProducto = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/productos/${id}`);
      const producto = response.data;
      setFormData({
        nombre: producto.nombre || '',
        descripcion: producto.descripcion || '',
        codigoBarras: producto.codigoBarras || '',
        precio: producto.precio || '',
        stockActual: producto.stockActual || '',
        stockMinimo: producto.stockMinimo || '5',
        categoriaId: producto.categoria?.id || '',
        imagenUrl: producto.imagenUrl || ''
      });
    } catch (error) {
      toast.error('Error al cargar producto');
      navigate('/productos');
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
      const payload = {
        ...formData,
        precio: parseFloat(formData.precio),
        stockActual: parseInt(formData.stockActual) || 0,
        stockMinimo: parseInt(formData.stockMinimo) || 5,
        categoria: formData.categoriaId ? { id: parseInt(formData.categoriaId) } : null
      };
      
      if (isEditing) {
        await api.put(`/api/productos/${id}`, payload);
        toast.success('Producto actualizado');
      } else {
        await api.post('/api/productos', payload);
        toast.success('Producto creado');
      }
      navigate('/productos');
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
          onClick={() => navigate('/productos')}
          className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {isEditing ? 'Editar Producto' : 'Nuevo Producto'}
          </h1>
          <p className="text-gray-500">
            {isEditing ? 'Modificá los datos del producto' : 'Agregá un nuevo producto al inventario'}
          </p>
        </div>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="card space-y-6">
        {/* Nombre */}
        <div>
          <label className="label">Nombre del producto *</label>
          <input
            type="text"
            name="nombre"
            value={formData.nombre}
            onChange={handleChange}
            className="input"
            placeholder="Ej: Quemador 4 bocas"
            required
          />
        </div>

        {/* Código de barras y Categoría */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="label">Código de barras</label>
            <input
              type="text"
              name="codigoBarras"
              value={formData.codigoBarras}
              onChange={handleChange}
              className="input"
              placeholder="Opcional"
            />
          </div>
          <div>
            <label className="label">Categoría</label>
            <select
              name="categoriaId"
              value={formData.categoriaId}
              onChange={handleChange}
              className="input"
            >
              <option value="">Seleccionar categoría</option>
              {categorias.map(cat => (
                <option key={cat.id} value={cat.id}>{cat.nombre}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Descripción */}
        <div>
          <label className="label">Descripción</label>
          <textarea
            name="descripcion"
            value={formData.descripcion}
            onChange={handleChange}
            className="input min-h-[100px]"
            placeholder="Descripción detallada del producto"
          />
        </div>

        {/* Precio y Stock */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label className="label">Precio (ARS) *</label>
            <input
              type="number"
              name="precio"
              value={formData.precio}
              onChange={handleChange}
              className="input"
              placeholder="0"
              min="0"
              step="0.01"
              required
            />
          </div>
          <div>
            <label className="label">Stock actual *</label>
            <input
              type="number"
              name="stockActual"
              value={formData.stockActual}
              onChange={handleChange}
              className="input"
              placeholder="0"
              min="0"
              required
            />
          </div>
          <div>
            <label className="label">Stock mínimo</label>
            <input
              type="number"
              name="stockMinimo"
              value={formData.stockMinimo}
              onChange={handleChange}
              className="input"
              placeholder="5"
              min="0"
            />
          </div>
        </div>

        {/* URL de imagen */}
        <div>
          <label className="label">URL de imagen (opcional)</label>
          <input
            type="url"
            name="imagenUrl"
            value={formData.imagenUrl}
            onChange={handleChange}
            className="input"
            placeholder="https://ejemplo.com/imagen.jpg"
          />
        </div>

        {/* Preview de imagen */}
        {formData.imagenUrl && (
          <div className="border border-gray-200 rounded-lg p-4">
            <p className="text-sm text-gray-500 mb-2">Vista previa:</p>
            <img 
              src={formData.imagenUrl} 
              alt="Preview" 
              className="w-32 h-32 object-cover rounded-lg"
              onError={(e) => e.target.style.display = 'none'}
            />
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-4 pt-4 border-t border-gray-200">
          <button
            type="button"
            onClick={() => navigate('/productos')}
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
                {isEditing ? 'Actualizar' : 'Crear Producto'}
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ProductoForm;
