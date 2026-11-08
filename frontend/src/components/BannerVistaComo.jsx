import React, { useEffect } from 'react';
import { Eye, LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const ROL_LABELS = {
  ADMIN: 'Administrador',
  EMPLEADO: 'Empleado',
  CLIENTE: 'Cliente',
};

const BannerVistaComo = () => {
  const { vista, enVista, salirDeVista } = useAuth();

  useEffect(() => {
    if (!enVista) return undefined;
    document.documentElement.style.setProperty('--banner-h', '3rem');
    return () => document.documentElement.style.setProperty('--banner-h', '0px');
  }, [enVista]);

  if (!enVista) return null;

  return (
    <div className="fixed top-0 left-0 right-0 z-[60] h-12 bg-amber-500 text-white shadow-md">
      <div className="h-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Eye className="w-5 h-5 shrink-0" aria-hidden="true" />
          <p className="text-sm font-medium truncate">
            Viendo como <span className="font-bold">{vista?.nombre}</span>
            {' '}({ROL_LABELS[vista?.rol] || vista?.rol}) — solo lectura
          </p>
        </div>
        <button
          type="button"
          onClick={salirDeVista}
          className="shrink-0 inline-flex items-center gap-2 rounded-lg bg-white/15 hover:bg-white/25 px-3 py-1.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-white"
        >
          <LogOut className="w-4 h-4" aria-hidden="true" />
          <span className="hidden sm:inline">Salir de la vista</span>
          <span className="sm:hidden">Salir</span>
        </button>
      </div>
    </div>
  );
};

export default BannerVistaComo;
