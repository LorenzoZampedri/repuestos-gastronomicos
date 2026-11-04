export const formatNumber = (value, options = {}) => {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return Number(value).toLocaleString('es-AR', {
    maximumFractionDigits: 2,
    ...options,
  });
};

export const formatMoney = (value, currency = 'ARS', fallback = '—') => {
  if (value == null || Number.isNaN(Number(value))) return fallback;
  const formatted = formatNumber(value);
  return currency === 'USD' ? `U$S ${formatted}` : `$${formatted}`;
};

export const normalizePhone = (phone) => String(phone || '').replace(/\D/g, '');

export const whatsappUrl = (phone, message) => {
  const number = normalizePhone(phone);
  if (!number) return 'https://wa.me/';
  const text = message ? `?text=${encodeURIComponent(message)}` : '';
  return `https://wa.me/${number}${text}`;
};
