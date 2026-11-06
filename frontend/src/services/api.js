import axios from 'axios';
import toast from 'react-hot-toast';

const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
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
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    if (status === 403 && !esLogin) {
      const mensaje = error.response?.data?.message || 'No tenés permisos para realizar esta acción';
      toast.error(mensaje);
    }

    return Promise.reject(error);
  }
);

export default api;
