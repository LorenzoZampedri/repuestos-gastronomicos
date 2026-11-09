import test from 'node:test';
import assert from 'node:assert/strict';
import {
  MOTIVO_MAXIMO,
  MOTIVO_MINIMO,
  parsearMonto,
  validarMontoDeuda,
  validarMotivoDeuda,
  validarClienteNuevo,
  construirPayloadDeuda,
  resolverConflictoCliente,
  agruparCompras,
} from './deuda.js';

test('parsearMonto acepta números, formato argentino y rechaza basura', () => {
  assert.equal(parsearMonto('1500.50'), 1500.5);
  assert.equal(parsearMonto('1.500,50'), 1500.5);
  assert.equal(parsearMonto('1,500.50'), 1500.5);
  assert.equal(parsearMonto('$ 1.500'), 1500);
  assert.equal(parsearMonto(25), 25);
  assert.equal(parsearMonto(''), null);
  assert.equal(parsearMonto('abc'), null);
  assert.equal(parsearMonto(null), null);
});

test('validarMontoDeuda exige positivo, hasta dos decimales y tope máximo', () => {
  assert.equal(validarMontoDeuda(''), 'Ingresá el monto de la deuda');
  assert.equal(validarMontoDeuda('0'), 'El monto debe ser mayor a cero');
  assert.equal(validarMontoDeuda('-1'), 'El monto debe ser mayor a cero');
  assert.equal(validarMontoDeuda('10.123'), 'El monto no puede tener más de 2 decimales');
  assert.equal(validarMontoDeuda('10.120'), '');
  assert.equal(validarMontoDeuda('100000000'), 'El monto supera el máximo permitido');
  assert.equal(validarMontoDeuda('1500,50'), '');
});

test('validarMotivoDeuda respeta el rango de longitud', () => {
  assert.notEqual(validarMotivoDeuda(''), '');
  assert.notEqual(validarMotivoDeuda('ab'), '');
  assert.equal(validarMotivoDeuda('Compra'), '');
  assert.equal(validarMotivoDeuda('a'.repeat(MOTIVO_MAXIMO)), '');
  assert.notEqual(validarMotivoDeuda('a'.repeat(MOTIVO_MAXIMO + 1)), '');
  assert.equal(MOTIVO_MINIMO, 3);
});

test('validarClienteNuevo valida nombre, teléfono, email y CUIT opcionales', () => {
  assert.deepEqual(validarClienteNuevo({ nombre: 'Juan', telefono: '', email: '', cuit: '' }), {});
  assert.ok(validarClienteNuevo({ nombre: 'J' }).nombre);
  assert.ok(validarClienteNuevo({ nombre: 'Juan', telefono: '123' }).telefono);
  assert.ok(validarClienteNuevo({ nombre: 'Juan', email: 'mal' }).email);
  assert.ok(validarClienteNuevo({ nombre: 'Juan', cuit: '123' }).cuit);
});

test('construirPayloadDeuda arma cliente existente o nuevo con campos limpios', () => {
  assert.deepEqual(
    construirPayloadDeuda({ clienteId: '7', monto: '10,50', motivo: '  Compra  ' }),
    { clienteId: 7, clienteNuevo: null, monto: 10.5, motivo: 'Compra' }
  );

  assert.deepEqual(
    construirPayloadDeuda({
      nuevo: { nombre: '  Ana  ', telefono: '  ', email: 'ana@example.com', cuit: null },
      monto: '20',
      motivo: 'Deuda inicial',
    }),
    {
      clienteId: null,
      clienteNuevo: {
        nombre: 'Ana',
        telefono: null,
        email: 'ana@example.com',
        cuit: null,
      },
      monto: 20,
      motivo: 'Deuda inicial',
    }
  );
});

test('resolverConflictoCliente distingue duplicado activo de inactivo', () => {
  const duplicado = resolverConflictoCliente({
    response: {
      data: {
        code: 'CLIENTE_DUPLICADO',
        message: 'Ya existe',
        datos: { clienteId: 3, clienteNombre: 'Ana' },
      },
    },
  });
  assert.equal(duplicado.codigo, 'CLIENTE_DUPLICADO');
  assert.equal(duplicado.clienteId, 3);
  assert.equal(duplicado.activo, true);

  const inactivo = resolverConflictoCliente({
    response: { data: { code: 'CLIENTE_INACTIVO', datos: { clienteId: 4 } } },
  });
  assert.equal(inactivo.activo, false);
  assert.equal(resolverConflictoCliente({ response: { data: { code: 'OTRO' } } }), null);
});

test('agruparCompras omite los grupos que no vienen por rol', () => {
  const grupos = agruparCompras({
    realizadas: { cantidad: 2, items: [] },
    pendientes: null,
    canceladas: null,
  });
  assert.equal(grupos.length, 1);
  assert.equal(grupos[0].clave, 'realizadas');
});
