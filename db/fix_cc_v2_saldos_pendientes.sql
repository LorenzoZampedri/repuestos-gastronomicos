-- Reparacion segura de saldos legacy de Cuentas Corrientes.
-- Sube solo saldos menores al pendiente real de ventas no anuladas,
-- y solo para clientes sin pagos registrados sin venta asociada.
-- Idempotente mediante reparaciones_datos.

BEGIN;

CREATE TABLE IF NOT EXISTS reparaciones_datos (
    nombre VARCHAR(100) PRIMARY KEY,
    aplicado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clientes_saldo_backup (
    id SERIAL PRIMARY KEY,
    cliente_id INTEGER NOT NULL,
    saldo_anterior NUMERIC(10,2) NOT NULL,
    saldo_nuevo NUMERIC(10,2) NOT NULL,
    motivo VARCHAR(200),
    aplicado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
DECLARE
    v_ya_aplicado BOOLEAN;
    v_reg RECORD;
BEGIN
    SELECT EXISTS(
        SELECT 1 FROM reparaciones_datos
        WHERE nombre = 'fix_cc_v2_saldos_pendientes'
    ) INTO v_ya_aplicado;

    IF v_ya_aplicado THEN
        RAISE NOTICE 'fix_cc_v2_saldos_pendientes ya fue aplicada. No se hacen cambios.';
        RETURN;
    END IF;

    FOR v_reg IN
        WITH pendiente AS (
            SELECT c.id,
                   COALESCE(SUM(
                       CASE WHEN v.estado <> 'ANULADA'
                            THEN v.total - COALESCE(p.pagado, 0)
                            ELSE 0 END
                   ), 0)::numeric(10,2) AS esperado
            FROM clientes c
            LEFT JOIN ventas v ON v.cliente_id = c.id
            LEFT JOIN (
                SELECT venta_id, SUM(monto) AS pagado
                FROM pagos
                WHERE venta_id IS NOT NULL
                GROUP BY venta_id
            ) p ON p.venta_id = v.id
            GROUP BY c.id
        ),
        sin_venta AS (
            SELECT cliente_id, SUM(monto) AS total
            FROM pagos
            WHERE venta_id IS NULL
            GROUP BY cliente_id
        )
        SELECT c.id,
               c.nombre,
               c.saldo_corriente,
               p.esperado,
               COALESCE(sv.total, 0)::numeric(10,2) AS pagos_sin_venta
        FROM clientes c
        JOIN pendiente p ON p.id = c.id
        LEFT JOIN sin_venta sv ON sv.cliente_id = c.id
        WHERE c.activo
          AND p.esperado > c.saldo_corriente
          AND c.saldo_corriente >= 0
          AND COALESCE(sv.total, 0) = 0
        ORDER BY c.id
    LOOP
        INSERT INTO clientes_saldo_backup (
            cliente_id, saldo_anterior, saldo_nuevo, motivo
        ) VALUES (
            v_reg.id, v_reg.saldo_corriente, v_reg.esperado,
            'v2: saldo menor al pendiente de ventas, sin pagos sin venta'
        );

        UPDATE clientes
        SET saldo_corriente = v_reg.esperado
        WHERE id = v_reg.id;

        RAISE NOTICE 'Cliente % (%): saldo % -> %',
            v_reg.id, v_reg.nombre, v_reg.saldo_corriente, v_reg.esperado;
    END LOOP;

    INSERT INTO reparaciones_datos (nombre)
    VALUES ('fix_cc_v2_saldos_pendientes');
END
$$;

COMMIT;
