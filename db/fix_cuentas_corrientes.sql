-- Reparación de cuentas corrientes (PostgreSQL)
--
-- Corrige datos generados por versiones anteriores que permitían:
--   1) ventas sobrepagadas (saldo pendiente negativo) junto con otras ventas impagas;
--   2) ventas anuladas que dejaban su deuda pendiente en el saldo del cliente.
--
-- El script es de ejecución única. Registra su aplicación en reparaciones_datos.
--
-- Uso con Docker (PowerShell):
--   Get-Content db\fix_cuentas_corrientes.sql | docker exec -i repuestos-gastronomicos-postgres-1 psql -U admin -d repuestos_db
--
-- Uso con Docker (bash):
--   docker exec -i repuestos-gastronomicos-postgres-1 psql -U admin -d repuestos_db < db/fix_cuentas_corrientes.sql

BEGIN;

CREATE TABLE IF NOT EXISTS reparaciones_datos (
    nombre VARCHAR(100) PRIMARY KEY,
    aplicado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
DECLARE
    v_cliente RECORD;
    v_origen RECORD;
    v_destino RECORD;
    v_pago RECORD;
    v_excedente NUMERIC(14,2);
    v_pendiente NUMERIC(14,2);
    v_mover NUMERIC(14,2);
    v_restante NUMERIC(14,2);
    v_tomar NUMERIC(14,2);
    v_ya_aplicado BOOLEAN;
BEGIN
    SELECT EXISTS(
        SELECT 1 FROM reparaciones_datos
        WHERE nombre = 'fix_cuentas_corrientes_v1'
    ) INTO v_ya_aplicado;

    IF v_ya_aplicado THEN
        RAISE NOTICE 'La reparación fix_cuentas_corrientes_v1 ya fue aplicada. No se hacen cambios.';
        RETURN;
    END IF;

    -- 1) Mover el excedente de ventas sobrepagadas a las ventas abiertas del mismo cliente.
    FOR v_cliente IN
        SELECT DISTINCT cliente_id
        FROM ventas
        WHERE cliente_id IS NOT NULL
    LOOP
        FOR v_origen IN
            SELECT v.id, v.total, COALESCE(SUM(p.monto), 0) AS pagado
            FROM ventas v
            LEFT JOIN pagos p ON p.venta_id = v.id
            WHERE v.cliente_id = v_cliente.cliente_id
              AND v.estado <> 'ANULADA'
            GROUP BY v.id, v.total
            HAVING COALESCE(SUM(p.monto), 0) > v.total
            ORDER BY v.fecha, v.id
        LOOP
            v_excedente := v_origen.pagado - v_origen.total;

            FOR v_destino IN
                SELECT v.id, v.total, COALESCE(SUM(p.monto), 0) AS pagado
                FROM ventas v
                LEFT JOIN pagos p ON p.venta_id = v.id
                WHERE v.cliente_id = v_cliente.cliente_id
                  AND v.estado <> 'ANULADA'
                  AND v.id <> v_origen.id
                GROUP BY v.id, v.total
                HAVING COALESCE(SUM(p.monto), 0) < v.total
                ORDER BY v.fecha, v.id
            LOOP
                EXIT WHEN v_excedente <= 0;

                v_pendiente := v_destino.total - v_destino.pagado;
                v_mover := LEAST(v_excedente, v_pendiente);
                v_restante := v_mover;

                FOR v_pago IN
                    SELECT id, monto, metodo_pago, moneda, fecha, usuario_id
                    FROM pagos
                    WHERE venta_id = v_origen.id
                      AND monto > 0
                    ORDER BY id DESC
                LOOP
                    EXIT WHEN v_restante <= 0;

                    v_tomar := LEAST(v_restante, v_pago.monto);

                    IF v_tomar >= v_pago.monto THEN
                        UPDATE pagos
                        SET venta_id = v_destino.id,
                            cliente_id = v_cliente.cliente_id
                        WHERE id = v_pago.id;
                    ELSE
                        UPDATE pagos
                        SET monto = monto - v_tomar
                        WHERE id = v_pago.id;

                        INSERT INTO pagos (
                            venta_id, cliente_id, monto, metodo_pago, moneda,
                            fecha, usuario_id, referencia
                        ) VALUES (
                            v_destino.id, v_cliente.cliente_id, v_tomar,
                            v_pago.metodo_pago, v_pago.moneda, v_pago.fecha,
                            v_pago.usuario_id, 'Reasignación de sobrepago'
                        );
                    END IF;

                    v_restante := v_restante - v_tomar;
                END LOOP;

                IF v_restante > 0 THEN
                    RAISE EXCEPTION 'No hay pagos suficientes para reasignar %.2f de la venta %',
                        v_mover, v_origen.id;
                END IF;

                v_excedente := v_excedente - v_mover;
            END LOOP;
        END LOOP;
    END LOOP;

    -- 2) Recalcular el estado de todas las ventas no anuladas según sus pagos.
    UPDATE ventas v
    SET estado = CASE
        WHEN p.pagado >= v.total THEN 'PAGADA'
        WHEN p.pagado > 0 THEN 'PARCIAL'
        ELSE 'PENDIENTE'
    END
    FROM (
        SELECT v2.id, COALESCE(SUM(p2.monto), 0) AS pagado
        FROM ventas v2
        LEFT JOIN pagos p2 ON p2.venta_id = v2.id
        GROUP BY v2.id
    ) p
    WHERE p.id = v.id
      AND v.estado <> 'ANULADA';

    -- 3) Quitar del saldo del cliente el efecto neto que quedó de ventas anuladas.
    UPDATE clientes c
    SET saldo_corriente = c.saldo_corriente - a.diferencia
    FROM (
        SELECT v.cliente_id,
               SUM(v.total - COALESCE(p.pagado, 0)) AS diferencia
        FROM ventas v
        LEFT JOIN (
            SELECT venta_id, SUM(monto) AS pagado
            FROM pagos
            WHERE venta_id IS NOT NULL
            GROUP BY venta_id
        ) p ON p.venta_id = v.id
        WHERE v.estado = 'ANULADA'
          AND v.cliente_id IS NOT NULL
        GROUP BY v.cliente_id
    ) a
    WHERE c.id = a.cliente_id
      AND a.diferencia <> 0;

    INSERT INTO reparaciones_datos (nombre)
    VALUES ('fix_cuentas_corrientes_v1');

    RAISE NOTICE 'Reparación fix_cuentas_corrientes_v1 aplicada correctamente.';
END
$$;

COMMIT;
