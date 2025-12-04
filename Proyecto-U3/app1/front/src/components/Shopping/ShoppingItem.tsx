import React from 'react';
import type { ShoppingItem as ShoppingItemType } from '../../types';
import { Button } from '../ui/Button';

interface ShoppingItemProps {
    item: ShoppingItemType;
    onToggle: (id: string) => void;
    onDelete: (id: string) => void;
}

export const ShoppingItem: React.FC<ShoppingItemProps> = ({ item, onToggle, onDelete }) => {
    return (
        <li className={item.completed ? 'completed' : ''}>
            <div className="item-content" onClick={() => onToggle(item.id)}>
                <div className="checkbox"></div>
                <span className="item-text">{item.text}</span>
            </div>
            <Button
                variant="danger"
                onClick={() => onDelete(item.id)}
                aria-label="Delete item"
            >
                ✕
            </Button>
        </li>
    );
};
