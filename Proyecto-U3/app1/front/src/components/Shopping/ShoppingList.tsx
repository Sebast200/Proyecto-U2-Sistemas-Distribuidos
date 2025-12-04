import React, { useState } from 'react';
import type { ShoppingList as ShoppingListType } from '../../types';
import { ShoppingItem } from './ShoppingItem';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';

interface ShoppingListProps {
    list: ShoppingListType;
    onAddItem: (text: string) => void;
    onToggleItem: (id: string) => void;
    onDeleteItem: (id: string) => void;
    onDeleteList: (id: string) => void;
    onUpdateList: (id: string, newName: string) => void;
    onBack: () => void;
}

export const ShoppingList: React.FC<ShoppingListProps> = ({
    list,
    onAddItem,
    onToggleItem,
    onDeleteItem,
    onDeleteList,
    onUpdateList,
    onBack,
}) => {
    const [inputValue, setInputValue] = useState('');
    const [isEditingName, setIsEditingName] = useState(false);
    const [editedName, setEditedName] = useState(list.name);

    const addItem = (e: React.FormEvent) => {
        e.preventDefault();
        if (!inputValue.trim()) return;
        onAddItem(inputValue.trim());
        setInputValue('');
    };

    const handleNameDoubleClick = () => {
        setIsEditingName(true);
        setEditedName(list.name);
    };

    const handleNameSave = () => {
        if (editedName.trim() && editedName !== list.name) {
            onUpdateList(list.id, editedName.trim());
        }
        setIsEditingName(false);
    };

    const handleNameKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            handleNameSave();
        } else if (e.key === 'Escape') {
            setIsEditingName(false);
            setEditedName(list.name);
        }
    };

    return (
        <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
                    <Button onClick={onBack} style={{ marginRight: '1rem' }}>
                        &larr; Back
                    </Button>
                    {isEditingName ? (
                        <Input
                            type="text"
                            value={editedName}
                            onChange={(e) => setEditedName(e.target.value)}
                            onBlur={handleNameSave}
                            onKeyDown={handleNameKeyDown}
                            autoFocus
                            style={{ fontSize: '1.5rem', fontWeight: 'bold', maxWidth: '400px' }}
                        />
                    ) : (
                        <h2
                            style={{ margin: 0, cursor: 'pointer', userSelect: 'none' }}
                            onDoubleClick={handleNameDoubleClick}
                            title="Double-click to edit"
                        >
                            {list.name}
                        </h2>
                    )}
                </div>
                <Button
                    onClick={() => onDeleteList(list.id)}
                    style={{
                        backgroundColor: 'var(--danger, #dc3545)',
                        color: 'white'
                    }}
                >
                    Delete List
                </Button>
            </div>

            <form className="input-group" onSubmit={addItem}>
                <Input
                    type="text"
                    placeholder="What do you need to buy?"
                    value={inputValue}
                    onChange={(e) => setInputValue(e.target.value)}
                />
                <Button type="submit">Add Item</Button>
            </form>

            <ul>
                {list.items.map((item) => (
                    <ShoppingItem
                        key={item.id}
                        item={item}
                        onToggle={onToggleItem}
                        onDelete={onDeleteItem}
                    />
                ))}
            </ul>

            {list.items.length === 0 && (
                <p style={{ color: 'var(--text-muted)', marginTop: '2rem' }}>
                    Your list is empty. Add some items!
                </p>
            )}
        </div>
    );
};
