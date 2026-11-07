import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';
import { limpiarCacheAvatares } from '../lib/avatarCache';

const AuthContext = createContext(null);

const HOME_POR_ROL = {
  ADMIN: '/',
  EMPLEADO: '/ventas/nueva',
  CLIENTE: '/catalogo',
};

export const homePorRol = (rol) => HOME_POR_ROL[rol] || '/login';

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) {
      api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      const userData = localStorage.getItem('user');
      if (userData) {
        setUser(JSON.parse(userData));
      }
    }
    setLoading(false);
  }, [token]);

  const login = async (username, password) => {
    const response = await api.post('/api/auth/login', { username, password });
    const { token: newToken, username: userUsername, nombre, rol, fotoUrl } = response.data;
    const nuevoUsuario = { username: userUsername, nombre, rol, fotoUrl: fotoUrl || '' };
    
    localStorage.setItem('token', newToken);
    localStorage.setItem('user', JSON.stringify(nuevoUsuario));
    
    setToken(newToken);
    setUser(nuevoUsuario);
    api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
    
    return response.data;
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    limpiarCacheAvatares();
    setToken(null);
    setUser(null);
    delete api.defaults.headers.common['Authorization'];
  };

  const actualizarFoto = (fotoUrl) => {
    setUser((actual) => {
      if (!actual) return actual;
      const actualizado = { ...actual, fotoUrl: fotoUrl || '' };
      localStorage.setItem('user', JSON.stringify(actualizado));
      return actualizado;
    });
  };

  const isAdmin = () => user?.rol === 'ADMIN';
  const isEmpleado = () => user?.rol === 'EMPLEADO';
  const isCliente = () => user?.rol === 'CLIENTE';
  const hasRole = (...roles) => roles.includes(user?.rol);

  return (
    <AuthContext.Provider value={{ 
      user, 
      token, 
      loading, 
      login, 
      logout, 
      actualizarFoto,
      isAdmin, 
      isEmpleado,
      isCliente,
      hasRole,
      homePorRol: () => homePorRol(user?.rol),
    }}>
      {children}
    </AuthContext.Provider>
  );
};
