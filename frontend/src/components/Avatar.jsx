import React, { useEffect, useState } from 'react';
import { User, Users } from 'lucide-react';
import api from '../services/api';
import { getAvatarCacheado, guardarAvatarCacheado } from '../lib/avatarCache';

const TAMANOS = {
  sm: 'w-8 h-8 text-xs',
  md: 'w-10 h-10 text-sm',
  lg: 'w-12 h-12 text-base',
  xl: 'w-20 h-20 text-xl',
};

const ICONOS = {
  sm: 'w-4 h-4',
  md: 'w-5 h-5',
  lg: 'w-6 h-6',
  xl: 'w-8 h-8',
};

const iniciales = (nombre) => {
  const partes = (nombre || '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '';
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
  return (partes[0][0] + partes[1][0]).toUpperCase();
};

const useFotoPerfil = (fotoUrl) => {
  const [src, setSrc] = useState(() => (fotoUrl ? getAvatarCacheado(fotoUrl) || null : null));

  useEffect(() => {
    if (!fotoUrl) {
      setSrc(null);
      return undefined;
    }

    const cacheado = getAvatarCacheado(fotoUrl);
    if (cacheado) {
      setSrc(cacheado);
      return undefined;
    }

    let activo = true;
    setSrc(null);

    api.get(fotoUrl, { responseType: 'blob' })
      .then(({ data }) => {
        const objectUrl = URL.createObjectURL(data);
        guardarAvatarCacheado(fotoUrl, objectUrl);
        if (activo) setSrc(objectUrl);
      })
      .catch(() => {
        if (activo) setSrc(null);
      });

    return () => {
      activo = false;
    };
  }, [fotoUrl]);

  return src;
};

const Avatar = ({ fotoUrl, nombre, size = 'md', tipo = 'usuario', className = '' }) => {
  const src = useFotoPerfil(fotoUrl);
  const Icono = tipo === 'cliente' ? Users : User;
  const texto = iniciales(nombre);

  return (
    <div
      className={`${TAMANOS[size] || TAMANOS.md} bg-primary-100 rounded-full overflow-hidden flex items-center justify-center flex-shrink-0 ${className}`}
    >
      {src ? (
        <img
          src={src}
          alt={nombre ? `Foto de ${nombre}` : 'Foto de perfil'}
          className="w-full h-full object-cover"
        />
      ) : texto ? (
        <span className="font-semibold text-primary-800" aria-hidden="true">{texto}</span>
      ) : (
        <Icono className={`${ICONOS[size] || ICONOS.md} text-primary-800`} aria-hidden="true" />
      )}
    </div>
  );
};

export default Avatar;
