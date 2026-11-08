import React, { useEffect, useId, useRef, useState } from 'react';
import { ChevronDown, Search, X } from 'lucide-react';
import { normalizarTexto } from '../utils/ventasFiltros';

const FiltroDesplegable = ({
  etiqueta,
  icono: Icono,
  titulo,
  ayuda,
  opciones,
  seleccionados,
  onChange,
  abierto,
  onToggle,
  textoVacio,
  aviso,
}) => {
  const contenedorRef = useRef(null);
  const botonRef = useRef(null);
  const buscadorRef = useRef(null);
  const panelId = useId();
  const [busqueda, setBusqueda] = useState('');

  const cantidadSeleccionada = Object.keys(seleccionados || {}).length;

  useEffect(() => {
    if (!abierto) {
      setBusqueda('');
      return;
    }
    const enfocar = setTimeout(() => buscadorRef.current?.focus(), 0);
    return () => clearTimeout(enfocar);
  }, [abierto]);

  useEffect(() => {
    if (!abierto) return;

    const alHacerClic = (evento) => {
      if (contenedorRef.current && !contenedorRef.current.contains(evento.target)) {
        onToggle();
      }
    };

    const alPresionarTecla = (evento) => {
      if (evento.key === 'Escape') {
        evento.stopPropagation();
        onToggle();
        botonRef.current?.focus();
      }
    };

    document.addEventListener('mousedown', alHacerClic);
    document.addEventListener('keydown', alPresionarTecla);

    return () => {
      document.removeEventListener('mousedown', alHacerClic);
      document.removeEventListener('keydown', alPresionarTecla);
    };
  }, [abierto, onToggle]);

  const opcionesFiltradas = (opciones || []).filter((opcion) =>
    normalizarTexto(opcion.label).includes(normalizarTexto(busqueda))
  );

  const alternarOpcion = (opcion, marcada) => {
    const clave = String(opcion.id);
    const siguientes = { ...(seleccionados || {}) };
    if (marcada) {
      siguientes[clave] = opcion.label;
    } else {
      delete siguientes[clave];
    }
    onChange(siguientes);
  };

  return (
    <div ref={contenedorRef} className="relative w-full sm:w-auto">
      <button
        ref={botonRef}
        type="button"
        onClick={onToggle}
        aria-expanded={abierto}
        aria-haspopup="dialog"
        aria-controls={panelId}
        className={`inline-flex w-full sm:w-auto items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
          cantidadSeleccionada > 0
            ? 'bg-primary-800 text-white hover:bg-primary-700'
            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
        }`}
      >
        {Icono && <Icono className="w-4 h-4" aria-hidden="true" />}
        <span>{cantidadSeleccionada > 0 ? `${etiqueta} · ${cantidadSeleccionada}` : etiqueta}</span>
        <ChevronDown
          className={`w-4 h-4 transition-transform ${abierto ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>

      {abierto && (
        <div
          id={panelId}
          role="dialog"
          aria-label={titulo}
          className="absolute left-0 right-0 z-30 mt-2 sm:right-auto sm:w-80 bg-white border border-gray-200 rounded-xl shadow-lg p-4"
        >
          <p className="text-sm font-semibold text-gray-900">{titulo}</p>
          {ayuda && <p className="mt-1 text-xs text-gray-500">{ayuda}</p>}
          {aviso && <p className="mt-1 text-xs font-medium text-primary-700">{aviso}</p>}

          <div className="relative mt-3">
            <Search
              className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
              aria-hidden="true"
            />
            <input
              ref={buscadorRef}
              type="text"
              value={busqueda}
              onChange={(evento) => setBusqueda(evento.target.value)}
              placeholder={`Buscar ${etiqueta.toLowerCase()}...`}
              aria-label={`Buscar ${etiqueta.toLowerCase()}`}
              className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent placeholder:text-gray-400"
            />
          </div>

          <div className="mt-2 flex justify-end">
            <button
              type="button"
              onClick={() => onChange({})}
              disabled={cantidadSeleccionada === 0}
              className="inline-flex items-center gap-1 text-xs font-medium text-primary-800 hover:text-primary-700 disabled:text-gray-400 disabled:cursor-not-allowed"
            >
              <X className="w-3 h-3" aria-hidden="true" />
              Limpiar selección
            </button>
          </div>

          <div
            className="mt-2 max-h-64 overflow-y-auto divide-y divide-gray-100"
            role="group"
            aria-label={titulo}
          >
            {opcionesFiltradas.map((opcion) => {
              const clave = String(opcion.id);
              const marcada = Object.prototype.hasOwnProperty.call(seleccionados || {}, clave);
              const sinVentas = opcion.count === 0;
              return (
                <label
                  key={clave}
                  className="flex items-center gap-3 py-2 px-1 cursor-pointer hover:bg-gray-50 rounded-lg"
                >
                  <input
                    type="checkbox"
                    checked={marcada}
                    onChange={(evento) => alternarOpcion(opcion, evento.target.checked)}
                    className="h-4 w-4 rounded border-gray-300 text-primary-800 focus:ring-primary-500"
                  />
                  <span className={`flex-1 text-sm ${sinVentas ? 'text-gray-400' : 'text-gray-700'}`}>
                    {opcion.label}
                  </span>
                  <span className={`text-xs ${sinVentas ? 'text-gray-300' : 'text-gray-400'}`}>
                    ({opcion.count})
                  </span>
                </label>
              );
            })}

            {opcionesFiltradas.length === 0 && (
              <p className="py-4 text-center text-sm text-gray-500">
                {opciones.length === 0 ? textoVacio : 'No hay resultados para tu búsqueda.'}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default FiltroDesplegable;
