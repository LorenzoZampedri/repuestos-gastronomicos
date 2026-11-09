import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, CheckCircle, Plus, Search, UserPlus } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { formatMoney } from '../utils/format';
import {
  MOTIVO_MAXIMO,
  MOTIVO_MINIMO,
  construirPayloadDeuda,
  resolverConflictoCliente,
  validarClienteNuevo,
  validarMontoDeuda,
  validarMotivoDeuda,
} from '../utils/deuda';

const NUEVO_VACIO = { nombre: '', telefono: '', email: '', cuit: '' };

const CargarDeuda = ({ clienteInicial = null, onCargada, onCancelar }) => {
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const [modo, setModo] = useState('existente');
  const [clientes, setClientes] = useState([]);
  const [cargandoClientes, setCargandoClientes] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [clienteId, setClienteId] = useState(clienteInicial ? String(clienteInicial.id) : '');
  const [nuevo, setNuevo] = useState(NUEVO_VACIO);
  const [monto, setMonto] = useState('');
  const [motivo, setMotivo] = useState('');
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [conflicto, setConflicto] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState(null);

  useEffect(() => {
    let activo = true;
    api.get('/api/clientes')
      .then(({ data }) => {
        if (activo) setClientes(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (activo) setClientes([]);
      })
      .finally(() => {
        if (activo) setCargandoClientes(false);
      });
    return () => {
      activo = false;
    };
  }, []);

  useEffect(() => {
    if (!clienteInicial) return;
    setModo('existente');
    setClienteId(String(clienteInicial.id));
  }, [clienteInicial]);

  const clientesFiltrados = useMemo(() => {
    const termino = busqueda.trim().toLowerCase();
    if (!termino) return clientes;
    return clientes.filter((cliente) => (
      cliente.nombre?.toLowerCase().includes(termino)
      || cliente.telefono?.includes(termino)
      || cliente.email?.toLowerCase().includes(termino)
    ));
  }, [clientes, busqueda]);

  if (!isAdmin()) {
    return (
      <div className="card max-w-xl">
        <div className="flex items-center gap-2 text-red-700">
          <AlertCircle className="w-5 h-5" />
          <p>Solo un administrador puede cargar deudas a cuenta corriente.</p>
        </div>
      </div>
    );
  }

  const cambiarModo = (nuevoModo) => {
    setModo(nuevoModo);
    setErrores({});
    setErrorGeneral('');
    setConflicto(null);
  };

  const usarClienteExistente = (id) => {
    setModo('existente');
    setClienteId(String(id));
    setConflicto(null);
    setErrorGeneral('');
  };

  const reiniciar = () => {
    setResultado(null);
    setMonto('');
    setMotivo('');
    setBusqueda('');
    setNuevo(NUEVO_VACIO);
    setErrores({});
    setErrorGeneral('');
    setConflicto(null);
    if (!clienteInicial) setClienteId('');
  };

  const validar = () => {
    const nuevosErrores = {};
    if (modo === 'existente' && !clienteId) {
      nuevosErrores.cliente = 'Seleccioná un cliente';
    }
    if (modo === 'nuevo') {
      Object.assign(nuevosErrores, validarClienteNuevo(nuevo));
    }

    const errorMonto = validarMontoDeuda(monto);
    if (errorMonto) nuevosErrores.monto = errorMonto;
    const errorMotivo = validarMotivoDeuda(motivo);
    if (errorMotivo) nuevosErrores.motivo = errorMotivo;

    setErrores(nuevosErrores);
    return Object.keys(nuevosErrores).length === 0;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setErrorGeneral('');
    setConflicto(null);
    if (!validar()) return;

    setEnviando(true);
    try {
      const payload = construirPayloadDeuda({
        clienteId: modo === 'existente' ? clienteId : null,
        nuevo: modo === 'nuevo' ? nuevo : null,
        monto,
        motivo,
      });
      const { data } = await api.post('/api/clientes/cargar-deuda', payload);
      setResultado(data);
      toast.success('Deuda cargada correctamente');
      onCargada?.(data);
    } catch (error) {
      const data = error.response?.data;
      const conflictoCliente = resolverConflictoCliente(error);
      if (conflictoCliente) {
        setConflicto(conflictoCliente);
      } else if (data?.code === 'DEUDA_DUPLICADA') {
        setErrorGeneral(data.message || 'Ya cargaste esta misma deuda hace instantes');
      } else {
        setErrorGeneral(data?.message || 'No se pudo cargar la deuda');
      }
    } finally {
      setEnviando(false);
    }
  };

  if (resultado) {
    return (
      <div className="card max-w-xl">
        <div className="flex items-start gap-3">
          <CheckCircle className="w-8 h-8 text-green-600 flex-shrink-0" />
          <div className="flex-1">
            <h2 className="text-lg font-semibold text-gray-900">Deuda cargada</h2>
            <p className="text-sm text-gray-500 mt-1">
              {resultado.clienteCreado
                ? 'El cliente se creó y la deuda quedó registrada en su cuenta corriente.'
                : 'La deuda quedó registrada en la cuenta corriente del cliente.'}
            </p>

            <div className="mt-4 rounded-lg bg-gray-50 p-4 space-y-2">
              <p className="font-medium text-gray-900">{resultado.cliente?.nombre}</p>
              <p className="text-sm text-gray-600">
                Monto cargado: <strong>{formatMoney(resultado.cargo?.monto, 'ARS')}</strong>
              </p>
              <p className="text-sm text-gray-600">
                Motivo: {resultado.cargo?.motivo}
              </p>
              <p className="text-sm text-gray-600">
                Saldo actual: <strong>{formatMoney(resultado.cliente?.saldoCorriente, 'ARS')}</strong>
              </p>
            </div>

            <div className="mt-5 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => navigate(`/clientes/${resultado.cliente?.id}/ficha`)}
                className="btn-primary"
              >
                Ver ficha del cliente
              </button>
              <button type="button" onClick={reiniciar} className="btn-secondary">
                Cargar otra deuda
              </button>
              {onCancelar && (
                <button type="button" onClick={onCancelar} className="btn-secondary">
                  Volver
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="card max-w-xl space-y-5" noValidate>
      <div>
        <h2 className="text-lg font-semibold text-gray-900">Cargar deuda a cliente</h2>
        <p className="text-sm text-gray-500">
          La deuda queda registrada de forma inalterable en la cuenta corriente.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-2" role="group" aria-label="Tipo de cliente">
        <button
          type="button"
          onClick={() => cambiarModo('existente')}
          className={`px-3 py-2 rounded-lg border text-sm font-medium ${
            modo === 'existente'
              ? 'border-primary-500 bg-primary-50 text-primary-800'
              : 'border-gray-200 text-gray-600 hover:bg-gray-50'
          }`}
          aria-pressed={modo === 'existente'}
        >
          Cliente existente
        </button>
        <button
          type="button"
          onClick={() => cambiarModo('nuevo')}
          className={`px-3 py-2 rounded-lg border text-sm font-medium flex items-center justify-center gap-1 ${
            modo === 'nuevo'
              ? 'border-primary-500 bg-primary-50 text-primary-800'
              : 'border-gray-200 text-gray-600 hover:bg-gray-50'
          }`}
          aria-pressed={modo === 'nuevo'}
        >
          <UserPlus className="w-4 h-4" />
          Cliente nuevo
        </button>
      </div>

      {modo === 'existente' ? (
        <div>
          <label htmlFor="cliente-deuda" className="label">Cliente</label>
          <div className="relative mb-2">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              value={busqueda}
              onChange={(event) => setBusqueda(event.target.value)}
              placeholder="Buscar por nombre, teléfono o email..."
              aria-label="Buscar cliente para cargar deuda"
              className="input pl-9"
            />
          </div>
          <select
            id="cliente-deuda"
            value={clienteId}
            onChange={(event) => setClienteId(event.target.value)}
            className="input"
            disabled={cargandoClientes}
          >
            <option value="">
              {cargandoClientes ? 'Cargando clientes...' : 'Seleccionar cliente'}
            </option>
            {clientesFiltrados.map((cliente) => (
              <option key={cliente.id} value={cliente.id}>
                {cliente.nombre}{cliente.telefono ? ` · ${cliente.telefono}` : ''}
              </option>
            ))}
          </select>
          {errores.cliente && <p className="text-sm text-red-600 mt-1">{errores.cliente}</p>}
          {clienteId && (
            <p className="text-xs text-gray-500 mt-1">
              Saldo actual:{' '}
              {formatMoney(
                clientes.find((cliente) => String(cliente.id) === String(clienteId))?.saldoCorriente,
                'ARS'
              )}
            </p>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="sm:col-span-2">
            <label htmlFor="nuevo-nombre" className="label">Nombre *</label>
            <input
              id="nuevo-nombre"
              type="text"
              value={nuevo.nombre}
              onChange={(event) => setNuevo({ ...nuevo, nombre: event.target.value })}
              className="input"
              maxLength={200}
            />
            {errores.nombre && <p className="text-sm text-red-600 mt-1">{errores.nombre}</p>}
          </div>
          <div>
            <label htmlFor="nuevo-telefono" className="label">Teléfono</label>
            <input
              id="nuevo-telefono"
              type="tel"
              value={nuevo.telefono}
              onChange={(event) => setNuevo({ ...nuevo, telefono: event.target.value })}
              className="input"
            />
            {errores.telefono && <p className="text-sm text-red-600 mt-1">{errores.telefono}</p>}
          </div>
          <div>
            <label htmlFor="nuevo-cuit" className="label">CUIT</label>
            <input
              id="nuevo-cuit"
              type="text"
              value={nuevo.cuit}
              onChange={(event) => setNuevo({ ...nuevo, cuit: event.target.value })}
              className="input"
            />
            {errores.cuit && <p className="text-sm text-red-600 mt-1">{errores.cuit}</p>}
          </div>
          <div className="sm:col-span-2">
            <label htmlFor="nuevo-email" className="label">Email</label>
            <input
              id="nuevo-email"
              type="email"
              value={nuevo.email}
              onChange={(event) => setNuevo({ ...nuevo, email: event.target.value })}
              className="input"
            />
            {errores.email && <p className="text-sm text-red-600 mt-1">{errores.email}</p>}
          </div>
        </div>
      )}

      <div>
        <label htmlFor="monto-deuda" className="label">Monto de la deuda *</label>
        <input
          id="monto-deuda"
          type="text"
          inputMode="decimal"
          value={monto}
          onChange={(event) => setMonto(event.target.value)}
          placeholder="0,00"
          className="input"
        />
        {errores.monto && <p className="text-sm text-red-600 mt-1">{errores.monto}</p>}
      </div>

      <div>
        <label htmlFor="motivo-deuda" className="label">Motivo *</label>
        <textarea
          id="motivo-deuda"
          value={motivo}
          onChange={(event) => setMotivo(event.target.value)}
          placeholder="Ej: Compra de repuestos sin pago"
          rows={3}
          maxLength={MOTIVO_MAXIMO}
          className="input"
        />
        <div className="flex items-center justify-between mt-1">
          {errores.motivo
            ? <p className="text-sm text-red-600">{errores.motivo}</p>
            : <p className="text-xs text-gray-500">Mínimo {MOTIVO_MINIMO} caracteres</p>}
          <p className="text-xs text-gray-400">{motivo.length}/{MOTIVO_MAXIMO}</p>
        </div>
      </div>

      {conflicto && (
        <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-sm text-amber-800">
          <p className="font-medium">
            {conflicto.codigo === 'CLIENTE_INACTIVO'
              ? 'El cliente existe pero está dado de baja'
              : 'Ya existe un cliente con esos datos'}
          </p>
          <p className="mt-1">
            {conflicto.mensaje}
            {conflicto.clienteNombre ? ` (${conflicto.clienteNombre})` : ''}
          </p>
          {conflicto.activo && conflicto.clienteId && (
            <button
              type="button"
              onClick={() => usarClienteExistente(conflicto.clienteId)}
              className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-amber-900 underline"
            >
              <Plus className="w-3 h-3" />
              Usar el cliente existente
            </button>
          )}
          {conflicto.codigo === 'CLIENTE_INACTIVO' && (
            <p className="mt-2 text-xs">
              Reactivá el cliente desde la sección Clientes para poder cargarle deudas.
            </p>
          )}
        </div>
      )}

      {errorGeneral && (
        <div className="flex items-start gap-2 rounded-lg bg-red-50 text-red-700 p-3 text-sm">
          <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
          <p>{errorGeneral}</p>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={enviando} className="btn-primary">
          {enviando ? 'Cargando...' : 'Cargar deuda'}
        </button>
        {onCancelar && (
          <button type="button" onClick={onCancelar} className="btn-secondary" disabled={enviando}>
            Cancelar
          </button>
        )}
      </div>
    </form>
  );
};

export default CargarDeuda;
