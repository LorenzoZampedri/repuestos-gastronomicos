import axios from 'axios';
import toast from 'react-hot-toast';
import { hayVista, limpiarVista, tokenActivo } from './sesion';

const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
api.interceptors.request.use(
  (config) => {
    const token = tokenActivo();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url || '';
    const esLogin = url.includes('/api/auth/login');

    if (status === 401 && !esLogin) {
      if (hayVista()) {
        limpiarVista();
        toast.error('La vista venció. Volviste a tu sesión de administrador.');
        window.location.assign('/usuarios');
      } else {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
    }

    if (status === 403 && !esLogin) {
      const codigo = error.response?.data?.code;
      const mensaje = codigo === 'MODO_VISTA_SOLO_LECTURA'
        ? 'Estás en modo vista: solo podés mirar, no modificar.'
        : error.response?.data?.message || 'No tenés permisos para realizar esta acción';
      toast.error(mensaje);
    }

    return Promise.reject(error);
  }
);

export default api;
