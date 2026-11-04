import React, { useState, useEffect, useRef } from 'react';
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
import toast from 'react-hot-toast';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import gsap from 'gsap';
import api from '../services/api';
import { formatMoney, formatNumber } from '../utils/format';

const fmtArs = (value) => formatMoney(value, 'ARS');
const fmtInt = (value) => formatNumber(Math.round(Number(value) || 0));

const CountUp = ({ value, format }) => {
  const ref = useRef(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const target = Number(value) || 0;
    const mm = gsap.matchMedia();

    mm.add('(prefers-reduced-motion: reduce)', () => {
      el.textContent = format(target);
    });

    mm.add('(prefers-reduced-motion: no-preference)', () => {
      const counter = { n: 0 };
      const tween = gsap.to(counter, {
        n: target,
        duration: 0.9,
        ease: 'power2.out',
        onUpdate: () => { el.textContent = format(counter.n); },
      });
      return () => tween.kill();
    });

    return () => mm.revert();
  }, [value, format]);

  return <span ref={ref}>{format(value)}</span>;
};

const Dashboard = () => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [metricasActivas, setMetricasActivas] = useState(['monto']);
  const statsGridRef = useRef(null);
  const [dias, setDias] = useState(7);
  const [serie, setSerie] = useState([]);
  const [totalesPeriodo, setTotalesPeriodo] = useState(null);
  const [cargandoGrafico, setCargandoGrafico] = useState(true);
  const ultimoDiasRef = useRef(7);

  useEffect(() => {
    fetchStats();
  }, []);

  useEffect(() => {
    if (loading || !statsGridRef.current) return;
    const cards = statsGridRef.current.querySelectorAll('.stat-card');
    if (!cards.length) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const tween = gsap.fromTo(
      cards,
      { opacity: 0, y: 20 },
      { opacity: 1, y: 0, duration: 0.55, stagger: 0.08, ease: 'power3.out', overwrite: true }
    );

    return () => tween.kill();
  }, [loading]);

  useEffect(() => {
    let cancelado = false;

    const fetchVentas = async () => {
      setCargandoGrafico(true);
      try {
        const response = await api.get('/api/dashboard/ventas', { params: { dias } });
        if (cancelado) return;
        setSerie(response.data.serie || []);
        setTotalesPeriodo(response.data.totales || null);
        ultimoDiasRef.current = response.data.dias || dias;
      } catch (error) {
        if (cancelado) return;
        console.error('Error fetching ventas del período:', error);
        toast.error('No se pudieron cargar las ventas del período');
        setDias(ultimoDiasRef.current);
      } finally {
        if (!cancelado) {
          setCargandoGrafico(false);
        }
      }
    };

    fetchVentas();

    return () => {
      cancelado = true;
    };
  }, [dias]);

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
      format: fmtInt,
      icon: Package,
      color: 'bg-blue-500',
      link: '/productos'
    },
    {
      name: 'Ventas del Día',
      value: stats?.ventasDelDia || 0,
      format: fmtArs,
      icon: ShoppingCart,
      color: 'bg-green-500',
      link: '/ventas'
    },
    {
      name: 'Clientes con Deuda',
      value: stats?.clientesConDeuda || 0,
      format: fmtInt,
      icon: Users,
      color: 'bg-orange-500',
      link: '/cuentas-corrientes'
    },
    {
      name: 'Stock Bajo',
      value: stats?.productosStockBajo || 0,
      format: fmtInt,
      icon: AlertTriangle,
      color: 'bg-red-500',
      link: '/productos?stock=bajo',
      alert: (stats?.productosStockBajo || 0) > 0
    }
  ];

  const chartData = serie;

  const metricas = {
    monto: { label: 'Monto', dataKey: 'monto', color: '#1e40af', format: fmtArs },
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
  const leftFormatter = hayMonto ? fmtArs : formatNumber;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500">Resumen general del sistema</p>
      </div>

      {/* Stats Grid */}
      <div ref={statsGridRef} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((stat) => (
          <Link
            key={stat.name}
            to={stat.link}
            className={`stat-card card hover:shadow-md transition-shadow focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${stat.alert ? 'ring-2 ring-red-500' : ''}`}
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{stat.name}</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">
                  <CountUp value={stat.value} format={stat.format} />
                </p>
              </div>
              <div className={`w-12 h-12 ${stat.color} rounded-xl flex items-center justify-center`}>
                <stat.icon className="w-6 h-6 text-white" aria-hidden="true" />
              </div>
            </div>
          </Link>
        ))}
      </div>

      {/* Charts and Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Sales Chart */}
        <div className="card">
          <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
            <div>
              <h3 className="text-lg font-semibold text-gray-900">Ventas · últimos {dias} días</h3>
              <p className="text-xs text-gray-500 mt-1">
                Total del período: {fmtArs(totalesPeriodo?.monto)} · {formatNumber(totalesPeriodo?.cantidadVentas)} ventas · {formatNumber(totalesPeriodo?.cantidadProductos)} productos
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <select
                aria-label="Período del gráfico"
                className="input w-auto py-1.5 text-sm"
                value={dias}
                onChange={(e) => setDias(Number(e.target.value))}
              >
                <option value={7}>Últimos 7 días</option>
                <option value={30}>Últimos 30 días</option>
                <option value={90}>Últimos 90 días</option>
              </select>
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
          </div>
          <div className="relative h-64">
            <div className={`h-full transition-opacity ${cargandoGrafico ? 'opacity-50' : ''}`}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                  <XAxis
                    dataKey="name"
                    stroke="#6b7280"
                    fontSize={12}
                    interval={dias === 7 ? 0 : dias === 30 ? 3 : 1}
                    minTickGap={8}
                  />
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
                    labelFormatter={(label, payload) => payload?.[0]?.payload?.etiqueta || label}
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
            {cargandoGrafico && (
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-800"></div>
              </div>
            )}
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
              to="/productos?stock=bajo"
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
