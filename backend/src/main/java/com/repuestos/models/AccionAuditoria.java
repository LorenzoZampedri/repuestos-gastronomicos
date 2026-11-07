package com.repuestos.models;

/**
 * Catálogo estable de acciones auditables. El nombre del enum es el código que se
 * persiste en {@code logs_actividad.accion}; nunca se debe renombrar un valor sin
 * migrar los registros existentes.
 */
public enum AccionAuditoria {

    LOGIN_OK("AUTH", "Inicio de sesión correcto"),
    LOGIN_FALLIDO("AUTH", "Inicio de sesión fallido"),
    PASSWORD_CAMBIADA("AUTH", "Contraseña cambiada"),
    PASSWORD_RESETEADA("AUTH", "Contraseña reseteada"),

    USUARIO_CREADO("USUARIOS", "Usuario creado"),
    USUARIO_EDITADO("USUARIOS", "Usuario editado"),
    USUARIO_ACTIVADO("USUARIOS", "Usuario activado"),
    USUARIO_DESACTIVADO("USUARIOS", "Usuario desactivado"),
    USUARIO_CLIENTE_VINCULADO("USUARIOS", "Cliente vinculado a usuario"),

    PRODUCTO_CREADO("PRODUCTOS", "Producto creado"),
    PRODUCTO_EDITADO("PRODUCTOS", "Producto editado"),
    STOCK_AJUSTADO("PRODUCTOS", "Stock ajustado"),
    PRODUCTO_ELIMINADO("PRODUCTOS", "Producto eliminado"),
    PRODUCTO_IMAGEN_SUBIDA("PRODUCTOS", "Imagen de producto subida"),
    CATEGORIA_CREADA("PRODUCTOS", "Categoría creada"),
    PRECIOS_ACTUALIZADOS_MASIVO("PRODUCTOS", "Actualización masiva de precios"),
    PRECIOS_REVERTIDOS("PRODUCTOS", "Actualización de precios revertida"),
    PRECIOS_RECALCULADOS("PRODUCTOS", "Precios en pesos recalculados"),

    CLIENTE_CREADO("CLIENTES", "Cliente creado"),
    CLIENTE_EDITADO("CLIENTES", "Cliente editado"),
    CLIENTE_ELIMINADO("CLIENTES", "Cliente eliminado"),
    CLIENTE_SALDO_AJUSTADO("CLIENTES", "Saldo de cliente ajustado"),

    VENTA_CREADA("VENTAS", "Venta creada"),
    VENTA_ANULADA("VENTAS", "Venta anulada"),
    PAGO_REGISTRADO("VENTAS", "Pago registrado"),
    PAGO_CUENTA_CORRIENTE("VENTAS", "Pago a cuenta corriente"),

    PEDIDO_CREADO("PEDIDOS", "Pedido creado"),
    PEDIDO_CANCELADO("PEDIDOS", "Pedido cancelado"),
    PEDIDO_APROBADO("PEDIDOS", "Pedido aprobado"),
    PEDIDO_RECHAZADO("PEDIDOS", "Pedido rechazado"),

    CONFIG_EDITADA("CONFIGURACION", "Configuración editada"),
    CONFIG_TRANSFERENCIA_EDITADA("CONFIGURACION", "Datos de transferencia editados"),

    ACCESO_DENEGADO("SEGURIDAD", "Acceso denegado");

    private final String modulo;
    private final String etiqueta;

    AccionAuditoria(String modulo, String etiqueta) {
        this.modulo = modulo;
        this.etiqueta = etiqueta;
    }

    public String getModulo() {
        return modulo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean esDeCliente() {
        return this == PEDIDO_CREADO || this == PEDIDO_CANCELADO;
    }
}
