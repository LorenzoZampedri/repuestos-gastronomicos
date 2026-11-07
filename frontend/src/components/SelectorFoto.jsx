import React, { useEffect, useRef, useState } from 'react';
import { Camera, Trash2, Upload, X, Info } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import Modal from './Modal';
import Avatar from './Avatar';

const MAX_BYTES = 5 * 1024 * 1024;

const SelectorFoto = ({ open, onClose, nombre, fotoUrl, endpoint, compartida = false, onActualizada }) => {
  const [archivo, setArchivo] = useState(null);
  const [preview, setPreview] = useState('');
  const [subiendo, setSubiendo] = useState(false);
  const [quitando, setQuitando] = useState(false);
  const inputRef = useRef(null);

  useEffect(() => {
    if (open) return;
    setArchivo(null);
    setSubiendo(false);
    setQuitando(false);
  }, [open]);

  useEffect(() => {
    if (!archivo) {
      setPreview('');
      return undefined;
    }
    const objectUrl = URL.createObjectURL(archivo);
    setPreview(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [archivo]);

  const handleArchivo = (event) => {
    const seleccionado = event.target.files?.[0];
    event.target.value = '';
    if (!seleccionado) return;

    if (!seleccionado.type.startsWith('image/')) {
      toast.error('Seleccioná una imagen JPG o PNG');
      return;
    }
    if (seleccionado.size > MAX_BYTES) {
      toast.error('La imagen supera el máximo de 5 MB');
      return;
    }

    setArchivo(seleccionado);
  };

  const handleSubir = async () => {
    if (!archivo || !endpoint) return;

    setSubiendo(true);
    try {
      const formData = new FormData();
      formData.append('file', archivo);
      const response = await api.put(endpoint, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast.success('Foto actualizada');
      onActualizada?.(response.data?.fotoUrl || '');
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo actualizar la foto');
    } finally {
      setSubiendo(false);
    }
  };

  const handleQuitar = async () => {
    if (!endpoint) return;
    if (!window.confirm('¿Quitar la foto de perfil?')) return;

    setQuitando(true);
    try {
      const response = await api.delete(endpoint);
      toast.success('Foto eliminada');
      onActualizada?.(response.data?.fotoUrl || '');
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo quitar la foto');
    } finally {
      setQuitando(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} labelledBy="selector-foto-titulo">
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 id="selector-foto-titulo" className="text-lg font-semibold flex items-center gap-2">
            <Camera className="w-5 h-5 text-primary-800" aria-hidden="true" />
            Foto de perfil
          </h3>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="Cerrar"
          >
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        <div className="flex flex-col items-center gap-3">
          {preview ? (
            <img
              src={preview}
              alt="Vista previa de la nueva foto"
              className="w-28 h-28 rounded-full object-cover border border-gray-200"
            />
          ) : (
            <Avatar fotoUrl={fotoUrl} nombre={nombre} size="xl" />
          )}
          <p className="text-sm font-medium text-gray-900 text-center">{nombre}</p>
        </div>

        {compartida && (
          <p className="mt-4 flex items-start gap-2 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
            <Info className="w-4 h-4 mt-0.5 flex-shrink-0" aria-hidden="true" />
            Si la cuenta está vinculada a una ficha de cliente, esta foto se comparte con ella.
          </p>
        )}

        <p className="mt-4 text-xs text-gray-500">
          Formatos JPG o PNG de hasta 5 MB. La imagen se recorta al centro y se guarda en 512×512.
        </p>

        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png"
          onChange={handleArchivo}
          className="hidden"
        />

        <div className="mt-4 flex flex-col gap-2">
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={subiendo || quitando}
            className="btn-secondary flex items-center justify-center gap-2"
          >
            <Upload className="w-4 h-4" aria-hidden="true" />
            {archivo ? 'Elegir otra imagen' : 'Elegir imagen'}
          </button>
          {archivo && (
            <button
              type="button"
              onClick={handleSubir}
              disabled={subiendo}
              className="btn-primary flex items-center justify-center gap-2"
            >
              <Camera className="w-4 h-4" aria-hidden="true" />
              {subiendo ? 'Guardando...' : 'Guardar foto'}
            </button>
          )}
          {fotoUrl && !archivo && (
            <button
              type="button"
              onClick={handleQuitar}
              disabled={quitando}
              className="flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-red-600 rounded-lg hover:bg-red-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:opacity-50"
            >
              <Trash2 className="w-4 h-4" aria-hidden="true" />
              {quitando ? 'Quitando...' : 'Quitar foto'}
            </button>
          )}
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancelar
          </button>
        </div>
      </div>
    </Modal>
  );
};

export default SelectorFoto;
