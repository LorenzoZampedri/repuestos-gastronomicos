import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ID_ANONIMA,
  normalizarTexto,
  construirOpcionesClientes,
  construirOpcionesUsuarios,
  filtrarVentas,
  leerSeleccionDesdeUrl,
  sumarTotales,
} from './ventasFiltros.js';

const venta = (id, cliente, usuario, total = 100) => ({ id, cliente, usuario, total });

const ventasEjemplo = () => [
  venta(1, { id: 1, nombre: 'Cliente X' }, { id: 10, nombre: 'Persona P', username: 'persona_p' }),
  venta(2, { id: 1, nombre: 'Cliente X' }, { id: 10, nombre: 'Persona P', username: 'persona_p' }),
  venta(3, { id: 1, nombre: 'Cliente X' }, { id: 10, nombre: 'Persona P', username: 'persona_p' }),
  venta(4, { id: 1, nombre: 'Cliente X' }, { id: 10, nombre: 'Persona P', username: 'persona_p' }),
  venta(5, { id: 1, nombre: 'Cliente X' }, { id: 10, nombre: 'Persona P', username: 'persona_p' }),
  venta(6, { id: 2, nombre: 'Cliente Y' }, { id: 11, nombre: 'Persona Q', username: 'persona_q' }),
];

test('normalizarTexto quita acentos y mayúsculas y tolera null', () => {
  assert.equal(normalizarTexto('José ÁNGEL'), 'jose angel');
  assert.equal(normalizarTexto(null), '');
  assert.equal(normalizarTexto(undefined), '');
});

test('construirOpcionesClientes incluye anónimas primero y ordena alfabéticamente', () => {
  const ventas = [
    venta(1, { id: 2, nombre: 'María López' }),
    venta(2, null),
    venta(3, { id: 1, nombre: 'Ana' }),
  ];
  const opciones = construirOpcionesClientes(ventas, ventas, {});
  assert.equal(opciones[0].id, ID_ANONIMA);
  assert.equal(opciones[0].count, 1);
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Venta anónima (sin cliente)', 'Ana', 'María López']
  );
  assert.deepEqual(
    opciones.map((opcion) => opcion.count),
    [1, 1, 1]
  );
});

test('construirOpcionesClientes desambigua nombres repetidos por id', () => {
  const ventas = [
    venta(1, { id: 7, nombre: 'Juan Pérez' }),
    venta(2, { id: 9, nombre: 'Juan Pérez' }),
  ];
  const opciones = construirOpcionesClientes(ventas, ventas, {});
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Juan Pérez · #7', 'Juan Pérez · #9']
  );
});

test('las opciones seleccionadas sin ventas se mantienen con count 0', () => {
  const ventas = [venta(1, { id: 1, nombre: 'Ana' })];
  const opciones = construirOpcionesClientes(ventas, ventas, {
    5: 'Cliente viejo',
    [ID_ANONIMA]: 'Venta anónima (sin cliente)',
  });
  const viejo = opciones.find((opcion) => opcion.id === '5');
  const anonima = opciones.find((opcion) => opcion.id === ID_ANONIMA);
  assert.equal(viejo.count, 0);
  assert.equal(anonima.count, 0);
  assert.equal(opciones.filter((opcion) => opcion.id === ID_ANONIMA).length, 1);
  assert.equal(opciones[opciones.length - 1].id, '5');
});

test('construirOpcionesUsuarios agrupa y ordena por nombre', () => {
  const ventas = [
    venta(1, null, { id: 2, nombre: 'Zoe' }),
    venta(2, null, { id: 1, nombre: 'Bruno' }),
    venta(3, null, { id: 1, nombre: 'Bruno' }),
  ];
  const opciones = construirOpcionesUsuarios(ventas, ventas, {});
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Bruno', 'Zoe']
  );
  assert.deepEqual(
    opciones.map((opcion) => opcion.count),
    [2, 1]
  );
});

test('los conteos de clientes respetan la selección de personal e ignoran la de clientes', () => {
  const ventas = ventasEjemplo();
  const basePersonal = filtrarVentas(ventas, { clientes: {}, usuarios: { 10: 'Persona P' } });
  const opciones = construirOpcionesClientes(ventas, basePersonal, {});

  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Cliente X', 'Cliente Y']
  );
  assert.deepEqual(
    opciones.map((opcion) => opcion.count),
    [5, 0]
  );
});

test('los conteos de personal respetan la selección de clientes e ignoran la de personal', () => {
  const ventas = ventasEjemplo();
  const baseClientes = filtrarVentas(ventas, { clientes: { 1: 'Cliente X' }, usuarios: {} });
  const opciones = construirOpcionesUsuarios(ventas, baseClientes, {});

  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Persona P', 'Persona Q']
  );
  assert.deepEqual(
    opciones.map((opcion) => opcion.count),
    [5, 0]
  );
});

test('ejemplo: cliente con 5 ventas de una sola persona deja al resto en 0', () => {
  const ventas = ventasEjemplo();
  const basePersonal = filtrarVentas(ventas, { clientes: {}, usuarios: { 10: 'Persona P' } });
  const opcionesPersonal = construirOpcionesUsuarios(
    ventas,
    filtrarVentas(ventas, { clientes: { 1: 'Cliente X' }, usuarios: {} }),
    {}
  );
  const opcionesClientes = construirOpcionesClientes(ventas, basePersonal, {});

  const personaP = opcionesPersonal.find((opcion) => opcion.id === '10');
  const personaQ = opcionesPersonal.find((opcion) => opcion.id === '11');
  const clienteX = opcionesClientes.find((opcion) => opcion.id === '1');

  assert.equal(personaP.count, 5);
  assert.equal(personaQ.count, 0);
  assert.equal(clienteX.count, 5);
  assert.equal(opcionesPersonal[opcionesPersonal.length - 1].id, '11');
  assert.deepEqual(
    filtrarVentas(ventas, { clientes: { 1: 'Cliente X' }, usuarios: { 10: 'Persona P' } }).length,
    5
  );
});

test('un filtro no modifica los conteos de su propio desplegable', () => {
  const ventas = ventasEjemplo();
  const opcionesClientes = construirOpcionesClientes(ventas, ventas, { 1: 'Cliente X' });
  const opcionesPersonal = construirOpcionesUsuarios(ventas, ventas, { 10: 'Persona P' });

  assert.deepEqual(
    opcionesClientes.map((opcion) => opcion.count),
    [5, 1]
  );
  assert.deepEqual(
    opcionesPersonal.map((opcion) => opcion.count),
    [5, 1]
  );
});

test('las opciones con conteo 0 se incluyen al final y ordenadas alfabéticamente', () => {
  const ventas = [
    venta(1, { id: 1, nombre: 'Alfa' }, { id: 10, nombre: 'Ana' }),
    venta(2, { id: 2, nombre: 'Zeta' }, { id: 11, nombre: 'Bruno' }),
    venta(3, { id: 3, nombre: 'Beta' }, { id: 11, nombre: 'Bruno' }),
  ];
  const baseClientes = filtrarVentas(ventas, { clientes: {}, usuarios: { 10: 'Ana' } });
  const opciones = construirOpcionesClientes(ventas, baseClientes, {});

  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Alfa', 'Beta', 'Zeta']
  );
  assert.deepEqual(
    opciones.map((opcion) => opcion.count),
    [1, 0, 0]
  );
});

test('Venta anónima queda fija arriba aunque su conteo contextual sea 0', () => {
  const ventas = [
    venta(1, null, { id: 10, nombre: 'Ana' }),
    venta(2, { id: 1, nombre: 'Alfa' }, { id: 11, nombre: 'Bruno' }),
  ];
  const base = filtrarVentas(ventas, { clientes: {}, usuarios: { 11: 'Bruno' } });
  const opciones = construirOpcionesClientes(ventas, base, {});

  assert.equal(opciones[0].id, ID_ANONIMA);
  assert.equal(opciones[0].count, 0);
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Venta anónima (sin cliente)', 'Alfa']
  );
});

test('construirOpcionesUsuarios desambigua nombres repetidos con username', () => {
  const ventas = [
    venta(1, null, { id: 15, nombre: 'Empleado E2E', username: 'empleado_x' }),
    venta(2, null, { id: 18, nombre: 'Empleado E2E', username: 'empleado_y' }),
  ];
  const opciones = construirOpcionesUsuarios(ventas, ventas, {});
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Empleado E2E · @empleado_x', 'Empleado E2E · @empleado_y']
  );
});

test('construirOpcionesUsuarios usa #id si falta el username al desambiguar', () => {
  const ventas = [
    venta(1, null, { id: 20, nombre: 'Sin Username' }),
    venta(2, null, { id: 21, nombre: 'Sin Username' }),
  ];
  const opciones = construirOpcionesUsuarios(ventas, ventas, {});
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Sin Username · #20', 'Sin Username · #21']
  );
});

test('sin selección los conteos son los globales del período', () => {
  const ventas = ventasEjemplo();
  const opcionesClientes = construirOpcionesClientes(ventas, ventas, {});
  const opcionesPersonal = construirOpcionesUsuarios(ventas, ventas, {});

  assert.deepEqual(
    opcionesClientes.map((opcion) => opcion.count),
    [5, 1]
  );
  assert.deepEqual(
    opcionesPersonal.map((opcion) => opcion.count),
    [5, 1]
  );
});

test('el conteo de cada opción coincide con las filas al combinarla con el otro filtro', () => {
  const ventas = ventasEjemplo();

  const combinaciones = [
    { seleccion: {}, tipo: 'cliente' },
    { seleccion: { usuarios: { 10: 'Persona P' } }, tipo: 'cliente' },
    { seleccion: { clientes: { 1: 'Cliente X' } }, tipo: 'personal' },
  ];

  for (const { seleccion, tipo } of combinaciones) {
    if (tipo === 'cliente') {
      const base = filtrarVentas(ventas, { clientes: {}, usuarios: seleccion.usuarios || {} });
      const opciones = construirOpcionesClientes(ventas, base, seleccion.clientes || {});
      for (const opcion of opciones) {
        const filas = filtrarVentas(ventas, {
          clientes: { ...(seleccion.clientes || {}), [opcion.id]: opcion.label },
          usuarios: seleccion.usuarios || {},
        });
        assert.equal(filas.length, opcion.count);
      }
    } else {
      const base = filtrarVentas(ventas, { clientes: seleccion.clientes || {}, usuarios: {} });
      const opciones = construirOpcionesUsuarios(ventas, base, seleccion.usuarios || {});
      for (const opcion of opciones) {
        const filas = filtrarVentas(ventas, {
          clientes: seleccion.clientes || {},
          usuarios: { ...(seleccion.usuarios || {}), [opcion.id]: opcion.label },
        });
        assert.equal(filas.length, opcion.count);
      }
    }
  }
});

test('filtrarVentas por cliente usa OR y respeta ventas anónimas', () => {
  const ventas = [
    venta(1, { id: 1, nombre: 'Ana' }),
    venta(2, { id: 2, nombre: 'Beto' }),
    venta(3, null),
  ];
  assert.deepEqual(
    filtrarVentas(ventas, { clientes: { 1: 'Ana', 2: 'Beto' } }).map((v) => v.id),
    [1, 2]
  );
  assert.deepEqual(
    filtrarVentas(ventas, { clientes: { [ID_ANONIMA]: 'Venta anónima' } }).map((v) => v.id),
    [3]
  );
  assert.deepEqual(
    filtrarVentas(ventas, { clientes: {} }).map((v) => v.id),
    [1, 2, 3]
  );
});

test('filtrarVentas por usuario y combinado aplica AND', () => {
  const ventas = [
    venta(1, { id: 1, nombre: 'Ana' }, { id: 10, nombre: 'Luis' }),
    venta(2, { id: 1, nombre: 'Ana' }, { id: 11, nombre: 'Marta' }),
    venta(3, { id: 2, nombre: 'Beto' }, { id: 10, nombre: 'Luis' }),
    venta(4, null, null),
  ];
  assert.deepEqual(
    filtrarVentas(ventas, { usuarios: { 10: 'Luis' } }).map((v) => v.id),
    [1, 3]
  );
  assert.deepEqual(
    filtrarVentas(ventas, { clientes: { 1: 'Ana' }, usuarios: { 10: 'Luis' } }).map((v) => v.id),
    [1]
  );
});

test('filtrarVentas no rompe con ventas sin usuario y devuelve vacío al pedir usuario inexistente', () => {
  const ventas = [venta(1, null, null), venta(2, { id: 1, nombre: 'Ana' })];
  assert.deepEqual(filtrarVentas(ventas, { usuarios: { 99: 'Nadie' } }), []);
  assert.deepEqual(filtrarVentas([], { clientes: { 1: 'Ana' } }), []);
});

test('sumarTotales suma solo los totales válidos', () => {
  assert.equal(sumarTotales([{ total: 100 }, { total: 50 }, { total: null }]), 150);
});

test('leerSeleccionDesdeUrl lee usuario con y sin nombre', () => {
  assert.deepEqual(
    leerSeleccionDesdeUrl('?usuario=15&usuarioNombre=Empleado%20E2E'),
    { clientes: {}, usuarios: { 15: 'Empleado E2E' } }
  );
  assert.deepEqual(
    leerSeleccionDesdeUrl('?usuario=15'),
    { clientes: {}, usuarios: { 15: '' } }
  );
});

test('leerSeleccionDesdeUrl es simétrico para cliente', () => {
  assert.deepEqual(
    leerSeleccionDesdeUrl('?cliente=7&clienteNombre=Mar%C3%ADa'),
    { clientes: { 7: 'María' }, usuarios: {} }
  );
});

test('leerSeleccionDesdeUrl ignora ids no numéricos', () => {
  assert.deepEqual(
    leerSeleccionDesdeUrl('?usuario=abc&usuarioNombre=Alguien&cliente=12x&clienteNombre=Otro'),
    { clientes: {}, usuarios: {} }
  );
});

test('leerSeleccionDesdeUrl recorta el nombre a 100 caracteres', () => {
  const nombre = 'x'.repeat(150);
  const seleccion = leerSeleccionDesdeUrl(`?usuario=3&usuarioNombre=${nombre}`);
  assert.equal(seleccion.usuarios['3'].length, 100);
});

test('un hash #venta-ID anula la selección de la URL', () => {
  assert.deepEqual(
    leerSeleccionDesdeUrl('?usuario=15&usuarioNombre=Empleado', '#venta-42'),
    { clientes: {}, usuarios: {} }
  );
});
