import React, { useEffect, useState } from 'react';
import { Search, Plus, Edit2, KeyRound, Power, PowerOff, Mail, Link2, Camera, Eye } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import Avatar from '../components/Avatar';
import SelectorFoto from '../components/SelectorFoto';

const ROLES = [
  { value: 'ADMIN', label: 'Administrador' },
  { value: 'EMPLEADO', label: 'Empleado' },
  { value: 'CLIENTE', label: 'Cliente' },
];

const ROL_BADGES = {
  ADMIN: 'bg-purple-100 text-purple-800',
  EMPLEADO: 'bg-blue-100 text-blue-800',
  CLIENTE: 'bg-green-100 text-green-800',
};

const rolLabel = (rol) => ROLES.find((r) => r.value === rol)?.label || rol;

const UsuarioFormModal = ({ open, usuario, onClose, onSaved }) => {
  const esEdicion = Boolean(usuario);
  const [form, setForm] = useState({
    username: '',
    password: '',
    nombre: '',
    email: '',
    rol: 'EMPLEADO',
    activo: true,
  });
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!open) return;
    setForm({
      username: usuario?.username || '',
      password: '',
      nombre: usuario?.nombre || '',
      email: usuario?.email || '',
      rol: usuario?.rol || 'EMPLEADO',
      activo: usuario?.activo ?? true,
    });
    setGuardando(false);
  }, [open, usuario]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm({ ...form, [name]: type === 'checkbox' ? checked : value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!esEdicion && form.password.length < 8) {
      toast.error('La contraseña debe tener al menos 8 caracteres');
      return;
    }

    setGuardando(true);
    try {
      const payload = esEdicion
        ? {
            nombre: form.nombre.trim(),
            email: form.email.trim() || null,
            rol: form.rol,
            activo: form.activo,
          }
        : {
            username: form.username.trim(),
            password: form.password,
            nombre: form.nombre.trim(),
            email: form.email.trim() || null,
            rol: form.rol,
          };

      if (esEdicion) {
        await api.put(`/api/usuarios/${usuario.id}`, payload);
      } else {
        await api.post('/api/usuarios', payload);
      }

      toast.success(esEdicion ? 'Usuario actualizado' : 'Usuario creado');
      onSaved();
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo guardar el usuario');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} labelledBy="usuario-form-titulo">
      <div className="p-6">
        <h3 id="usuario-form-titulo" className="text-lg font-semibold mb-4">
          {esEdicion ? 'Editar usuario' : 'Nuevo usuario'}
        </h3>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="usuario-username" className="label">Usuario</label>
            <input
              id="usuario-username"
              name="username"
              type="text"
              value={form.username}
              onChange={handleChange}
              className="input disabled:bg-gray-100 disabled:text-gray-500"
              minLength={3}
              maxLength={50}
              required
              disabled={esEdicion}
              autoComplete="off"
            />
            {esEdicion && (
              <p className="text-xs text-gray-500 mt-1">El nombre de usuario no se puede modificar.</p>
            )}
          </div>

          {!esEdicion && (
            <div>
              <label htmlFor="usuario-password" className="label">Contraseña</label>
              <input
                id="usuario-password"
                name="password"
                type="password"
                value={form.password}
                onChange={handleChange}
                className="input"
                minLength={8}
                maxLength={72}
                required
                autoComplete="new-password"
              />
            </div>
          )}

          <div>
            <label htmlFor="usuario-nombre" className="label">Nombre completo</label>
            <input
              id="usuario-nombre"
              name="nombre"
              type="text"
              value={form.nombre}
              onChange={handleChange}
              className="input"
              maxLength={100}
              required
            />
          </div>

          <div>
            <label htmlFor="usuario-email" className="label">Email (opcional)</label>
            <input
              id="usuario-email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              className="input"
              maxLength={100}
            />
          </div>

          <div>
            <label htmlFor="usuario-rol" className="label">Rol</label>
            <select
              id="usuario-rol"
              name="rol"
              value={form.rol}
              onChange={handleChange}
              className="input"
              required
            >
              {ROLES.map((rol) => (
                <option key={rol.value} value={rol.value}>{rol.label}</option>
              ))}
            </select>
          </div>

          {esEdicion && (
            <label htmlFor="usuario-activo" className="flex items-center gap-2 text-sm text-gray-700">
              <input
                id="usuario-activo"
                name="activo"
                type="checkbox"
                checked={form.activo}
                onChange={handleChange}
                className="rounded border-gray-300"
              />
              Usuario activo
            </label>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" disabled={guardando} className="btn-primary">
              {guardando ? 'Guardando...' : 'Guardar'}
            </button>
          </div>
        </form>
      </div>
    </Modal>
  );
};

const ResetPasswordModal = ({ open, usuario, onClose }) => {
  const [password, setPassword] = useState('');
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!open) {
      setPassword('');
      setGuardando(false);
    }
  }, [open]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (password.length < 8) {
      toast.error('La contraseña debe tener al menos 8 caracteres');
      return;
    }

    setGuardando(true);
    try {
      await api.put(`/api/usuarios/${usuario.id}/password`, { passwordNueva: password });
      toast.success('Contraseña restablecida');
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo restablecer la contraseña');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal open={open && !!usuario} onClose={onClose} labelledBy="reset-password-titulo">
      <div className="p-6">
        <h3 id="reset-password-titulo" className="text-lg font-semibold mb-1">
          Restablecer contraseña
        </h3>
        <p className="text-sm text-gray-500 mb-4">
          Usuario: <span className="font-medium text-gray-700">{usuario?.username}</span>
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="reset-password" className="label">Nueva contraseña</label>
            <input
              id="reset-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="input"
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              required
            />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" disabled={guardando} className="btn-primary">
              {guardando ? 'Guardando...' : 'Restablecer'}
            </button>
          </div>
        </form>
      </div>
    </Modal>
  );
};

const VincularClienteModal = ({ open, usuario, onClose, onSaved }) => {
  const [clientes, setClientes] = useState([]);
  const [clienteId, setClienteId] = useState('');
  const [cargando, setCargando] = useState(false);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!open || !usuario) return;

    setClienteId(usuario.clienteId ? String(usuario.clienteId) : '');
    setGuardando(false);
    setCargando(true);
    api.get('/api/clientes')
      .then((response) => setClientes(response.data))
      .catch((error) => toast.error(error.response?.data?.message || 'No se pudieron cargar los clientes'))
      .finally(() => setCargando(false));
  }, [open, usuario]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!clienteId) {
      toast.error('Seleccioná un cliente');
      return;
    }

    setGuardando(true);
    try {
      await api.put(`/api/usuarios/${usuario.id}/cliente`, {
        clienteId: Number(clienteId),
      });
      toast.success('Cliente vinculado');
      onSaved();
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo vincular el cliente');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <Modal open={open && !!usuario} onClose={onClose} labelledBy="vincular-cliente-titulo">
      <div className="p-6">
        <h3 id="vincular-cliente-titulo" className="text-lg font-semibold mb-1">
          Vincular cliente
        </h3>
        <p className="text-sm text-gray-500 mb-4">
          Usuario: <span className="font-medium text-gray-700">{usuario?.username}</span>
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="vincular-cliente" className="label">Ficha de cliente</label>
            <select
              id="vincular-cliente"
              value={clienteId}
              onChange={(e) => setClienteId(e.target.value)}
              className="input"
              disabled={cargando || guardando}
              required
            >
              <option value="">Seleccionar cliente...</option>
              {clientes.map((cliente) => (
                <option key={cliente.id} value={cliente.id}>
                  {cliente.nombre}{cliente.telefono ? ` — ${cliente.telefono}` : ''}
                </option>
              ))}
            </select>
            <p className="text-xs text-gray-500 mt-1">
              Solo se muestran clientes activos. Al vincular, los pedidos del usuario se
              reasignan a la ficha elegida.
            </p>
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" disabled={cargando || guardando} className="btn-primary">
              {guardando ? 'Vinculando...' : 'Vincular'}
            </button>
          </div>
        </form>
      </div>
    </Modal>
  );
};

const VistaComoModal = ({ open, usuario, onClose, onConfirm, iniciando }) => {
  return (
    <Modal open={open && !!usuario} onClose={onClose} labelledBy="vista-como-titulo">
      <div className="p-6">
        <h3 id="vista-como-titulo" className="text-lg font-semibold mb-2">
          Ver como usuario
        </h3>
        <p className="text-sm text-gray-600">
          Vas a ver el sistema como{' '}
          <span className="font-medium text-gray-900">{usuario?.nombre}</span>{' '}
          ({rolLabel(usuario?.rol)}) en esta pestaña. Es de solo lectura: no vas a poder
          modificar nada. Tu sesión de administrador no se cierra y queda registrado en
          Logs. La vista dura 30 minutos.
        </p>

        <div className="flex justify-end gap-3 pt-6">
          <button type="button" onClick={onClose} className="btn-secondary" disabled={iniciando}>
            Cancelar
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="btn-primary flex items-center gap-2"
            disabled={iniciando}
          >
            <Eye className="w-4 h-4" aria-hidden="true" />
            {iniciando ? 'Iniciando...' : 'Ver como'}
          </button>
        </div>
      </div>
    </Modal>
  );
};

const Usuarios = () => {
  const { user, actualizarFoto, iniciarVistaComo } = useAuth();
  const [usuarios, setUsuarios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busqueda, setBusqueda] = useState('');
  const [filtroRol, setFiltroRol] = useState('');
  const [filtroActivo, setFiltroActivo] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [usuarioEditando, setUsuarioEditando] = useState(null);
  const [usuarioPassword, setUsuarioPassword] = useState(null);
  const [usuarioCliente, setUsuarioCliente] = useState(null);
  const [usuarioFoto, setUsuarioFoto] = useState(null);
  const [usuarioVista, setUsuarioVista] = useState(null);
  const [iniciandoVista, setIniciandoVista] = useState(false);

  useEffect(() => {
    fetchUsuarios();
  }, []);

  const fetchUsuarios = async () => {
    try {
      const response = await api.get('/api/usuarios');
      setUsuarios(response.data);
    } catch (error) {
      toast.error('Error al cargar usuarios');
    } finally {
      setLoading(false);
    }
  };

  const handleNuevo = () => {
    setUsuarioEditando(null);
    setFormOpen(true);
  };

  const handleEditar = (usuario) => {
    setUsuarioEditando(usuario);
    setFormOpen(true);
  };

  const handleIniciarVista = async () => {
    if (!usuarioVista) return;

    setIniciandoVista(true);
    try {
      await iniciarVistaComo(usuarioVista.id);
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo iniciar la vista');
      setIniciandoVista(false);
    }
  };

  const handleToggleActivo = async (usuario) => {
    const accion = usuario.activo ? 'desactivar' : 'activar';
    if (!window.confirm(`¿Estás seguro de ${accion} a ${usuario.nombre}?`)) return;

    try {
      await api.patch(`/api/usuarios/${usuario.id}/activo`, { activo: !usuario.activo });
      toast.success(usuario.activo ? 'Usuario desactivado' : 'Usuario activado');
      fetchUsuarios();
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo actualizar el estado');
    }
  };

  const usuariosFiltrados = usuarios.filter((usuario) => {
    const texto = busqueda.toLowerCase();
    const coincideBusqueda = !texto
      || usuario.username.toLowerCase().includes(texto)
      || usuario.nombre.toLowerCase().includes(texto)
      || usuario.email?.toLowerCase().includes(texto);

    const coincideRol = !filtroRol || usuario.rol === filtroRol;
    const coincideActivo = !filtroActivo
      || (filtroActivo === 'activo' ? usuario.activo : !usuario.activo);

    return coincideBusqueda && coincideRol && coincideActivo;
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Usuarios</h1>
          <p className="text-gray-500">{usuarios.length} usuarios registrados</p>
        </div>
        <button onClick={handleNuevo} className="btn-primary flex items-center gap-2">
          <Plus className="w-5 h-5" />
          Nuevo Usuario
        </button>
      </div>

      <div className="card">
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Buscar por usuario, nombre o email..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              aria-label="Buscar usuarios"
              className="input pl-10"
            />
          </div>
          <select
            value={filtroRol}
            onChange={(e) => setFiltroRol(e.target.value)}
            aria-label="Filtrar por rol"
            className="input w-auto"
          >
            <option value="">Todos los roles</option>
            {ROLES.map((rol) => (
              <option key={rol.value} value={rol.value}>{rol.label}</option>
            ))}
          </select>
          <select
            value={filtroActivo}
            onChange={(e) => setFiltroActivo(e.target.value)}
            aria-label="Filtrar por estado"
            className="input w-auto"
          >
            <option value="">Todos los estados</option>
            <option value="activo">Activos</option>
            <option value="inactivo">Inactivos</option>
          </select>
        </div>
      </div>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800"></div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Usuario</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Email</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Rol</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Estado</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-gray-500">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {usuariosFiltrados.map((usuario) => {
                  const esMismoUsuario = usuario.username === user?.username;
                  return (
                    <tr key={usuario.id} className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <Avatar fotoUrl={usuario.fotoUrl} nombre={usuario.nombre} size="sm" />
                          <div>
                            <p className="font-medium text-gray-900">
                              {usuario.nombre}
                              {esMismoUsuario && (
                                <span className="ml-2 text-xs font-normal text-gray-500">(vos)</span>
                              )}
                            </p>
                            <p className="text-sm text-gray-500">@{usuario.username}</p>
                            {usuario.rol === 'CLIENTE' && (
                              <p className="text-xs text-gray-400">
                                {usuario.clienteNombre
                                  ? `Cliente: ${usuario.clienteNombre}`
                                  : 'Cliente sin vincular'}
                              </p>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        {usuario.email ? (
                          <span className="flex items-center gap-2 text-sm text-gray-600">
                            <Mail className="w-4 h-4 text-gray-400" aria-hidden="true" />
                            {usuario.email}
                          </span>
                        ) : (
                          <span className="text-sm text-gray-400">—</span>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-1 text-xs font-medium rounded-full ${ROL_BADGES[usuario.rol] || 'bg-gray-100 text-gray-700'}`}>
                          {rolLabel(usuario.rol)}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-1 text-xs font-medium rounded-full ${usuario.activo ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'}`}>
                          {usuario.activo ? 'Activo' : 'Inactivo'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center justify-end gap-1">
                          {!esMismoUsuario && usuario.rol !== 'ADMIN' && usuario.activo && (
                            <button
                              onClick={() => setUsuarioVista(usuario)}
                              className="p-2 text-gray-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                              title="Ver como este usuario"
                              aria-label={`Ver como ${usuario.username}`}
                            >
                              <Eye className="w-4 h-4" aria-hidden="true" />
                            </button>
                          )}
                          <button
                            onClick={() => setUsuarioFoto(usuario)}
                            className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                            title="Cambiar foto"
                            aria-label={`Cambiar foto de ${usuario.username}`}
                          >
                            <Camera className="w-4 h-4" aria-hidden="true" />
                          </button>
                          <button
                            onClick={() => handleEditar(usuario)}
                            className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                            title="Editar usuario"
                            aria-label={`Editar ${usuario.username}`}
                          >
                            <Edit2 className="w-4 h-4" aria-hidden="true" />
                          </button>
                          {usuario.rol === 'CLIENTE' && (
                            <button
                              onClick={() => setUsuarioCliente(usuario)}
                              className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                              title="Vincular cliente"
                              aria-label={`Vincular cliente de ${usuario.username}`}
                            >
                              <Link2 className="w-4 h-4" aria-hidden="true" />
                            </button>
                          )}
                          <button
                            onClick={() => setUsuarioPassword(usuario)}
                            className="p-2 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                            title="Restablecer contraseña"
                            aria-label={`Restablecer contraseña de ${usuario.username}`}
                          >
                            <KeyRound className="w-4 h-4" aria-hidden="true" />
                          </button>
                          <button
                            onClick={() => handleToggleActivo(usuario)}
                            disabled={esMismoUsuario}
                            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                            title={esMismoUsuario ? 'No podés desactivar tu propio usuario' : (usuario.activo ? 'Desactivar usuario' : 'Activar usuario')}
                            aria-label={usuario.activo ? `Desactivar ${usuario.username}` : `Activar ${usuario.username}`}
                          >
                            {usuario.activo
                              ? <PowerOff className="w-4 h-4" aria-hidden="true" />
                              : <Power className="w-4 h-4" aria-hidden="true" />}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {usuariosFiltrados.length === 0 && (
                  <tr>
                    <td colSpan="5" className="py-12 text-center text-gray-500">
                      No se encontraron usuarios
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <UsuarioFormModal
        open={formOpen}
        usuario={usuarioEditando}
        onClose={() => setFormOpen(false)}
        onSaved={fetchUsuarios}
      />

      <ResetPasswordModal
        open={!!usuarioPassword}
        usuario={usuarioPassword}
        onClose={() => setUsuarioPassword(null)}
      />

      <VincularClienteModal
        open={!!usuarioCliente}
        usuario={usuarioCliente}
        onClose={() => setUsuarioCliente(null)}
        onSaved={fetchUsuarios}
      />

      <VistaComoModal
        open={!!usuarioVista}
        usuario={usuarioVista}
        onClose={() => {
          setUsuarioVista(null);
          setIniciandoVista(false);
        }}
        onConfirm={handleIniciarVista}
        iniciando={iniciandoVista}
      />

      <SelectorFoto
        open={!!usuarioFoto}
        onClose={() => setUsuarioFoto(null)}
        nombre={usuarioFoto?.nombre}
        fotoUrl={usuarioFoto?.fotoUrl}
        endpoint={usuarioFoto ? `/api/usuarios/${usuarioFoto.id}/foto` : ''}
        compartida={Boolean(usuarioFoto?.clienteId)}
        onActualizada={(fotoUrl) => {
          if (usuarioFoto?.username === user?.username) actualizarFoto(fotoUrl);
          fetchUsuarios();
        }}
      />
    </div>
  );
};

export default Usuarios;
