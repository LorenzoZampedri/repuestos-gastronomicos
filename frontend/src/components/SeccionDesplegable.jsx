import React, { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';

const SeccionDesplegable = ({ id, titulo, icono: Icono, abiertaInicial = false, children }) => {
  const [abierto, setAbierto] = useState(abiertaInicial);
  const contenidoId = `${id}-contenido`;

  return (
    <section className="card">
      <button
        type="button"
        onClick={() => setAbierto((previo) => !previo)}
        aria-expanded={abierto}
        aria-controls={contenidoId}
        className="w-full flex items-center justify-between gap-3 text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded-lg"
      >
        <span className="flex items-center gap-3">
          {Icono && (
            <span className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center">
              <Icono className="w-5 h-5 text-primary-800" aria-hidden="true" />
            </span>
          )}
          <span className="font-semibold text-gray-900">{titulo}</span>
        </span>
        {abierto ? (
          <ChevronUp className="w-5 h-5 text-gray-500" aria-hidden="true" />
        ) : (
          <ChevronDown className="w-5 h-5 text-gray-500" aria-hidden="true" />
        )}
      </button>

      {abierto && (
        <div id={contenidoId} className="mt-4 pt-4 border-t border-gray-100">
          {children}
        </div>
      )}
    </section>
  );
};

export default SeccionDesplegable;
