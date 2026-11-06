import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth, homePorRol } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import { CurrencyProvider } from './context/CurrencyContext';

// Layout
import Layout from './components/Layout';
import PublicLayout from './components/PublicLayout';
import PageTransition from './components/PageTransition';

// Pages
import Login from './pages/Login';
import Registro from './pages/Registro';
import Dashboard from './pages/Dashboard';
import Productos from './pages/Productos';
import ProductoForm from './pages/ProductoForm';
import Ventas from './pages/Ventas';
import VentaRapida from './pages/VentaRapida';
import Clientes from './pages/Clientes';
import ClienteForm from './pages/ClienteForm';
import CuentasCorrientes from './pages/CuentasCorrientes';
import Catalogo from './pages/Catalogo';
import Configuracion from './pages/Configuracion';
import Usuarios from './pages/Usuarios';
import MisPedidos from './pages/MisPedidos';
import Pedidos from './pages/Pedidos';

// Protected Route Component
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { user, loading } = useAuth();
  
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-800"></div>
      </div>
    );
  }
  
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  
  if (allowedRoles && !allowedRoles.includes(user.rol)) {
    return <Navigate to={homePorRol(user.rol)} replace />;
  }
  
  return children;
};

// Public Route (redirect if logged in)
const PublicRoute = ({ children }) => {
  const { user, loading } = useAuth();
  
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-800"></div>
      </div>
    );
  }
  
  if (user) {
    return <Navigate to={homePorRol(user.rol)} replace />;
  }
  
  return children;
};

const RoleHome = () => {
  const { user } = useAuth();
  return user?.rol === 'ADMIN' ? <Dashboard /> : <Navigate to={homePorRol(user?.rol)} replace />;
};

const CatchAll = () => {
  const { user } = useAuth();
  return <Navigate to={user ? homePorRol(user.rol) : '/catalogo'} replace />;
};

function App() {
  return (
    <AuthProvider>
      <CurrencyProvider>
      <CartProvider>
        <Router>
        <Toaster 
          position="top-right"
          toastOptions={{
            duration: 4000,
            style: {
              background: '#363636',
              color: '#fff',
            },
            success: {
              duration: 3000,
              iconTheme: {
                primary: '#10b981',
                secondary: '#fff',
              },
            },
            error: {
              duration: 4000,
              iconTheme: {
                primary: '#ef4444',
                secondary: '#fff',
              },
            },
          }}
        />
        <Routes>
          {/* Public Routes */}
          <Route path="/login" element={
            <PublicRoute>
              <PageTransition>
                <Login />
              </PageTransition>
            </PublicRoute>
          } />

          <Route path="/registro" element={
            <PublicRoute>
              <PageTransition>
                <Registro />
              </PageTransition>
            </PublicRoute>
          } />
          
          <Route path="/catalogo" element={
            <PublicLayout>
              <Catalogo />
            </PublicLayout>
          } />

          <Route path="/mis-pedidos" element={
            <ProtectedRoute allowedRoles={['CLIENTE']}>
              <PublicLayout>
                <MisPedidos />
              </PublicLayout>
            </ProtectedRoute>
          } />
          
          {/* Protected Routes */}
          <Route path="/" element={
            <ProtectedRoute allowedRoles={['ADMIN', 'EMPLEADO']}>
              <Layout />
            </ProtectedRoute>
          }>
            <Route index element={<RoleHome />} />
            <Route path="productos" element={<Productos />} />
            <Route path="productos/nuevo" element={<ProductoForm />} />
            <Route path="productos/:id" element={<ProductoForm />} />
            <Route path="pedidos" element={<Pedidos />} />
            <Route path="ventas" element={<Ventas />} />
            <Route path="ventas/nueva" element={<VentaRapida />} />
            <Route path="clientes" element={<Clientes />} />
            <Route path="clientes/nuevo" element={<ClienteForm />} />
            <Route path="clientes/:id" element={<ClienteForm />} />
            <Route path="cuentas-corrientes" element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <CuentasCorrientes />
              </ProtectedRoute>
            } />
            <Route path="usuarios" element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <Usuarios />
              </ProtectedRoute>
            } />
            <Route path="configuracion" element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <Configuracion />
              </ProtectedRoute>
            } />
          </Route>
          
          {/* Catch all */}
          <Route path="*" element={<CatchAll />} />
        </Routes>
        </Router>
      </CartProvider>
      </CurrencyProvider>
    </AuthProvider>
  );
}

export default App;
