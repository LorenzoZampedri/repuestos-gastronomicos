import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, MessageCircle, Send } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import Modal from './Modal';
import { useCart } from '../context/CartContext';
import { useCurrency } from '../context/CurrencyContext';
import { formatMoney } from '../utils/format';

const PedidoModal = ({ open, onClose }) => {
  const { items, clearCart, precioUnitario, subtotalItem, cartTotal } = useCart();
  const { moneda, getWhatsAppUrl } = useCurrency();

  const [telefono, setTelefono] = useState('');
  const [observaciones, setObservaciones] = useState('');
  const [cargandoContacto, setCargandoContacto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [pedidoCreado, setPedidoCreado] = useState(null);

  useEffect(() => {
    if (!open) return;

    setTelefono('');
    setObservaciones('');
    setPedidoCreado(null);
    setEnviando(false);

    let activo = true;
    setCargandoContacto(true);
    api.get('/api/pedidos/mios/contacto')
      .then((response) => {
        if (activo) setTelefono(response.data?.telefono || '');
      })
      .catch((error) => {
        if (activo) toast.error(error.response?.data?.message
          || 'No se pudieron cargar tus datos de contacto');
      })
      .finally(() => {
        if (activo) setCargandoContacto(false);
      });

    return () => { activo = false; };
  }, [open]);

  const fmt = (valor) => (valor == null ? '—' : formatMoney(valor, moneda));

  const handleSubmit = async (e) => {
    e.preventDefault();

    const telefonoNormalizado = telefono.trim();
    if (telefonoNormalizado.length < 6) {
      toast.error('Ingresá un teléfono de contacto válido');
      return;
    }
    if (items.length === 0) {
      toast.error('El carrito está vacío');
      onClose();
      return;
    }

    setEnviando(true);
    try {
      const response = await api.post('/api/pedidos', {
        items: items.map((item) => ({
          productoId: item.id,
          cantidad: item.cantidad,
        })),
        telefono: telefonoNormalizado,
        observaciones: observaciones.trim() || null,
      });
      clearCart(true);
      setPedidoCreado(response.data);
      toast.success(`Pedido #${response.data.id} enviado`);
      window.dispatchEvent(new Event('pedidos-actualizados'));
    } catch (error) {
      toast.error(error.response?.data?.message || 'No se pudo enviar el pedido');
    } finally {
      setEnviando(false);
    }
  };

  const avisarPorWhatsApp = () => {
    const mensaje = `Hola! Ya hice el pedido #${pedidoCreado.id} desde la web `
      + 'con los productos del carrito. ¿Me confirman la disponibilidad y el pago?';
    window.open(getWhatsAppUrl(mensaje), '_blank');
  };

  return (
    <Modal open={open} onClose={onClose} labelledBy="pedido-modal-titulo">
      <div className="p-6">
        {pedidoCreado ? (
          <div className="text-center space-y-4">
            <CheckCircle2 className="w-12 h-12 mx-auto text-green-600" aria-hidden="true" />
            <div>
              <h3 id="pedido-modal-titulo" className="text-lg font-semibold">
                Pedido #{pedidoCreado.id} enviado
              </h3>
              <p className="text-sm text-gray-500 mt-1">
                Nos ponemos en contacto para coordinar el pago y la entrega.
              </p>
            </div>
            <div className="flex flex-col gap-2">
              <button onClick={avisarPorWhatsApp} className="btn-whatsapp w-full">
                <MessageCircle className="w-5 h-5" aria-hidden="true" />
                Avisar por WhatsApp
              </button>
              <div className="flex gap-2 justify-center">
                <Link to="/mis-pedidos" onClick={onClose} className="btn-secondary">
                  Ver mis pedidos
                </Link>
                <button onClick={onClose} className="btn-primary">
                  Seguir comprando
                </button>
              </div>
            </div>
          </div>
        ) : (
          <>
            <h3 id="pedido-modal-titulo" className="text-lg font-semibold mb-1">
              Confirmar pedido
            </h3>
            <p className="text-sm text-gray-500 mb-4">
              Revisá los productos y dejá tu teléfono de contacto.
            </p>

            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 flex gap-2 mb-4">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" aria-hidden="true" />
              <p className="text-xs text-amber-800">
                El total es estimado: el precio final se confirma al aprobar el pedido.
                No se reserva stock hasta que el local lo aprueba.
              </p>
            </div>

            <div className="space-y-2 border border-gray-100 rounded-lg p-3 mb-4 max-h-40 overflow-y-auto">
              {items.map((item) => (
                <div key={item.id} className="flex justify-between gap-3 text-sm">
                  <div>
                    <p className="font-medium text-gray-900">{item.nombre}</p>
                    <p className="text-gray-500">
                      {item.cantidad} x {fmt(precioUnitario(item))}
                    </p>
                  </div>
                  <span className="font-medium text-gray-900 whitespace-nowrap">
                    {fmt(subtotalItem(item))}
                  </span>
                </div>
              ))}
              <div className="flex justify-between gap-3 text-sm font-bold border-t border-gray-100 pt-2">
                <span>Total estimado</span>
                <span className="text-primary-800">{fmt(cartTotal)}</span>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label htmlFor="pedido-telefono" className="label">Teléfono de contacto</label>
                <input
                  id="pedido-telefono"
                  type="tel"
                  value={telefono}
                  onChange={(e) => setTelefono(e.target.value)}
                  className="input"
                  minLength={6}
                  maxLength={20}
                  placeholder="11 5555-1234"
                  required
                  disabled={cargandoContacto || enviando}
                />
              </div>

              <div>
                <label htmlFor="pedido-observaciones" className="label">
                  Observaciones (opcional)
                </label>
                <textarea
                  id="pedido-observaciones"
                  value={observaciones}
                  onChange={(e) => setObservaciones(e.target.value)}
                  className="input"
                  rows={3}
                  maxLength={1000}
                  placeholder="Horario de contacto, aclaraciones, etc."
                  disabled={enviando}
                />
              </div>

              <div className="flex justify-end gap-3">
                <button type="button" onClick={onClose} className="btn-secondary">
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={enviando || cargandoContacto}
                  className="btn-primary flex items-center gap-2"
                >
                  {enviando ? (
                    <>
                      <span className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></span>
                      Enviando...
                    </>
                  ) : (
                    <>
                      <Send className="w-4 h-4" aria-hidden="true" />
                      Hacer pedido
                    </>
                  )}
                </button>
              </div>
            </form>
          </>
        )}
      </div>
    </Modal>
  );
};

export default PedidoModal;
