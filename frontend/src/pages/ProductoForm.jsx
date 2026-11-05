import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Save, ArrowLeft, Upload } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const MENSAJE_IMAGEN_URL_INVALIDA =
  'Ingresá una URL completa (https://...) o una ruta local que empiece con / (ej: /images/archivo.jpg)';
const MENSAJE_FORMATO_INVALIDO = 'Formato no permitido. Usá JPG, PNG o WebP';
const MENSAJE_IMAGEN_GRANDE = 'La imagen supera el máximo de 5 MB';
const MAX_IMAGEN_BYTES = 5 * 1024 * 1024;
const TIPOS_IMAGEN_PERMITIDOS = ['image/jpeg', 'image/png', 'image/webp'];

const esImagenUrlValida = (valor) => {
  const v = (valor || '').trim();
  if (v === '') return true;
  if (/^https?:\/\/\S+$/i.test(v)) return true;
  if (/^\/(?!\/)\S*$/.test(v)) return true;
  return false;
};

const ProductoForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditing = !!id;
  
  const [formData, setFormData] = useState({
    nombre: '',
    descripcion: '',
    codigoBarras: '',
    precioUsd: '',
    stockActual: '',
    stockMinimo: '5',
    categoriaId: '',
    imagenUrl: ''
  });
  const [categorias, setCategorias] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [imagenError, setImagenError] = useState('');
  const [archivo, setArchivo] = useState(null);
  const [archivoError, setArchivoError] = useState('');
  const [previewUrl, setPreviewUrl] = useState('');
  const imagenInputRef = useRef(null);
  const archivoInputRef = useRef(null);
  const previewObjectUrlRef = useRef(null);

  useEffect(() => {
    fetchCategorias();
    if (isEditing) {
      fetchProducto();
    }
  }, [id]);

  useEffect(() => {
    return () => {
      if (previewObjectUrlRef.current) {
        URL.revokeObjectURL(previewObjectUrlRef.current);
      }
    };
  }, []);

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
        precioUsd: producto.precioUsd || '',
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
    if (name === 'imagenUrl') setImagenError('');
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const limpiarPreviewObjeto = () => {
    if (previewObjectUrlRef.current) {
      URL.revokeObjectURL(previewObjectUrlRef.current);
      previewObjectUrlRef.current = null;
    }
    setPreviewUrl('');
  };

  const handleArchivoChange = (e) => {
    const file = e.target.files?.[0] || null;
    if (!file) return;

    if (!TIPOS_IMAGEN_PERMITIDOS.includes(file.type)) {
      setArchivoError(MENSAJE_FORMATO_INVALIDO);
      setArchivo(null);
      limpiarPreviewObjeto();
      e.target.value = '';
      return;
    }

    if (file.size > MAX_IMAGEN_BYTES) {
      setArchivoError(MENSAJE_IMAGEN_GRANDE);
      setArchivo(null);
      limpiarPreviewObjeto();
      e.target.value = '';
      return;
    }

    setArchivoError('');
    setImagenError('');
    limpiarPreviewObjeto();
    const objectUrl = URL.createObjectURL(file);
    previewObjectUrlRef.current = objectUrl;
    setPreviewUrl(objectUrl);
    setArchivo(file);
    e.target.value = '';
  };

  const handleQuitarArchivo = () => {
    limpiarPreviewObjeto();
    setArchivo(null);
    setArchivoError('');
    if (archivoInputRef.current) archivoInputRef.current.value = '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    const imagenUrl = formData.imagenUrl.trim();
    if (!archivo && !esImagenUrlValida(imagenUrl)) {
      setImagenError(MENSAJE_IMAGEN_URL_INVALIDA);
      toast.error(MENSAJE_IMAGEN_URL_INVALIDA);
      if (imagenInputRef.current) imagenInputRef.current.focus();
      return;
    }
    setImagenError('');

    setSaving(true);
    
    try {
      let imagenUrlFinal = imagenUrl;

      if (archivo) {
        try {
          const fd = new FormData();
          fd.append('file', archivo);
          const respuesta = await api.post('/api/productos/imagen', fd, {
            headers: { 'Content-Type': 'multipart/form-data' }
          });
          imagenUrlFinal = respuesta.data.url;
          setFormData(prev => ({ ...prev, imagenUrl: imagenUrlFinal }));
          limpiarPreviewObjeto();
          setArchivo(null);
          if (archivoInputRef.current) archivoInputRef.current.value = '';
        } catch (error) {
          const status = error.response?.status;
          const message = error.response?.data?.message
            || (status === 413 ? MENSAJE_IMAGEN_GRANDE : 'No se pudo subir la imagen');
          toast.error(message);
          setSaving(false);
          return;
        }
      }

      const payload = {
        ...formData,
        codigoBarras: formData.codigoBarras.trim() || null,
        imagenUrl: imagenUrlFinal,
        precioUsd: parseFloat(formData.precioUsd) || 0,
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
          <label htmlFor="producto-nombre" className="label">Nombre del producto *</label>
          <input
            id="producto-nombre"
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
            <label htmlFor="producto-codigo" className="label">Código de barras</label>
            <input
              id="producto-codigo"
              type="text"
              name="codigoBarras"
              value={formData.codigoBarras}
              onChange={handleChange}
              className="input"
              placeholder="Opcional"
              maxLength={50}
            />
          </div>
          <div>
            <label htmlFor="producto-categoria" className="label">Categoría</label>
            <select
              id="producto-categoria"
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
          <label htmlFor="producto-descripcion" className="label">Descripción</label>
          <textarea
            id="producto-descripcion"
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
            <label htmlFor="producto-precio" className="label">Precio (USD) *</label>
            <input
              id="producto-precio"
              type="number"
              name="precioUsd"
              value={formData.precioUsd}
              onChange={handleChange}
              className="input"
              placeholder="0"
              min="0"
              step="0.01"
              required
            />
            <p className="text-xs text-gray-500 mt-1">
              El precio en pesos se calcula automáticamente con la cotización configurada
            </p>
          </div>
          <div>
            <label htmlFor="producto-stock" className="label">Stock actual *</label>
            <input
              id="producto-stock"
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
            <label htmlFor="producto-stock-minimo" className="label">Stock mínimo</label>
            <input
              id="producto-stock-minimo"
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

        {/* Imagen: subir archivo o usar URL */}
        <div>
          <label className="label">Imagen del producto</label>
          <input
            type="file"
            ref={archivoInputRef}
            onChange={handleArchivoChange}
            accept="image/jpeg,image/png,image/webp"
            hidden
          />
          {!archivo ? (
            <button
              type="button"
              onClick={() => archivoInputRef.current?.click()}
              className="btn-secondary flex items-center gap-2"
            >
              <Upload className="w-4 h-4" />
              Subir imagen desde tu PC
            </button>
          ) : (
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-700">
                {archivo.name} ({Math.round(archivo.size / 1024)} KB)
              </span>
              <button
                type="button"
                onClick={handleQuitarArchivo}
                className="text-sm text-red-500 hover:text-red-600"
              >
                Quitar
              </button>
            </div>
          )}
          <p className="text-xs text-gray-500 mt-1">JPG, PNG o WebP · máx. 5 MB</p>
          {archivoError && (
            <p className="text-xs text-red-500 mt-1">{archivoError}</p>
          )}
        </div>

        <div className="flex items-center gap-3">
          <div className="h-px flex-1 bg-gray-200"></div>
          <span className="text-xs text-gray-400">o</span>
          <div className="h-px flex-1 bg-gray-200"></div>
        </div>

        {/* URL de imagen */}
        <div>
          <label htmlFor="producto-imagen" className="label">URL de imagen (opcional)</label>
          <input
            id="producto-imagen"
            type="text"
            inputMode="url"
            name="imagenUrl"
            value={formData.imagenUrl}
            onChange={handleChange}
            ref={imagenInputRef}
            className="input disabled:bg-gray-100 disabled:text-gray-500"
            placeholder="https://ejemplo.com/imagen.jpg o /images/archivo.jpg"
            disabled={!!archivo}
          />
          {imagenError && (
            <p className="text-xs text-red-500 mt-1">{imagenError}</p>
          )}
          {archivo ? (
            <p className="text-xs text-gray-500 mt-1">Se usará el archivo seleccionado</p>
          ) : (
            <p className="text-xs text-gray-500 mt-1">
              URL completa (https://...) o ruta local (/images/archivo.jpg)
            </p>
          )}
        </div>

        {/* Preview de imagen */}
        {(archivo ? previewUrl : formData.imagenUrl) && (
          <div className="border border-gray-200 rounded-lg p-4">
            <p className="text-sm text-gray-500 mb-2">Vista previa:</p>
            <img 
              src={archivo ? previewUrl : formData.imagenUrl}
              alt="Preview" 
              className="w-32 h-32 object-contain rounded-lg"
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
