import React from 'react';

const EmptyState = ({ icon: Icon, title, description, action }) => (
  <div className="flex flex-col items-center justify-center text-center py-12 px-4">
    {Icon && <Icon className="w-12 h-12 text-gray-300 mb-3" aria-hidden="true" />}
    <h3 className="text-base font-medium text-gray-900">{title}</h3>
    {description && <p className="text-sm text-gray-500 mt-1 max-w-sm">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
);

export default EmptyState;
