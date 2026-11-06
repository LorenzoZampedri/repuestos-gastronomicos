import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Package } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const Registro = () => {
  const [form, setForm] = useState({
    username: '',
    nombre: '',
    email: '',
    password: '',
    confirmarPassword: '',
  });
  const [loading, setLoading] = useState(false);
  const { login, homePorRol } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (form.password.length < 8) {
      toast.error('La contraseña debe tener al menos 8 caracteres');
      return;
    }
    if (form.password !== form.confirmarPassword) {
      toast.error('Las contraseñas no coinciden');
      return;
    }

    setLoading(true);
    try {
      const username = form.username.trim();
      await api.post('/api/auth/register', {
        username,
        password: form.password,
        nombre: form.nombre.trim(),
        email: form.email.trim() || null,
      });
      const data = await login(username, form.password);
      toast.success('¡Cuenta creada! Ya podés consultar el catálogo');
      navigate(homePorRol(data.rol), { replace: true });
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo crear la cuenta');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-900 via-primary-800 to-primary-950 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="w-16 h-16 bg-white rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg">
            <Package className="w-10 h-10 text-primary-800" />
          </div>
          <h1 className="text-2xl font-bold text-white">Repuestos Gastronómicos</h1>
          <p className="text-primary-200 mt-1">Creá tu cuenta de cliente</p>
        </div>

        <div className="bg-white rounded-2xl shadow-xl p-8">
          <h2 className="text-xl font-semibold text-gray-900 mb-6">Crear Cuenta</h2>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="registro-usuario" className="label">Usuario</label>
              <input
                id="registro-usuario"
                name="username"
                type="text"
                value={form.username}
                onChange={handleChange}
                className="input"
                placeholder="Tu nombre de usuario"
                minLength={3}
                maxLength={50}
                required
                autoComplete="username"
              />
            </div>

            <div>
              <label htmlFor="registro-nombre" className="label">Nombre completo</label>
              <input
                id="registro-nombre"
                name="nombre"
                type="text"
                value={form.nombre}
                onChange={handleChange}
                className="input"
                placeholder="Nombre y apellido"
                maxLength={100}
                required
                autoComplete="name"
              />
            </div>

            <div>
              <label htmlFor="registro-email" className="label">Email (opcional)</label>
              <input
                id="registro-email"
                name="email"
                type="email"
                value={form.email}
                onChange={handleChange}
                className="input"
                placeholder="tu@email.com"
                maxLength={100}
                autoComplete="email"
              />
            </div>

            <div>
              <label htmlFor="registro-password" className="label">Contraseña</label>
              <input
                id="registro-password"
                name="password"
                type="password"
                value={form.password}
                onChange={handleChange}
                className="input"
                placeholder="Mínimo 8 caracteres"
                minLength={8}
                maxLength={72}
                required
                autoComplete="new-password"
              />
            </div>

            <div>
              <label htmlFor="registro-confirmar" className="label">Repetir contraseña</label>
              <input
                id="registro-confirmar"
                name="confirmarPassword"
                type="password"
                value={form.confirmarPassword}
                onChange={handleChange}
                className="input"
                placeholder="Repetí tu contraseña"
                minLength={8}
                maxLength={72}
                required
                autoComplete="new-password"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                  Creando cuenta...
                </>
              ) : (
                'Crear Cuenta'
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-600">
            ¿Ya tenés cuenta?{' '}
            <Link to="/login" className="font-medium text-primary-800 hover:text-primary-700">
              Iniciar sesión
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Registro;
