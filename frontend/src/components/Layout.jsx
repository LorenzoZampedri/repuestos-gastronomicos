import React, { useState, useEffect } from 'react';
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import PageTransition from './PageTransition';
import CambiarPasswordModal from './CambiarPasswordModal';
import Avatar from './Avatar';
import SelectorFoto from './SelectorFoto';
import { 
  LayoutDashboard, 
  Package, 
  ShoppingCart, 
  Users, 
  CreditCard,
  Settings,
  Menu,
  X,
  LogOut,
  UserCog,
  KeyRound,
  Camera,
  ClipboardList,
  ScrollText,
  Percent
} from 'lucide-react';

const ROLE_LABELS = {
  ADMIN: 'Administrador',
  EMPLEADO: 'Empleado',
  CLIENTE: 'Cliente',
};

const Layout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [fotoOpen, setFotoOpen] = useState(false);
  const [pendingOrders, setPendingOrders] = useState(0);
  const { user, logout, isAdmin, isCliente, actualizarFoto, enVista } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    let activo = true;
    const fetchPendientes = async () => {
      try {
        const response = await api.get('/api/pedidos/pendientes/count');
        if (activo) setPendingOrders(response.data.count || 0);
      } catch (error) {
        if (activo) setPendingOrders(0);
      }
    };

    fetchPendientes();
    window.addEventListener('pedidos-actualizados', fetchPendientes);

    return () => {
      activo = false;
      window.removeEventListener('pedidos-actualizados', fetchPendientes);
    };
  }, [location.pathname]);

  const handleLogout = () => {
    if (enVista) {
      logout();
      return;
    }
    logout();
    navigate('/login');
  };

  const navigation = [
    ...(isAdmin() ? [{ name: 'Dashboard', href: '/', icon: LayoutDashboard }] : []),
    { name: 'Productos', href: '/productos', icon: Package },
    { name: 'Pedidos', href: '/pedidos', icon: ClipboardList, badge: pendingOrders },
    { name: 'Ventas', href: '/ventas', icon: ShoppingCart, end: true },
    { name: 'Venta Rápida', href: '/ventas/nueva', icon: ShoppingCart },
    { name: 'Clientes', href: '/clientes', icon: Users },
    ...(isAdmin() ? [
      { name: 'Precios', href: '/precios', icon: Percent },
      { name: 'Ctas. Corrientes', href: '/cuentas-corrientes', icon: CreditCard },
      { name: 'Usuarios', href: '/usuarios', icon: UserCog },
      { name: 'Logs', href: '/logs', icon: ScrollText },
      { name: 'Configuración', href: '/configuracion', icon: Settings },
    ] : []),
  ];

  return (
    <div className="min-h-screen bg-gray-50" style={{ paddingTop: 'var(--banner-h, 0px)' }}>
      {/* Mobile sidebar backdrop */}
      {sidebarOpen && (
        <div 
          className="fixed inset-0 bg-gray-900/50 z-40 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed left-0 bottom-0 z-50 w-64 bg-white border-r border-gray-200 transform transition-transform duration-300 ease-in-out lg:translate-x-0 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}`}
        style={{ top: 'var(--banner-h, 0px)' }}
      >
        <div className="flex flex-col h-full">
          {/* Logo */}
          <div className="flex items-center justify-between h-16 px-6 border-b border-gray-200">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 bg-primary-800 rounded-lg flex items-center justify-center">
                <Package className="w-5 h-5 text-white" />
              </div>
              <span className="font-bold text-gray-900">Repuestos</span>
            </div>
            <button 
              onClick={() => setSidebarOpen(false)}
              className="lg:hidden p-1 rounded-lg hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
              aria-label="Cerrar menú"
            >
              <X className="w-5 h-5" aria-hidden="true" />
            </button>
          </div>

          {/* Navigation */}
          <nav className="flex-1 px-4 py-4 space-y-1 overflow-y-auto">
            {navigation.map((item) => (
              <NavLink
                key={item.name}
                to={item.href}
                end={item.href === '/' || item.end}
                className={({ isActive }) => 
                  `sidebar-link ${isActive ? 'active' : ''}`
                }
                onClick={() => setSidebarOpen(false)}
              >
                <item.icon className="w-5 h-5" />
                <span className="flex-1">{item.name}</span>
                {item.badge > 0 && (
                  <span className="min-w-5 h-5 px-1 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold">
                    {item.badge}
                  </span>
                )}
              </NavLink>
            ))}
          </nav>

          {/* User section */}
          <div className="p-4 border-t border-gray-200">
            <div className="flex items-center gap-3">
              {enVista ? (
                <Avatar fotoUrl={user?.fotoUrl} nombre={user?.nombre} size="md" />
              ) : (
                <button
                  type="button"
                  onClick={() => setFotoOpen(true)}
                  className="relative group rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                  title="Cambiar foto de perfil"
                  aria-label="Cambiar foto de perfil"
                >
                  <Avatar fotoUrl={user?.fotoUrl} nombre={user?.nombre} size="md" />
                  <span className="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                    <Camera className="w-4 h-4 text-white" aria-hidden="true" />
                  </span>
                </button>
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">
                  {user?.nombre}
                </p>
                <p className="text-xs text-gray-500 truncate">
                  {ROLE_LABELS[user?.rol] || user?.rol}
                </p>
              </div>
              {!enVista && (
                <button
                  onClick={() => setPasswordOpen(true)}
                  className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                  title="Cambiar contraseña"
                  aria-label="Cambiar contraseña"
                >
                  <KeyRound className="w-5 h-5" aria-hidden="true" />
                </button>
              )}
              <button
                onClick={handleLogout}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                title={enVista ? 'Salir de la vista' : 'Cerrar sesión'}
                aria-label={enVista ? 'Salir de la vista' : 'Cerrar sesión'}
              >
                <LogOut className="w-5 h-5" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="lg:pl-64">
        {/* Top bar */}
        <header
          className="sticky z-30 h-16 bg-white border-b border-gray-200 flex items-center px-4 lg:px-6"
          style={{ top: 'var(--banner-h, 0px)' }}
        >
          <button
            onClick={() => setSidebarOpen(true)}
            className="lg:hidden p-2 text-gray-600 hover:bg-gray-100 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="Abrir menú"
            aria-expanded={sidebarOpen}
          >
            <Menu className="w-6 h-6" aria-hidden="true" />
          </button>
          
          <div className="flex-1" />
          
          <div className="flex items-center gap-4">
            <NavLink 
              to="/catalogo" 
              target="_blank"
              className="text-sm text-gray-600 hover:text-primary-800"
            >
              Ver Catálogo Público
            </NavLink>
          </div>
        </header>

        {/* Page content */}
        <main className="p-4 lg:p-6">
          <PageTransition>
            <Outlet />
          </PageTransition>
        </main>
      </div>

      {!enVista && (
        <>
          <CambiarPasswordModal open={passwordOpen} onClose={() => setPasswordOpen(false)} />

          <SelectorFoto
            open={fotoOpen}
            onClose={() => setFotoOpen(false)}
            nombre={user?.nombre}
            fotoUrl={user?.fotoUrl}
            endpoint="/api/auth/foto"
            compartida={isCliente()}
            onActualizada={actualizarFoto}
          />
        </>
      )}
    </div>
  );
};

export default Layout;
