#!/usr/bin/env python3
"""
Script para importar productos desde Excel a PostgreSQL
Uso: python scripts/importar_excel.py
"""

import pandas as pd
import psycopg2
import os
import sys

# Configuración de conexión
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", 5432)),
    "database": os.getenv("DB_NAME", "repuestos_db"),
    "user": os.getenv("DB_USER", "admin"),
    "password": os.getenv("DB_PASSWORD", "repuestos123")
}

# Ruta del archivo Excel (buscar en scripts/ o raíz)
EXCEL_PATHS = [
    "scripts/inventario.xlsx",
    "inventario.xlsx",
    "data/inventario.xlsx"
]

def find_excel():
    """Buscar el archivo Excel en las ubicaciones posibles"""
    for path in EXCEL_PATHS:
        if os.path.exists(path):
            return path
    return None

def connect_db():
    """Conectar a PostgreSQL"""
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        print("✅ Conectado a PostgreSQL")
        return conn
    except Exception as e:
        print(f"❌ Error al conectar a la base de datos: {e}")
        print("   Verificá que PostgreSQL esté corriendo: docker-compose ps postgres")
        sys.exit(1)

def create_tables_if_not_exist(conn):
    """Crear tablas si no existen"""
    cursor = conn.cursor()
    
    # Crear tabla de categorías
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS categorias (
            id SERIAL PRIMARY KEY,
            nombre VARCHAR(100) UNIQUE NOT NULL,
            descripcion TEXT,
            activa BOOLEAN DEFAULT true,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """)
    
    # Crear tabla de productos
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS productos (
            id SERIAL PRIMARY KEY,
            codigo_barras VARCHAR(50) UNIQUE,
            nombre VARCHAR(200) NOT NULL,
            descripcion TEXT,
            precio DECIMAL(10,2) NOT NULL,
            stock_actual INTEGER NOT NULL DEFAULT 0,
            stock_minimo INTEGER DEFAULT 5,
            imagen_url VARCHAR(500),
            categoria_id INTEGER REFERENCES categorias(id),
            activo BOOLEAN DEFAULT true,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """)
    
    conn.commit()
    cursor.close()
    print("✅ Tablas verificadas/creadas")

def get_or_create_categoria(conn, nombre):
    """Obtener categoría existente o crear una nueva"""
    cursor = conn.cursor()
    
    # Buscar categoría existente
    cursor.execute("SELECT id FROM categorias WHERE nombre = %s", (nombre,))
    result = cursor.fetchone()
    
    if result:
        cursor.close()
        return result[0]
    
    # Crear nueva categoría
    cursor.execute(
        "INSERT INTO categorias (nombre) VALUES (%s) RETURNING id",
        (nombre,)
    )
    categoria_id = cursor.fetchone()[0]
    conn.commit()
    cursor.close()
    
    return categoria_id

def importar_productos(conn, excel_path):
    """Importar productos desde Excel"""
    try:
        # Leer Excel
        df = pd.read_excel(excel_path)
        print(f"📊 Archivo leído: {excel_path}")
        print(f"   Filas encontradas: {len(df)}")
        print(f"   Columnas: {list(df.columns)}")
        
        # Mostrar primeras filas
        print("\n📋 Primeras filas del Excel:")
        print(df.head().to_string())
        
        # Mapear columnas (buscar variaciones comunes)
        column_map = {}
        for col in df.columns:
            col_lower = col.lower().strip()
            if 'codigo' in col_lower or 'cod' in col_lower or 'barra' in col_lower:
                column_map['codigo'] = col
            elif 'nombre' in col_lower or 'producto' in col_lower or 'desc' in col_lower:
                column_map['nombre'] = col
            elif 'precio' in col_lower or 'pre' in col_lower:
                column_map['precio'] = col
            elif 'stock' in col_lower or 'cant' in col_lower or 'cantidad' in col_lower:
                column_map['stock'] = col
            elif 'categoria' in col_lower or 'rubro' in col_lower or 'grupo' in col_lower:
                column_map['categoria'] = col
            elif 'detalle' in col_lower or 'obs' in col_lower:
                column_map['descripcion'] = col
        
        print(f"\n🔍 Mapeo de columnas detectado:")
        for key, value in column_map.items():
            print(f"   {key}: {value}")
        
        # Validar columnas requeridas
        required = ['nombre', 'precio', 'stock']
        missing = [col for col in required if col not in column_map]
        if missing:
            print(f"\n❌ Faltan columnas requeridas: {missing}")
            print("   Asegurate de que el Excel tenga columnas: nombre, precio, stock")
            sys.exit(1)
        
        cursor = conn.cursor()
        inserted = 0
        updated = 0
        errors = 0
        
        print("\n⏳ Importando productos...")
        
        for index, row in df.iterrows():
            try:
                # Extraer valores
                nombre = str(row[column_map['nombre']]).strip()
                if not nombre or nombre == 'nan':
                    continue
                
                precio = float(row[column_map['precio']])
                stock = int(float(row[column_map['stock']]))
                
                codigo = None
                if 'codigo' in column_map:
                    codigo = str(row[column_map['codigo']]).strip()
                    if codigo == 'nan':
                        codigo = None
                
                descripcion = None
                if 'descripcion' in column_map:
                    descripcion = str(row[column_map['descripcion']]).strip()
                    if descripcion == 'nan':
                        descripcion = None
                
                categoria_nombre = None
                if 'categoria' in column_map:
                    categoria_nombre = str(row[column_map['categoria']]).strip()
                    if categoria_nombre == 'nan':
                        categoria_nombre = None
                
                # Obtener o crear categoría
                categoria_id = None
                if categoria_nombre:
                    categoria_id = get_or_create_categoria(conn, categoria_nombre)
                
                # Insertar o actualizar producto
                if codigo:
                    cursor.execute("""
                        INSERT INTO productos (codigo_barras, nombre, descripcion, precio, stock_actual, categoria_id)
                        VALUES (%s, %s, %s, %s, %s, %s)
                        ON CONFLICT (codigo_barras) DO UPDATE SET
                            nombre = EXCLUDED.nombre,
                            descripcion = EXCLUDED.descripcion,
                            precio = EXCLUDED.precio,
                            stock_actual = EXCLUDED.stock_actual,
                            categoria_id = EXCLUDED.categoria_id,
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id
                    """, (codigo, nombre, descripcion, precio, stock, categoria_id))
                else:
                    cursor.execute("""
                        INSERT INTO productos (nombre, descripcion, precio, stock_actual, categoria_id)
                        VALUES (%s, %s, %s, %s, %s)
                        RETURNING id
                    """, (nombre, descripcion, precio, stock, categoria_id))
                
                result = cursor.fetchone()
                if result:
                    inserted += 1
                
            except Exception as e:
                print(f"   ⚠️  Error en fila {index + 2}: {e}")
                errors += 1
                continue
        
        conn.commit()
        cursor.close()
        
        print(f"\n{'='*50}")
        print(f"✅ Importación completada!")
        print(f"   - Productos insertados: {inserted}")
        print(f"   - Errores: {errors}")
        print(f"{'='*50}")
        
    except FileNotFoundError:
        print(f"❌ No se encontró el archivo: {excel_path}")
        print("   Colocá tu archivo Excel en la carpeta 'scripts/' y renombralo a 'inventario.xlsx'")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error durante la importación: {e}")
        import traceback
        traceback.print_exc()
        conn.rollback()
        sys.exit(1)

def main():
    """Función principal"""
    print("="*50)
    print("📦 Importador de Productos - Repuestos Gastronómicos")
    print("="*50)
    
    # Buscar archivo Excel
    excel_path = find_excel()
    if not excel_path:
        print("❌ No se encontró el archivo Excel")
        print("   Buscando en:")
        for path in EXCEL_PATHS:
            print(f"   - {path}")
        print("\n   Colocá tu archivo Excel en una de esas ubicaciones")
        print("   y renombralo a 'inventario.xlsx'")
        sys.exit(1)
    
    # Conectar y crear tablas
    conn = connect_db()
    create_tables_if_not_exist(conn)
    
    # Importar
    importar_productos(conn, excel_path)
    
    conn.close()
    
    print("\n🚀 Próximos pasos:")
    print("   1. Abrí http://localhost:3000")
    print("   2. Iniciá sesión con admin / admin123")
    print("   3. Verificá que los productos aparecen en el inventario")

if __name__ == "__main__":
    main()
