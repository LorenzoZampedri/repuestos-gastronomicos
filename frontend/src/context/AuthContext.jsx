import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import api from '../services/api';
import { guardarVista, leerVista, limpiarVista, vistaVencida } from '../services/sesion';
import { limpiarCacheAvatares } from '../lib/avatarCache';

const AuthContext = createContext(null);

const HOME_POR_ROL = {
  ADMIN: '/',
  EMPLEADO: '/ventas/nueva',
  CLIENTE: '/catalogo',
};

export const homePorRol = (rol) => HOME_POR_ROL[rol] || '/login';

const usuarioDesdeVista = (vista) => ({
  username: vista.username,
  nombre: vista.nombre,
  rol: vista.rol,
  fotoUrl: vista.fotoUrl || '',
});

const leerUsuarioLocal = () => {
  const userData = localStorage.getItem('user');
  if (!userData) return null;
  try {
    return JSON.parse(userData);
  } catch {
    return null;
  }
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

const construirSesionInicial = () => {
  const vistaGuardada = leerVista();
  if (vistaGuardada && !vistaVencida(vistaGuardada)) {
    return {
      vista: vistaGuardada,
      token: vistaGuardada.token,
      user: usuarioDesdeVista(vistaGuardada),
    };
  }

  if (vistaGuardada) limpiarVista();

  const tokenGuardado = localStorage.getItem('token');
  const userGuardado = leerUsuarioLocal();
  return {
    vista: null,
    token: tokenGuardado,
    user: tokenGuardado ? userGuardado : null,
  };
};

export const AuthProvider = ({ children }) => {
  const sesionInicial = useRef(null);
  if (sesionInicial.current === null) {
    sesionInicial.current = construirSesionInicial();
  }

  const [vista, setVista] = useState(sesionInicial.current.vista);
  const [token, setToken] = useState(sesionInicial.current.token);
  const [user, setUser] = useState(sesionInicial.current.user);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) {
      api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    } else {
      delete api.defaults.headers.common['Authorization'];
    }
    setLoading(false);
  }, [token]);

  const login = async (username, password) => {
    const response = await api.post('/api/auth/login', { username, password });
    const { token: newToken, username: userUsername, nombre, rol, fotoUrl } = response.data;
    const nuevoUsuario = { username: userUsername, nombre, rol, fotoUrl: fotoUrl || '' };

    limpiarVista();
    localStorage.setItem('token', newToken);
    localStorage.setItem('user', JSON.stringify(nuevoUsuario));

    setVista(null);
    setToken(newToken);
    setUser(nuevoUsuario);
    api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;

    return response.data;
  };

  const iniciarVistaComo = async (usuarioId) => {
    const response = await api.post(`/api/usuarios/${usuarioId}/vista-como`);
    const datos = response.data;

    guardarVista(datos);
    setVista(datos);
    setToken(datos.token);
    setUser(usuarioDesdeVista(datos));
    api.defaults.headers.common['Authorization'] = `Bearer ${datos.token}`;

    window.location.assign(homePorRol(datos.rol));
    return datos;
  };

  const salirDeVista = async () => {
    if (!vistaVencida(vista)) {
      try {
        await api.post('/api/auth/vista-como/fin');
      } catch {
        // Si el token de vista ya venció, el interceptor de api ya limpió y redirigió.
      }
    }

    limpiarVista();
    const adminToken = localStorage.getItem('token');
    const adminUser = leerUsuarioLocal();

    setVista(null);
    setToken(adminToken);
    setUser(adminUser);

    if (adminToken) {
      api.defaults.headers.common['Authorization'] = `Bearer ${adminToken}`;
    } else {
      delete api.defaults.headers.common['Authorization'];
    }

    window.location.assign(adminToken ? '/usuarios' : '/login');
  };

  const logout = () => {
    if (vista) {
      salirDeVista();
      return;
    }

    localStorage.removeItem('token');
    localStorage.removeItem('user');
    limpiarCacheAvatares();
    setToken(null);
    setUser(null);
    delete api.defaults.headers.common['Authorization'];
  };

  const actualizarFoto = (fotoUrl) => {
    if (vista) return;
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
      vista,
      enVista: Boolean(vista),
      login, 
      logout, 
      iniciarVistaComo,
      salirDeVista,
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
