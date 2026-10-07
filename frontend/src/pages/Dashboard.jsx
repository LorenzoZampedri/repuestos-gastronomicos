import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { 
  Package, 
  ShoppingCart, 
  Users, 
  AlertTriangle,
  TrendingUp,
  ArrowUpRight,
  ArrowDownRight
} from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import api from '../services/api';

const Dashboard = () => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [metricasActivas, setMetricasActivas] = useState(['monto']);

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      const response = await api.get('/api/dashboard');
      setStats(response.data);
    } catch (error) {
      console.error('Error fetching stats:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
      </div>
    );
  }

  const statCards = [
    {
      name: 'Productos',
      value: stats?.totalProductos || 0,
      icon: Package,
      color: 'bg-blue-500',
      link: '/productos'
    },
    {
      name: 'Ventas del Día',
      value: `$${(stats?.ventasDelDia || 0).toLocaleString('es-AR')}`,
      icon: ShoppingCart,
      color: 'bg-green-500',
      link: '/ventas'
    },
    {
      name: 'Clientes con Deuda',
      value: stats?.clientesConDeuda || 0,
      icon: Users,
      color: 'bg-orange-500',
      link: '/cuentas-corrientes'
    },
    {
      name: 'Stock Bajo',
      value: stats?.productosStockBajo || 0,
      icon: AlertTriangle,
      color: 'bg-red-500',
      link: '/productos',
      alert: (stats?.productosStockBajo || 0) > 0
    }
  ];

  const chartData = stats?.ventasSemana || [];

  const formatCurrency = (value) => `$${Number(value || 0).toLocaleString('es-AR')}`;
  const formatNumber = (value) => Number(value || 0).toLocaleString('es-AR');

  const metricas = {
    monto: { label: 'Monto', dataKey: 'monto', color: '#1e40af', format: formatCurrency },
    ventas: { label: 'Ventas', dataKey: 'cantidadVentas', color: '#16a34a', format: formatNumber },
    productos: { label: 'Productos', dataKey: 'cantidadProductos', color: '#ea580c', format: formatNumber },
  };

  const toggleMetrica = (key) => {
    setMetricasActivas((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
  };

  const formatearTicket = (value, name) => {
    const cfg = Object.values(metricas).find((m) => m.label === name);
    return [cfg ? cfg.format(value) : value, name];
  };

  const hayMonto = metricasActivas.includes('monto');
  const hayConteo = metricasActivas.some((key) => key !== 'monto');
  const usaDobleEje = hayMonto && hayConteo;

  const yAxisIdFor = (key) => (usaDobleEje && key !== 'monto' ? 'right' : 'left');
  const leftFormatter = hayMonto ? formatCurrency : formatNumber;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500">Resumen general del sistema</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((stat) => (
          <Link
            key={stat.name}
            to={stat.link}
            className={`card hover:shadow-md transition-shadow ${stat.alert ? 'ring-2 ring-red-500' : ''}`}
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{stat.name}</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">{stat.value}</p>
              </div>
              <div className={`w-12 h-12 ${stat.color} rounded-xl flex items-center justify-center`}>
                <stat.icon className="w-6 h-6 text-white" />
              </div>
            </div>
          </Link>
        ))}
      </div>

      {/* Charts and Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Sales Chart */}
        <div className="card">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h3 className="text-lg font-semibold text-gray-900">Ventas de la Semana</h3>
            <div className="flex rounded-lg bg-gray-100 p-1">
              {Object.entries(metricas).map(([key, cfg]) => {
                const activa = metricasActivas.includes(key);
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => toggleMetrica(key)}
                    className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
                      activa
                        ? 'bg-white text-gray-900 shadow-sm'
                        : 'text-gray-400 hover:text-gray-600'
                    }`}
                  >
                    <span
                      className="inline-block w-2.5 h-2.5 rounded-full mr-1.5 align-middle"
                      style={{ backgroundColor: activa ? cfg.color : '#d1d5db' }}
                    />
                    {cfg.label}
                  </button>
                );
              })}
            </div>
          </div>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis dataKey="name" stroke="#6b7280" fontSize={12} />
                <YAxis
                  yAxisId="left"
                  stroke="#6b7280"
                  fontSize={12}
                  tickFormatter={leftFormatter}
                />
                {usaDobleEje && (
                  <YAxis
                    yAxisId="right"
                    orientation="right"
                    stroke="#6b7280"
                    fontSize={12}
                    allowDecimals={false}
                    tickFormatter={formatNumber}
                  />
                )}
                <Tooltip 
                  formatter={formatearTicket}
                  contentStyle={{ 
                    backgroundColor: 'white', 
                    border: '1px solid #e5e7eb',
                    borderRadius: '8px'
                  }}
                />
                <Legend />
                {Object.entries(metricas)
                  .filter(([key]) => metricasActivas.includes(key))
                  .map(([key, cfg]) => (
                    <Bar
                      key={key}
                      dataKey={cfg.dataKey}
                      name={cfg.label}
                      fill={cfg.color}
                      yAxisId={yAxisIdFor(key)}
                      radius={[4, 4, 0, 0]}
                    />
                  ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Quick Actions */}
        <div className="card">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Acciones Rápidas</h3>
          <div className="space-y-3">
            <Link 
              to="/ventas/nueva"
              className="flex items-center gap-3 p-4 bg-primary-50 rounded-xl hover:bg-primary-100 transition-colors"
            >
              <div className="w-10 h-10 bg-primary-800 rounded-lg flex items-center justify-center">
                <ShoppingCart className="w-5 h-5 text-white" />
              </div>
              <div>
                <p className="font-medium text-gray-900">Nueva Venta</p>
                <p className="text-sm text-gray-500">Registrar venta rápida</p>
              </div>
              <ArrowUpRight className="w-5 h-5 text-gray-400 ml-auto" />
            </Link>
            
            <Link 
              to="/productos/nuevo"
              className="flex items-center gap-3 p-4 bg-gray-50 rounded-xl hover:bg-gray-100 transition-colors"
            >
              <div className="w-10 h-10 bg-gray-600 rounded-lg flex items-center justify-center">
                <Package className="w-5 h-5 text-white" />
              </div>
              <div>
                <p className="font-medium text-gray-900">Nuevo Producto</p>
                <p className="text-sm text-gray-500">Agregar producto al inventario</p>
              </div>
              <ArrowUpRight className="w-5 h-5 text-gray-400 ml-auto" />
            </Link>
            
            <Link 
              to="/cuentas-corrientes"
              className="flex items-center gap-3 p-4 bg-gray-50 rounded-xl hover:bg-gray-100 transition-colors"
            >
              <div className="w-10 h-10 bg-orange-500 rounded-lg flex items-center justify-center">
                <Users className="w-5 h-5 text-white" />
              </div>
              <div>
                <p className="font-medium text-gray-900">Cuentas Corrientes</p>
                <p className="text-sm text-gray-500">Ver clientes con saldo</p>
              </div>
              <ArrowUpRight className="w-5 h-5 text-gray-400 ml-auto" />
            </Link>
          </div>
        </div>
      </div>

      {/* Low Stock Alert */}
      {stats?.productosStockBajo > 0 && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-red-100 rounded-lg flex items-center justify-center">
              <AlertTriangle className="w-5 h-5 text-red-600" />
            </div>
            <div>
              <p className="font-medium text-red-800">Stock Bajo</p>
              <p className="text-sm text-red-600">
                Hay {stats.productosStockBajo} producto(s) con stock por debajo del mínimo.
              </p>
            </div>
            <Link 
              to="/productos"
              className="ml-auto text-sm font-medium text-red-600 hover:text-red-700"
            >
              Ver productos →
            </Link>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
