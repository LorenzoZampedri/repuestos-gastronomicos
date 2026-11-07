const cache = new Map();

export const getAvatarCacheado = (fotoUrl) => cache.get(fotoUrl);

export const guardarAvatarCacheado = (fotoUrl, objectUrl) => {
  cache.set(fotoUrl, objectUrl);
};

export const limpiarCacheAvatares = () => {
  cache.forEach((objectUrl) => URL.revokeObjectURL(objectUrl));
  cache.clear();
};
