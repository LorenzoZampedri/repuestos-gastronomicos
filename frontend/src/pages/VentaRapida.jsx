import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Plus, Minus, Trash2, ShoppingCart, ArrowLeft, CreditCard } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const VentaRapida = () => {
  const navigate = useNavigate();
  const [productos, setProductos] = useState([]);
  const [clientes, setClientes] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [carrito, setCarrito] = useState([]);
  const [clienteSeleccionado, setClienteSeleccionado] = useState('');
  const [metodoPago, setMetodoPago] = useState('EFECTIVO');
  const [procesando, setProcesando] = useState(false);
  const [busquedaCliente, setBusquedaCliente] = useState('');

  useEffect(() => {
    fetchProductos();
    fetchClientes();
  }, []);

  const fetchProductos = async () => {
    try {
      const response = await api.get('/api/productos');
      setProductos(response.data);
    } catch (error) {
      toast.error('Error al cargar productos');
    }
  };

  const fetchClientes = async () => {
    try {
      const response = await api.get('/api/clientes');
      setClientes(response.data);
    } catch (error) {
      console.error('Error:', error);
    }
  };

  const productosFiltrados = productos.filter(p => 
    p.stockActual > 0 &&
    (p.nombre.toLowerCase().includes(busqueda.toLowerCase()) ||
     p.codigoBarras?.toLowerCase().includes(busqueda.toLowerCase()))
  );

  const clientesFiltrados = clientes.filter(c =>
    c.nombre.toLowerCase().includes(busquedaCliente.toLowerCase()) ||
    c.telefono?.includes(busquedaCliente)
  );

  const agregarAlCarrito = (producto) => {
    const existente = carrito.find(item => item.producto.id === producto.id);
    
    if (existente) {
      if (existente.cantidad >= producto.stockActual) {
        toast.error('No hay suficiente stock');
        return;
      }
      setCarrito(carrito.map(item =>
        item.producto.id === producto.id
          ? { ...item, cantidad: item.cantidad + 1 }
          : item
      ));
    } else {
      setCarrito([...carrito, { producto, cantidad: 1 }]);
    }
  };

  const actualizarCantidad = (productoId, nuevaCantidad) => {
    if (nuevaCantidad <= 0) {
      setCarrito(carrito.filter(item => item.producto.id !== productoId));
      return;
    }
    
    const producto = carrito.find(item => item.producto.id === productoId)?.producto;
    if (producto && nuevaCantidad > producto.stockActual) {
      toast.error('Stock insuficiente');
      return;
    }
    
    setCarrito(carrito.map(item =>
      item.producto.id === productoId
        ? { ...item, cantidad: nuevaCantidad }
        : item
    ));
  };

  const eliminarDelCarrito = (productoId) => {
    setCarrito(carrito.filter(item => item.producto.id !== productoId));
  };

  const total = carrito.reduce((sum, item) => 
    sum + (item.producto.precio * item.cantidad), 0
  );

  const handleCompra = async () => {
    if (carrito.length === 0) {
      toast.error('El carrito está vacío');
      return;
    }

    setProcesando(true);
    try {
      const detalles = carrito.map(item => ({
        producto: { id: item.producto.id },
        cantidad: item.cantidad,
        precioUnitario: item.producto.precio
      }));

      const payload = {
        clienteId: clienteSeleccionado ? parseInt(clienteSeleccionado) : null,
        detalles,
        observaciones: ''
      };

      const response = await api.post('/api/ventas', payload);
      
      // Registrar pago
      if (response.data.id) {
        await api.post(`/api/ventas/${response.data.id}/pago`, {
          monto: total,
          metodoPago: metodoPago,
          moneda: 'ARS'
        });
      }

      toast.success('¡Venta registrada exitosamente!');
      navigate('/ventas');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Error al procesar la venta');
    } finally {
      setProcesando(false);
    }
  };

  return (
    <div className="h-[calc(100vh-8rem)] flex flex-col lg:flex-row gap-6">
      {/* Left: Product Selection */}
      <div className="flex-1 flex flex-col">
        <div className="flex items-center gap-4 mb-4">
          <button
            onClick={() => navigate('/ventas')}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <h1 className="text-2xl font-bold text-gray-900">Venta Rápida</h1>
        </div>

        {/* Search */}
        <div className="mb-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Buscar producto por nombre o código..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              className="input pl-10"
              autoFocus
            />
          </div>
        </div>

        {/* Products Grid */}
        <div className="flex-1 overflow-y-auto grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
          {productosFiltrados.map((producto) => (
            <button
              key={producto.id}
              onClick={() => agregarAlCarrito(producto)}
              className="card text-left hover:shadow-md hover:border-primary-300 transition-all"
            >
              <div className="w-full h-20 bg-gray-100 rounded-lg flex items-center justify-center mb-2">
                {producto.imagenUrl ? (
                  <img 
                    src={producto.imagenUrl} 
                    alt={producto.nombre}
                    className="w-full h-full object-cover rounded-lg"
                  />
                ) : (
                  <ShoppingCart className="w-8 h-8 text-gray-300" />
                )}
              </div>
              <p className="font-medium text-gray-900 text-sm truncate">{producto.nombre}</p>
              <p className="text-xs text-gray-500">{producto.codigoBarras}</p>
              <div className="flex items-center justify-between mt-2">
                <span className="font-bold text-primary-800">
                  ${producto.precio?.toLocaleString('es-AR')}
                </span>
                <span className={`text-xs ${producto.stockActual <= 5 ? 'text-red-500' : 'text-gray-500'}`}>
                  Stock: {producto.stockActual}
                </span>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Right: Cart */}
      <div className="w-full lg:w-96 bg-white rounded-xl shadow-sm border border-gray-200 flex flex-col">
        {/* Cart Header */}
        <div className="p-4 border-b border-gray-200">
          <h2 className="font-semibold text-gray-900 flex items-center gap-2">
            <ShoppingCart className="w-5 h-5" />
            Carrito ({carrito.length})
          </h2>
        </div>

        {/* Cart Items */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {carrito.length === 0 ? (
            <div className="text-center py-8 text-gray-500">
              <ShoppingCart className="w-12 h-12 mx-auto text-gray-300 mb-2" />
              <p>El carrito está vacío</p>
              <p className="text-sm">Seleccioná un producto</p>
            </div>
          ) : (
            carrito.map((item) => (
              <div key={item.producto.id} className="flex gap-3 bg-gray-50 rounded-lg p-3">
                <div className="flex-1">
                  <p className="font-medium text-gray-900 text-sm">{item.producto.nombre}</p>
                  <p className="text-sm text-gray-500">
                    ${item.producto.precio?.toLocaleString('es-AR')} c/u
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => actualizarCantidad(item.producto.id, item.cantidad - 1)}
                    className="w-7 h-7 flex items-center justify-center bg-white border rounded-lg hover:bg-gray-100"
                  >
                    <Minus className="w-3 h-3" />
                  </button>
                  <span className="w-8 text-center font-medium">{item.cantidad}</span>
                  <button
                    onClick={() => actualizarCantidad(item.producto.id, item.cantidad + 1)}
                    className="w-7 h-7 flex items-center justify-center bg-white border rounded-lg hover:bg-gray-100"
                  >
                    <Plus className="w-3 h-3" />
                  </button>
                </div>
                <div className="text-right">
                  <p className="font-semibold text-gray-900">
                    ${(item.producto.precio * item.cantidad).toLocaleString('es-AR')}
                  </p>
                  <button
                    onClick={() => eliminarDelCarrito(item.producto.id)}
                    className="text-red-500 hover:text-red-600 text-xs"
                  >
                    Eliminar
                  </button>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Cart Footer */}
        <div className="p-4 border-t border-gray-200 space-y-4">
          {/* Client Selection */}
          <div>
            <label className="label">Cliente (opcional)</label>
            <input
              type="text"
              placeholder="Buscar cliente..."
              value={busquedaCliente}
              onChange={(e) => setBusquedaCliente(e.target.value)}
              className="input text-sm mb-2"
            />
            <select
              value={clienteSeleccionado}
              onChange={(e) => setClienteSeleccionado(e.target.value)}
              className="input text-sm"
            >
              <option value="">Venta anónima</option>
              {clientesFiltrados.map(cliente => (
                <option key={cliente.id} value={cliente.id}>
                  {cliente.nombre}
                </option>
              ))}
            </select>
          </div>

          {/* Payment Method */}
          <div>
            <label className="label">Método de pago</label>
            <select
              value={metodoPago}
              onChange={(e) => setMetodoPago(e.target.value)}
              className="input"
            >
              <option value="EFECTIVO">Efectivo</option>
              <option value="TARJETA">Tarjeta</option>
              <option value="TRANSFERENCIA">Transferencia</option>
            </select>
          </div>

          {/* Total */}
          <div className="bg-gray-50 rounded-lg p-4">
            <div className="flex justify-between text-lg font-bold">
              <span>Total</span>
              <span className="text-primary-800">${total.toLocaleString('es-AR')}</span>
            </div>
          </div>

          {/* Checkout Button */}
          <button
            onClick={handleCompra}
            disabled={procesando || carrito.length === 0}
            className="btn-primary w-full flex items-center justify-center gap-2"
          >
            {procesando ? (
              <>
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                Procesando...
              </>
            ) : (
              <>
                <CreditCard className="w-5 h-5" />
                Confirmar Venta
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default VentaRapida;
