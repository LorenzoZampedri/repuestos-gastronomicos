import React, { useState, useEffect, useRef } from 'react';
import gsap from 'gsap';

const Modal = ({ open, onClose, labelledBy, children }) => {
  const overlayRef = useRef(null);
  const panelRef = useRef(null);
  const [mounted, setMounted] = useState(open);

  useEffect(() => {
    if (open) setMounted(true);
  }, [open]);

  useEffect(() => {
    if (!mounted) return;
    const overlay = overlayRef.current;
    const panel = panelRef.current;
    if (!overlay || !panel) return;

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) {
      gsap.set(overlay, { opacity: open ? 1 : 0 });
      gsap.set(panel, { opacity: open ? 1 : 0, scale: 1, y: 0 });
      if (!open) setMounted(false);
      return;
    }

    const tl = gsap.timeline({ defaults: { ease: 'power2.out' } });
    if (open) {
      tl.fromTo(overlay, { opacity: 0 }, { opacity: 1, duration: 0.2 }, 0)
        .fromTo(
          panel,
          { opacity: 0, scale: 0.96, y: 8 },
          { opacity: 1, scale: 1, y: 0, duration: 0.28 },
          0
        );
    } else {
      tl.to(overlay, { opacity: 0, duration: 0.18 }, 0)
        .to(
          panel,
          { opacity: 0, scale: 0.97, y: 8, duration: 0.2, onComplete: () => setMounted(false) },
          0
        );
    }

    return () => tl.kill();
  }, [open, mounted]);

  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!mounted) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div ref={overlayRef} className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        className="relative bg-white rounded-2xl max-w-lg w-full max-h-[90vh] overflow-auto"
      >
        {children}
      </div>
    </div>
  );
};

export default Modal;
