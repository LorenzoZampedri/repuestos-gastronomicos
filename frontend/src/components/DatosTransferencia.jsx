import React, { useState } from 'react';
import { Copy } from 'lucide-react';
import toast from 'react-hot-toast';
import { useCurrency } from '../context/CurrencyContext';

const DatosTransferencia = ({ className = '' }) => {
  const { datosTransferencia } = useCurrency();
  const [copiado, setCopiado] = useState(null);

  const cvu = datosTransferencia?.cvu;
  const alias = datosTransferencia?.alias;
  const titular = datosTransferencia?.titular;
  const hayDatos = Boolean(cvu || alias || titular);

  const copiar = async (valor, campo) => {
    if (!valor) return;
    if (!navigator.clipboard?.writeText) {
      toast.error('El portapapeles no está disponible en este navegador');
      return;
    }
    try {
      await navigator.clipboard.writeText(valor);
      setCopiado(campo);
      setTimeout(() => setCopiado(null), 2000);
      toast.success(`${campo === 'cvu' ? 'CVU' : 'Alias'} copiado`);
    } catch (error) {
      toast.error('No se pudo copiar. Copialo manualmente.');
    }
  };

  if (!hayDatos) {
    return (
      <div className={`rounded-lg border border-gray-200 bg-gray-50 p-4 ${className}`}>
        <p className="text-sm font-semibold text-gray-900">MP · Mercado Pago</p>
        <p className="text-sm text-gray-600 mt-1">
          El local te enviará los datos para transferir.
        </p>
      </div>
    );
  }

  return (
    <div className={`rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-3 ${className}`}>
      <p className="text-sm font-semibold text-gray-900">MP · Mercado Pago</p>

      <div className="space-y-2 text-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-gray-500">CVU</p>
            <p className="font-mono text-gray-900 break-all">{cvu || '—'}</p>
          </div>
          {cvu && (
            <button
              type="button"
              onClick={() => copiar(cvu, 'cvu')}
              className="btn-secondary flex items-center gap-1 text-xs whitespace-nowrap"
              aria-label="Copiar CVU"
            >
              <Copy className="w-3.5 h-3.5" aria-hidden="true" />
              {copiado === 'cvu' ? 'Copiado' : 'Copiar'}
            </button>
          )}
        </div>

        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-gray-500">Alias</p>
            <p className="font-mono text-gray-900 break-all">{alias || '—'}</p>
          </div>
          {alias && (
            <button
              type="button"
              onClick={() => copiar(alias, 'alias')}
              className="btn-secondary flex items-center gap-1 text-xs whitespace-nowrap"
              aria-label="Copiar alias"
            >
              <Copy className="w-3.5 h-3.5" aria-hidden="true" />
              {copiado === 'alias' ? 'Copiado' : 'Copiar'}
            </button>
          )}
        </div>

        <div>
          <p className="text-xs uppercase tracking-wide text-gray-500">Titular de la cuenta</p>
          <p className="text-gray-900">{titular || '—'}</p>
        </div>
      </div>
    </div>
  );
};

export default DatosTransferencia;
