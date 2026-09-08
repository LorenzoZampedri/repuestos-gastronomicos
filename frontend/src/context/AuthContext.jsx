import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

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
    const { token: newToken, username: userUsername, nombre, rol } = response.data;
    
    localStorage.setItem('token', newToken);
    localStorage.setItem('user', JSON.stringify({ username: userUsername, nombre, rol }));
    
    setToken(newToken);
    setUser({ username: userUsername, nombre, rol });
    api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
    
    return response.data;
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
    delete api.defaults.headers.common['Authorization'];
  };

  const isAdmin = () => user?.rol === 'ADMIN';
  const isVendedor = () => user?.rol === 'VENDEDOR' || user?.rol === 'ADMIN';
  const isOperario = () => user?.rol === 'OPERARIO' || user?.rol === 'ADMIN';

  return (
    <AuthContext.Provider value={{ 
      user, 
      token, 
      loading, 
      login, 
      logout, 
      isAdmin, 
      isVendedor, 
      isOperario 
    }}>
      {children}
    </AuthContext.Provider>
  );
};
