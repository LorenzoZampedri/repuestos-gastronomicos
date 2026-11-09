export const MOTIVO_MINIMO = 3;
export const MOTIVO_MAXIMO = 300;
export const MONTO_MAXIMO = 99999999.99;

const limpiarMonto = (valor) => String(valor ?? '')
  .trim()
  .replace(/\s/g, '')
  .replace(/\$/g, '');

const normalizarSeparadores = (texto) => {
  if (!texto) return '';
  const ultimaComa = texto.lastIndexOf(',');
  const ultimoPunto = texto.lastIndexOf('.');

  if (ultimaComa >= 0 && ultimoPunto >= 0) {
    if (ultimaComa > ultimoPunto) {
      return texto.replace(/\./g, '').replace(/,/g, '.');
    }
    return texto.replace(/,/g, '');
  }

  if (ultimaComa >= 0) {
    return texto.replace(/\./g, '').replace(/,/g, '.');
  }

  if (/^\d{1,3}(\.\d{3})+$/.test(texto)) {
    return texto.replace(/\./g, '');
  }

  return texto;
};

export const parsearMonto = (valor) => {
  if (valor == null || valor === '') return null;
  const numeroDirecto = Number(valor);
  if (typeof valor === 'number' && Number.isFinite(numeroDirecto)) return numeroDirecto;

  const texto = normalizarSeparadores(limpiarMonto(valor));
  if (!/^-?\d+(\.\d+)?$/.test(texto)) return null;
  const numero = Number(texto);
  return Number.isFinite(numero) ? numero : null;
};

const decimalesEscritos = (valor) => {
  const texto = limpiarMonto(valor);
  const posicion = Math.max(texto.lastIndexOf(','), texto.lastIndexOf('.'));
  if (posicion < 0) return 0;
  return texto.slice(posicion + 1).replace(/\D/g, '').replace(/0+$/, '').length;
};

export const validarMontoDeuda = (valor) => {
  const texto = limpiarMonto(valor);
  if (!texto) return 'Ingresá el monto de la deuda';

  const monto = parsearMonto(texto);
  if (monto == null) return 'Ingresá un monto válido';
  if (monto <= 0) return 'El monto debe ser mayor a cero';
  if (decimalesEscritos(texto) > 2) return 'El monto no puede tener más de 2 decimales';
  if (monto > MONTO_MAXIMO) return 'El monto supera el máximo permitido';
  return '';
};

export const validarMotivoDeuda = (motivo) => {
  const texto = String(motivo ?? '').trim();
  if (texto.length < MOTIVO_MINIMO || texto.length > MOTIVO_MAXIMO) {
    return `El motivo debe tener entre ${MOTIVO_MINIMO} y ${MOTIVO_MAXIMO} caracteres`;
  }
  return '';
};

export const validarClienteNuevo = ({ nombre, telefono, email, cuit } = {}) => {
  const errores = {};
  const nombreLimpio = String(nombre ?? '').trim();
  if (nombreLimpio.length < 2 || nombreLimpio.length > 200) {
    errores.nombre = 'El nombre debe tener entre 2 y 200 caracteres';
  }

  const telefonoLimpio = String(telefono ?? '').trim();
  if (telefonoLimpio && telefonoLimpio.replace(/\D/g, '').length < 6) {
    errores.telefono = 'El teléfono debe tener al menos 6 dígitos';
  }

  const emailLimpio = String(email ?? '').trim();
  if (emailLimpio && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLimpio)) {
    errores.email = 'Ingresá un email válido';
  }

  const cuitLimpio = String(cuit ?? '').trim();
  if (cuitLimpio && ![10, 11].includes(cuitLimpio.replace(/\D/g, '').length)) {
    errores.cuit = 'Ingresá un CUIT válido';
  }

  return errores;
};

const limpiarOpcional = (valor) => {
  const texto = String(valor ?? '').trim();
  return texto || null;
};

export const construirPayloadDeuda = ({
  clienteId = null,
  nuevo = null,
  monto,
  motivo,
} = {}) => {
  const montoNumerico = parsearMonto(monto);
  if (clienteId) {
    return {
      clienteId: Number(clienteId),
      clienteNuevo: null,
      monto: montoNumerico,
      motivo: String(motivo ?? '').trim(),
    };
  }

  return {
    clienteId: null,
    clienteNuevo: {
      nombre: String(nuevo?.nombre ?? '').trim(),
      telefono: limpiarOpcional(nuevo?.telefono),
      email: limpiarOpcional(nuevo?.email),
      cuit: limpiarOpcional(nuevo?.cuit),
    },
    monto: montoNumerico,
    motivo: String(motivo ?? '').trim(),
  };
};

export const resolverConflictoCliente = (error) => {
  const data = error?.response?.data;
  if (!data) return null;
  const codigo = data.code;
  if (codigo !== 'CLIENTE_DUPLICADO' && codigo !== 'CLIENTE_INACTIVO') return null;
  return {
    codigo,
    mensaje: data.message || 'Ya existe un cliente con esos datos',
    clienteId: data.clienteId ?? data.datos?.clienteId ?? null,
    clienteNombre: data.clienteNombre ?? data.datos?.clienteNombre ?? null,
    activo: codigo === 'CLIENTE_DUPLICADO',
  };
};

export const agruparCompras = (compras) => {
  if (!compras) return [];
  return [
    { clave: 'realizadas', titulo: 'Realizadas', grupo: compras.realizadas },
    { clave: 'pendientes', titulo: 'Pendientes', grupo: compras.pendientes },
    { clave: 'canceladas', titulo: 'Canceladas', grupo: compras.canceladas },
  ].filter(({ grupo }) => grupo != null);
};
