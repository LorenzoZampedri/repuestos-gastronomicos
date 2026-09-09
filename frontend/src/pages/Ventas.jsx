import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Search, Plus, Eye, Calendar, Filter, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const Ventas = () => {
  const [ventas, setVentas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [fechaInicio, setFechaInicio] = useState('');
  const [fechaFin, setFechaFin] = useState('');
  const [filtroActivo, setFiltroActivo] = useState('mes');
  const [ventaSeleccionada, setVentaSeleccionada] = useState(null);

  useEffect(() => {
    aplicarFiltro('mes');
  }, []);

  useEffect(() => {
    if (fechaInicio && fechaFin) fetchVentas();
  }, [fechaInicio, fechaFin]);

  const hoy = () => new Date().toISOString().split('T')[0];

  const ayer = () => {
    const d = new Date(); d.setDate(d.getDate() - 1);
    return d.toISOString().split('T')[0];
  };

  const semana = () => {
    const d = new Date(); d.setDate(d.getDate() - 7);
    return d.toISOString().split('T')[0];
  };

  const mes = () => {
    const d = new Date(); d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
  };

  const aplicarFiltro = (tipo) => {
    setFiltroActivo(tipo);
    const fin = hoy();
    let inicio;
    switch (tipo) {
      case 'hoy': inicio = fin; break;
      case 'ayer': inicio = ayer(); break;
      case 'semana': inicio = semana(); break;
      case 'mes': inicio = mes(); break;
      case 'todo': inicio = '2020-01-01'; break;
      default: inicio = mes();
    }
    setFechaInicio(inicio);
    setFechaFin(fin);
  };

  const fetchVentas = async () => {
    setLoading(true);
    try {
      const inicio = `${fechaInicio}T00:00:00`;
      const fin = `${fechaFin}T23:59:59`;
      const response = await api.get(`/api/ventas?inicio=${inicio}&fin=${fin}`);
      setVentas(response.data);
    } catch (error) {
      toast.error('Error al cargar ventas');
    } finally {
      setLoading(false);
    }
  };

  const getEstadoBadge = (estado) => {
    const styles = {
      PENDIENTE: 'bg-yellow-100 text-yellow-800',
      PARCIAL: 'bg-orange-100 text-orange-800',
      PAGADA: 'bg-green-100 text-green-800',
      ANULADA: 'bg-gray-100 text-gray-800'
    };
    return (
      <span className={`px-2 py-1 text-xs font-medium rounded-full ${styles[estado] || styles.PENDIENTE}`}>
        {estado}
      </span>
    );
  };

  const totalVentas = ventas.reduce((sum, v) => sum + (v.total || 0), 0);

  const anularVenta = async (id) => {
    if (!confirm('¿Anular esta venta? Se devolverá el stock.')) return;
    try {
      await api.put(`/api/ventas/${id}/anular`);
      toast.success('Venta anulada');
      fetchVentas();
      if (ventaSeleccionada?.id === id) setVentaSeleccionada(null);
    } catch (error) {
      toast.error('Error al anular venta');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Ventas</h1>
          <p className="text-gray-500">Historial de ventas realizadas</p>
        </div>
        <Link to="/ventas/nueva" className="btn-primary flex items-center gap-2">
          <Plus className="w-5 h-5" />
          Nueva Venta
        </Link>
      </div>

      {/* Filters */}
      <div className="card">
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            {[
              { key: 'todo', label: 'Todas' },
              { key: 'mes', label: 'Último mes' },
              { key: 'semana', label: 'Última semana' },
              { key: 'ayer', label: 'Ayer' },
              { key: 'hoy', label: 'Hoy' },
            ].map((f) => (
              <button
                key={f.key}
                onClick={() => aplicarFiltro(f.key)}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  filtroActivo === f.key
                    ? 'bg-primary-800 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
          <div className="flex flex-col sm:flex-row gap-4 items-end">
            <div className="flex-1">
              <label className="label">Fecha inicio</label>
              <input type="date" value={fechaInicio} onChange={(e) => { setFechaInicio(e.target.value); setFiltroActivo(''); }} className="input" />
            </div>
            <div className="flex-1">
              <label className="label">Fecha fin</label>
              <input type="date" value={fechaFin} onChange={(e) => { setFechaFin(e.target.value); setFiltroActivo(''); }} className="input" />
            </div>
          </div>
          <div className="text-right">
            <p className="text-sm text-gray-500">Total del período</p>
            <p className="text-2xl font-bold text-primary-800">
              ${totalVentas.toLocaleString('es-AR')}
            </p>
          </div>
        </div>
      </div>

      {/* Sales Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">#</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Fecha</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Cliente</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Vendedor</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Total</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Estado</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {ventas.map((venta) => (
                  <tr key={venta.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-3 px-4 text-sm font-mono text-gray-600">
                      {venta.id}
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2 text-sm text-gray-600">
                        <Calendar className="w-4 h-4 text-gray-400" />
                        {new Date(venta.fecha).toLocaleDateString('es-AR')}
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-sm text-gray-900">
                        {venta.cliente?.nombre || 'Venta anónima'}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-sm text-gray-600">
                        {venta.usuario?.nombre || '-'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <span className="font-semibold text-gray-900">
                        ${venta.total?.toLocaleString('es-AR')}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      {getEstadoBadge(venta.estado)}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => setVentaSeleccionada(venta)}
                          className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                        {venta.estado !== 'ANULADA' && (
                          <button
                            onClick={() => anularVenta(venta.id)}
                            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                            title="Anular venta"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {ventas.length === 0 && (
                  <tr>
                    <td colSpan="7" className="py-12 text-center text-gray-500">
                      No se encontraron ventas en este período
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Sale Detail Modal */}
      {ventaSeleccionada && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full max-h-[90vh] overflow-auto">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold">Detalle de Venta #{ventaSeleccionada.id}</h3>
                <button
                  onClick={() => setVentaSeleccionada(null)}
                  className="p-2 text-gray-400 hover:text-gray-600"
                >
                  ✕
                </button>
              </div>
              
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-gray-500">Fecha</p>
                    <p className="font-medium">
                      {new Date(ventaSeleccionada.fecha).toLocaleString('es-AR')}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Estado</p>
                    {getEstadoBadge(ventaSeleccionada.estado)}
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Cliente</p>
                    <p className="font-medium">
                      {ventaSeleccionada.cliente?.nombre || 'Anónimo'}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">Vendedor</p>
                    <p className="font-medium">
                      {ventaSeleccionada.usuario?.nombre}
                    </p>
                  </div>
                </div>
                
                {ventaSeleccionada.detalles && ventaSeleccionada.detalles.length > 0 && (
                  <div>
                    <p className="text-sm font-medium text-gray-700 mb-2">Productos</p>
                    <div className="space-y-2">
                      {ventaSeleccionada.detalles.map((detalle, idx) => (
                        <div key={idx} className="flex justify-between text-sm bg-gray-50 p-2 rounded">
                          <span>{detalle.producto?.nombre} x{detalle.cantidad}</span>
                          <span className="font-medium">
                            ${detalle.subtotal?.toLocaleString('es-AR')}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                
                <div className="border-t pt-4">
                  <div className="flex justify-between items-center">
                    <span className="text-lg font-bold">Total</span>
                    <span className="text-2xl font-bold text-primary-800">
                      ${ventaSeleccionada.total?.toLocaleString('es-AR')}
                    </span>
                  </div>
                  {ventaSeleccionada.estado !== 'ANULADA' && (
                    <button
                      onClick={() => anularVenta(ventaSeleccionada.id)}
                      className="mt-4 w-full flex items-center justify-center gap-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                      Anular venta
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Ventas;
