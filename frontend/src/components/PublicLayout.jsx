import React from 'react';
import { Link } from 'react-router-dom';
import { Package, Phone } from 'lucide-react';

const PublicLayout = ({ children }) => {
  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary-800 rounded-lg flex items-center justify-center">
                <Package className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="text-lg font-bold text-gray-900">Repuestos Gastronómicos</h1>
                <p className="text-xs text-gray-500">Repuestos para gastronomía pesada</p>
              </div>
            </div>
            
            <div className="flex items-center gap-4">
              <a 
                href="https://wa.me/5491112345678" 
                target="_blank" 
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-green-600 hover:text-green-700"
              >
                <Phone className="w-4 h-4" />
                <span className="hidden sm:inline">WhatsApp</span>
              </a>
              <Link 
                to="/login"
                className="text-sm text-gray-600 hover:text-primary-800"
              >
                Iniciar Sesión
              </Link>
            </div>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-gray-200 mt-auto">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-sm text-gray-500">
              © 2024 Repuestos Gastronómicos. Todos los derechos reservados.
            </p>
            <div className="flex items-center gap-4 text-sm text-gray-500">
              <span>Once, Buenos Aires</span>
              <span>•</span>
              <a href="tel:+5491112345678" className="hover:text-primary-800">
                +54 9 11 1234-5678
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default PublicLayout;
