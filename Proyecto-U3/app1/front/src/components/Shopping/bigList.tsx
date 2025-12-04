import React from 'react';
import type { ShoppingList as ShoppingListType } from '../../types';

interface BigListProps {
    lists: ShoppingListType[];
    onSelect: (id: string) => void;
    onAddList: () => void;
}

export const BigList: React.FC<BigListProps> = ({ lists, onSelect, onAddList }) => {
    return (
        <div className="shopping-grid">
            {lists.map((list) => (
                <div
                    key={list.id}
                    className="card"
                    onClick={() => onSelect(list.id)}
                    style={{ cursor: 'pointer' }}
                >
                    <h2>{list.name}</h2>
                    <ul>
                        {list.items.slice(0, 3).map((subItem) => (
                            <li key={subItem.id}>
                                <span
                                    style={{ textDecoration: subItem.completed ? 'line-through' : 'none' }}
                                >
                                    {subItem.text}
                                </span>
                            </li>
                        ))}
                        {list.items.length > 3 && (
                            <li style={{ justifyContent: 'center', color: 'var(--text-muted)' }}>
                                +{list.items.length - 3} more...
                            </li>
                        )}
                    </ul>
                </div>
            ))}
            <div
                className="card"
                onClick={onAddList}
                style={{
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: '200px',
                    borderStyle: 'dashed',
                    opacity: 0.7
                }}
            >
                <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>+</div>
                    <h3>Add New List</h3>
                </div>
            </div>
        </div>
    );
};