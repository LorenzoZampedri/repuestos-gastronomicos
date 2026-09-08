import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import { CartProvider } from './context/CartContext';

// Layout
import Layout from './components/Layout';
import PublicLayout from './components/PublicLayout';

// Pages
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Productos from './pages/Productos';
import ProductoForm from './pages/ProductoForm';
import Ventas from './pages/Ventas';
import VentaRapida from './pages/VentaRapida';
import Clientes from './pages/Clientes';
import ClienteForm from './pages/ClienteForm';
import CuentasCorrientes from './pages/CuentasCorrientes';
import Catalogo from './pages/Catalogo';

// Protected Route Component
const ProtectedRoute = ({ children, requiredRole }) => {
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
  
  if (requiredRole && user.rol !== requiredRole && user.rol !== 'ADMIN') {
    return <Navigate to="/" replace />;
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
    return <Navigate to="/" replace />;
  }
  
  return children;
};

function App() {
  return (
    <AuthProvider>
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
              <Login />
            </PublicRoute>
          } />
          
          <Route path="/catalogo" element={
            <PublicLayout>
              <Catalogo />
            </PublicLayout>
          } />
          
          {/* Protected Routes */}
          <Route path="/" element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }>
            <Route index element={<Dashboard />} />
            <Route path="productos" element={<Productos />} />
            <Route path="productos/nuevo" element={<ProductoForm />} />
            <Route path="productos/:id" element={<ProductoForm />} />
            <Route path="ventas" element={<Ventas />} />
            <Route path="ventas/nueva" element={<VentaRapida />} />
            <Route path="clientes" element={
              <ProtectedRoute requiredRole="ADMIN">
                <Clientes />
              </ProtectedRoute>
            } />
            <Route path="clientes/nuevo" element={
              <ProtectedRoute requiredRole="ADMIN">
                <ClienteForm />
              </ProtectedRoute>
            } />
            <Route path="clientes/:id" element={
              <ProtectedRoute requiredRole="ADMIN">
                <ClienteForm />
              </ProtectedRoute>
            } />
            <Route path="cuentas-corrientes" element={
              <ProtectedRoute requiredRole="ADMIN">
                <CuentasCorrientes />
              </ProtectedRoute>
            } />
          </Route>
          
          {/* Catch all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Router>
      </CartProvider>
    </AuthProvider>
  );
}

export default App;
