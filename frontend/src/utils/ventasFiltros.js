export const ID_ANONIMA = 'anonima';

const ETIQUETA_ANONIMA = 'Venta anónima (sin cliente)';

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

const construirBase = (ventas, seleccionados, obtenerId, obtenerNombre) => {
  const conteos = new Map();
  const nombres = new Map();

  for (const venta of ventas || []) {
    const id = obtenerId(venta);
    if (id == null) continue;
    const clave = String(id);
    conteos.set(clave, (conteos.get(clave) || 0) + 1);
    const nombre = obtenerNombre(venta);
    if (nombre != null && nombre !== '') nombres.set(clave, String(nombre));
  }

  for (const [id, nombre] of Object.entries(seleccionados || {})) {
    if (!conteos.has(id)) conteos.set(id, 0);
    if (nombre != null && nombre !== '') nombres.set(id, String(nombre));
  }

  return { conteos, nombres };
};

export const construirOpcionesClientes = (ventas, seleccionados = {}) => {
  const { conteos, nombres } = construirBase(
    ventas,
    seleccionados,
    (venta) => venta.cliente?.id,
    (venta) => venta.cliente?.nombre
  );

  const anonimas = (ventas || []).filter((venta) => venta.cliente?.id == null).length;
  const anonimaSeleccionada = Object.prototype.hasOwnProperty.call(seleccionados || {}, ID_ANONIMA);
  const opciones = [];

  if (anonimas > 0 || anonimaSeleccionada) {
    opciones.push({ id: ID_ANONIMA, label: ETIQUETA_ANONIMA, count: anonimas });
  }

  const idsPorNombre = new Map();
  for (const [id, nombre] of nombres) {
    if (id === ID_ANONIMA) continue;
    const clave = normalizarTexto(nombre).trim();
    if (!idsPorNombre.has(clave)) idsPorNombre.set(clave, new Set());
    idsPorNombre.get(clave).add(id);
  }

  const clientes = [];
  for (const [id, count] of conteos) {
    if (id === ID_ANONIMA) continue;
    const nombre = nombres.get(id) || `Cliente #${id}`;
    const clave = normalizarTexto(nombre).trim();
    const repetido = (idsPorNombre.get(clave)?.size || 0) > 1;
    clientes.push({
      id,
      label: repetido ? `${nombre} · #${id}` : nombre,
      count,
    });
  }

  clientes.sort((a, b) => a.label.localeCompare(b.label, 'es'));

  return [...opciones, ...clientes];
};

export const construirOpcionesUsuarios = (ventas, seleccionados = {}) => {
  const { conteos, nombres } = construirBase(
    ventas,
    seleccionados,
    (venta) => venta.usuario?.id,
    (venta) => venta.usuario?.nombre || venta.usuario?.username
  );

  const usuarios = [];
  for (const [id, count] of conteos) {
    usuarios.push({
      id,
      label: nombres.get(id) || `Usuario #${id}`,
      count,
    });
  }

  usuarios.sort((a, b) => a.label.localeCompare(b.label, 'es'));

  return usuarios;
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
