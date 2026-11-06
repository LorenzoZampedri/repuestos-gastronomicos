import React, { useEffect, useState } from 'react';
import { KeyRound, X } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import Modal from './Modal';

const CambiarPasswordModal = ({ open, onClose }) => {
  const [passwordActual, setPasswordActual] = useState('');
  const [passwordNueva, setPasswordNueva] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!open) {
      setPasswordActual('');
      setPasswordNueva('');
      setConfirmar('');
      setGuardando(false);
    }
  }, [open]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (passwordNueva.length < 8) {
      toast.error('La contraseña nueva debe tener al menos 8 caracteres');
      return;
    }
    if (passwordNueva !== confirmar) {
      toast.error('Las contraseñas nuevas no coinciden');
      return;
    }

    setGuardando(true);
    try {
      await api.put('/api/auth/password', { passwordActual, passwordNueva });
      toast.success('Contraseña actualizada correctamente');
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo cambiar la contraseña');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} labelledBy="cambiar-password-titulo">
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 id="cambiar-password-titulo" className="text-lg font-semibold flex items-center gap-2">
            <KeyRound className="w-5 h-5 text-primary-800" aria-hidden="true" />
            Cambiar contraseña
          </h3>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="Cerrar"
          >
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="password-actual" className="label">Contraseña actual</label>
            <input
              id="password-actual"
              type="password"
              value={passwordActual}
              onChange={(e) => setPasswordActual(e.target.value)}
              className="input"
              autoComplete="current-password"
              required
            />
          </div>

          <div>
            <label htmlFor="password-nueva" className="label">Contraseña nueva</label>
            <input
              id="password-nueva"
              type="password"
              value={passwordNueva}
              onChange={(e) => setPasswordNueva(e.target.value)}
              className="input"
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              required
            />
          </div>

          <div>
            <label htmlFor="password-confirmar" className="label">Repetir contraseña nueva</label>
            <input
              id="password-confirmar"
              type="password"
              value={confirmar}
              onChange={(e) => setConfirmar(e.target.value)}
              className="input"
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              required
            />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" disabled={guardando} className="btn-primary">
              {guardando ? 'Guardando...' : 'Actualizar'}
            </button>
          </div>
        </form>
      </div>
    </Modal>
  );
};

export default CambiarPasswordModal;
