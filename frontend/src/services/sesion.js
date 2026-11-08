const CLAVE_VISTA = 'vista_como';

const parsearJson = (valor) => {
  if (!valor) return null;
  try {
    return JSON.parse(valor);
  } catch {
    return null;
  }
};

export const leerVista = () => {
  if (typeof window === 'undefined') return null;

  const vista = parsearJson(window.sessionStorage.getItem(CLAVE_VISTA));
  if (!vista?.token || !vista?.username) return null;

  return vista;
};

export const vistaVencida = (vista = leerVista()) =>
  Boolean(vista?.expiraEn && new Date(vista.expiraEn).getTime() <= Date.now());

export const guardarVista = (vista) => {
  window.sessionStorage.setItem(CLAVE_VISTA, JSON.stringify(vista));
};

export const limpiarVista = () => {
  window.sessionStorage.removeItem(CLAVE_VISTA);
};

export const hayVista = () => Boolean(leerVista());

export const tokenActivo = () => leerVista()?.token || localStorage.getItem('token');
