import React, { useLayoutEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { gsap, prefersReducedMotion } from '../lib/animations';

const PageTransition = ({ children }) => {
  const { pathname } = useLocation();
  const ref = useRef(null);

  useLayoutEffect(() => {
    window.scrollTo(0, 0);

    const el = ref.current;
    if (!el || prefersReducedMotion()) return;

    const tween = gsap.fromTo(
      el,
      { opacity: 0 },
      { opacity: 1, duration: 0.25, ease: 'power2.out', overwrite: true }
    );

    return () => tween.kill();
  }, [pathname]);

  return <div ref={ref}>{children}</div>;
};

export default PageTransition;
