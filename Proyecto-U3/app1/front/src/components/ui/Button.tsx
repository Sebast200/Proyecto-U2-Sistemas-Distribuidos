import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: 'primary' | 'danger' | 'ghost';
}

export const Button: React.FC<ButtonProps> = ({
    children,
    variant = 'primary',
    className = '',
    ...props
}) => {


    // Mapping variants to classes (using the CSS classes we defined in index.css or inline styles if needed)
    // Since we are using vanilla CSS in index.css, we can just pass the variant as a class or handle it here.
    // For this refactor, I'll use the existing classes and add specific ones if needed.
    // However, to keep it simple and consistent with previous CSS:

    let variantClass = '';
    if (variant === 'danger') variantClass = 'delete-btn';

    return (
        <button
            className={`${variantClass} ${className}`}
            {...props}
        >
            {children}
        </button>
    );
};
