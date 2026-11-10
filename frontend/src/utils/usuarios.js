export const USERNAME_REGEX = /^[A-Za-z0-9._-]{3,50}$/;

export const USERNAME_FORMATO_MSG =
  'Usá solo letras sin acentos, números, punto, guion o guion bajo';

export const USERNAME_LARGO_MSG = 'El nombre de usuario debe tener entre 3 y 50 caracteres';

export const validarUsername = (username) => {
  const valor = String(username ?? '').trim();
  if (valor.length < 3 || valor.length > 50) return USERNAME_LARGO_MSG;
  if (!USERNAME_REGEX.test(valor)) return USERNAME_FORMATO_MSG;
  return '';
};
