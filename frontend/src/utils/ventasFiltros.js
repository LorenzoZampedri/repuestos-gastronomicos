export const ID_ANONIMA = 'anonima';

const ETIQUETA_ANONIMA = 'Venta anónima (sin cliente)';

const ID_VALIDO = /^\d+$/;
const MAX_NOMBRE_URL = 100;

const leerSeleccion = (params, idParam, nombreParam) => {
  const id = params.get(idParam);
  if (!id || !ID_VALIDO.test(id)) return {};
  const nombre = params.get(nombreParam);
  return { [id]: typeof nombre === 'string' ? nombre.slice(0, MAX_NOMBRE_URL) : '' };
};

export const leerSeleccionDesdeUrl = (search = '', hash = '') => {
  if (/^#venta-\d+$/.test(hash)) return { clientes: {}, usuarios: {} };
  const params = new URLSearchParams(search);
  return {
    clientes: leerSeleccion(params, 'cliente', 'clienteNombre'),
    usuarios: leerSeleccion(params, 'usuario', 'usuarioNombre'),
  };
};

export const normalizarTexto = (texto) => {
  if (texto == null) return '';
  return String(texto)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
};

const aMapaIds = (seleccion) => {
  if (!seleccion) return new Set();
  const ids = Array.isArray(seleccion) ? seleccion : Object.keys(seleccion);
  return new Set(ids.map((id) => String(id)));
};

const construirBase = (ventas, seleccionados, obtenerId, obtenerNombre, obtenerExtra) => {
  const conteos = new Map();
  const nombres = new Map();
  const extras = new Map();

  for (const venta of ventas || []) {
    const id = obtenerId(venta);
    if (id == null) continue;
    const clave = String(id);
    conteos.set(clave, (conteos.get(clave) || 0) + 1);
    const nombre = obtenerNombre(venta);
    if (nombre != null && nombre !== '') nombres.set(clave, String(nombre));
    if (obtenerExtra) {
      const extra = obtenerExtra(venta);
      if (extra != null && extra !== '') extras.set(clave, String(extra));
    }
  }

  for (const [id, nombre] of Object.entries(seleccionados || {})) {
    if (!conteos.has(id)) conteos.set(id, 0);
    if (!nombres.has(id) && nombre != null && nombre !== '') nombres.set(id, String(nombre));
  }

  return { conteos, nombres, extras };
};

const agruparIdsPorNombre = (nombres) => {
  const idsPorNombre = new Map();
  for (const [id, nombre] of nombres) {
    const clave = normalizarTexto(nombre).trim();
    if (!idsPorNombre.has(clave)) idsPorNombre.set(clave, new Set());
    idsPorNombre.get(clave).add(id);
  }
  return idsPorNombre;
};

const ordenarPorConteo = (opciones) => {
  const comparar = (a, b) => a.label.localeCompare(b.label, 'es');
  const conVentas = opciones.filter((opcion) => opcion.count > 0).sort(comparar);
  const sinVentas = opciones.filter((opcion) => opcion.count === 0).sort(comparar);
  return [...conVentas, ...sinVentas];
};

export const construirOpcionesClientes = (universo, ventasConteo, seleccionados = {}) => {
  const obtenerId = (venta) => venta.cliente?.id;
  const obtenerNombre = (venta) => venta.cliente?.nombre;
  const { conteos, nombres } = construirBase(universo, seleccionados, obtenerId, obtenerNombre);
  const { conteos: conteosConteo } = construirBase(ventasConteo, {}, obtenerId, obtenerNombre);

  const anonimasUniverso = (universo || []).filter((venta) => venta.cliente?.id == null).length;
  const anonimaSeleccionada = Object.prototype.hasOwnProperty.call(seleccionados || {}, ID_ANONIMA);
  const opciones = [];

  if (anonimasUniverso > 0 || anonimaSeleccionada) {
    const anonimas = (ventasConteo || []).filter((venta) => venta.cliente?.id == null).length;
    opciones.push({ id: ID_ANONIMA, label: ETIQUETA_ANONIMA, count: anonimas });
  }

  const nombresBase = new Map();
  for (const id of conteos.keys()) {
    if (id === ID_ANONIMA) continue;
    nombresBase.set(id, nombres.get(id) || `Cliente #${id}`);
  }

  const idsPorNombre = agruparIdsPorNombre(nombresBase);
  const clientes = [];
  for (const [id, nombre] of nombresBase) {
    const clave = normalizarTexto(nombre).trim();
    const repetido = (idsPorNombre.get(clave)?.size || 0) > 1;
    clientes.push({
      id,
      label: repetido ? `${nombre} · #${id}` : nombre,
      count: conteosConteo.get(id) || 0,
    });
  }

  return [...opciones, ...ordenarPorConteo(clientes)];
};

export const construirOpcionesUsuarios = (universo, ventasConteo, seleccionados = {}) => {
  const obtenerId = (venta) => venta.usuario?.id;
  const obtenerNombre = (venta) => venta.usuario?.nombre || venta.usuario?.username;
  const obtenerUsername = (venta) => venta.usuario?.username;
  const { conteos, nombres, extras } = construirBase(
    universo,
    seleccionados,
    obtenerId,
    obtenerNombre,
    obtenerUsername
  );
  const { conteos: conteosConteo } = construirBase(
    ventasConteo,
    {},
    obtenerId,
    obtenerNombre,
    obtenerUsername
  );

  const nombresBase = new Map();
  for (const id of conteos.keys()) {
    nombresBase.set(id, nombres.get(id) || `Personal #${id}`);
  }

  const idsPorNombre = agruparIdsPorNombre(nombresBase);
  const usuarios = [];
  for (const [id, nombre] of nombresBase) {
    const clave = normalizarTexto(nombre).trim();
    const repetido = (idsPorNombre.get(clave)?.size || 0) > 1;
    const sufijo = extras.get(id) ? `@${extras.get(id)}` : `#${id}`;
    usuarios.push({
      id,
      label: repetido ? `${nombre} · ${sufijo}` : nombre,
      count: conteosConteo.get(id) || 0,
    });
  }

  return ordenarPorConteo(usuarios);
};

export const filtrarVentas = (ventas, { clientes, usuarios } = {}) => {
  const idsClientes = aMapaIds(clientes);
  const idsUsuarios = aMapaIds(usuarios);
  const filtraClientes = idsClientes.size > 0;
  const filtraUsuarios = idsUsuarios.size > 0;

  return (ventas || []).filter((venta) => {
    if (filtraClientes) {
      const idCliente = venta.cliente?.id;
      const coincide =
        idCliente == null
          ? idsClientes.has(ID_ANONIMA)
          : idsClientes.has(String(idCliente));
      if (!coincide) return false;
    }

    if (filtraUsuarios) {
      const idUsuario = venta.usuario?.id;
      if (idUsuario == null || !idsUsuarios.has(String(idUsuario))) return false;
    }

    return true;
  });
};

export const sumarTotales = (ventas) =>
  (ventas || []).reduce((suma, venta) => suma + (Number(venta?.total) || 0), 0);
