import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ID_ANONIMA,
  normalizarTexto,
  construirOpcionesClientes,
  construirOpcionesUsuarios,
  filtrarVentas,
  sumarTotales,
} from './ventasFiltros.js';

const venta = (id, cliente, usuario, total = 100) => ({ id, cliente, usuario, total });

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
  const opciones = construirOpcionesClientes(ventas, {});
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
  const opciones = construirOpcionesClientes(ventas, {});
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Juan Pérez · #7', 'Juan Pérez · #9']
  );
});

test('las opciones seleccionadas sin ventas se mantienen con count 0', () => {
  const ventas = [venta(1, { id: 1, nombre: 'Ana' })];
  const opciones = construirOpcionesClientes(ventas, {
    5: 'Cliente viejo',
    [ID_ANONIMA]: 'Venta anónima (sin cliente)',
  });
  const viejo = opciones.find((opcion) => opcion.id === '5');
  const anonima = opciones.find((opcion) => opcion.id === ID_ANONIMA);
  assert.equal(viejo.count, 0);
  assert.equal(anonima.count, 0);
  assert.equal(opciones.filter((opcion) => opcion.id === ID_ANONIMA).length, 1);
});

test('construirOpcionesUsuarios agrupa y ordena por nombre', () => {
  const ventas = [
    venta(1, null, { id: 2, nombre: 'Zoe' }),
    venta(2, null, { id: 1, nombre: 'Bruno' }),
    venta(3, null, { id: 1, nombre: 'Bruno' }),
  ];
  const opciones = construirOpcionesUsuarios(ventas, {});
  assert.deepEqual(
    opciones.map((opcion) => opcion.label),
    ['Bruno', 'Zoe']
  );
  assert.deepEqual(
    opciones.map((opcion) => opcion.count),
    [2, 1]
  );
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
