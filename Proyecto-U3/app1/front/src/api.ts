import type { ShoppingList, ShoppingItem } from './types';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:3000';


export const api = {
    async getLists(): Promise<ShoppingList[]> {
        const response = await fetch(`${API_URL}/lists`);
        if (!response.ok) throw new Error('Failed to fetch lists ' + response.status);
        const lists = await response.json();

        // We need to fetch items for each list to match the ShoppingList type
        // In a real app, we might want a "get lists with items" endpoint or load items lazily
        // For now, we'll fetch items for all lists
        const listsWithItems = await Promise.all(lists.map(async (list: any) => {
            const itemsResponse = await fetch(`${API_URL}/items?list_id=${list.id}`);
            const items = await itemsResponse.json();
            return {
                id: String(list.id),
                name: list.name,
                items: items.map((item: any) => ({
                    id: String(item.id),
                    text: item.description,
                    completed: Boolean(item.completed)
                }))
            };
        }));

        return listsWithItems;
    },

    async createList(name: string): Promise<ShoppingList> {
        const response = await fetch(`${API_URL}/lists`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name }),
        });
        if (!response.ok) throw new Error('Failed to create list');
        const data = await response.json();
        return {
            id: String(data.id),
            name: data.name,
            items: []
        };
    },

    async updateList(id: string, name: string): Promise<void> {
        const response = await fetch(`${API_URL}/lists/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name }),
        });
        if (!response.ok) throw new Error('Failed to update list');
    },

    async deleteList(id: string): Promise<void> {
        const response = await fetch(`${API_URL}/lists/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Failed to delete list');
    },

    async createItem(listId: string, text: string): Promise<ShoppingItem> {
        const response = await fetch(`${API_URL}/items`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description: text, list_id: Number(listId) }),
        });
        if (!response.ok) throw new Error('Failed to create item');
        const data = await response.json();
        return {
            id: String(data.id),
            text: data.description,
            completed: Boolean(data.completed)
        };
    },

    async updateItem(id: string, updates: { text?: string; completed?: boolean }): Promise<void> {
        const body: any = {};
        if (updates.text !== undefined) body.description = updates.text;
        if (updates.completed !== undefined) body.completed = updates.completed;

        const response = await fetch(`${API_URL}/items/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        if (!response.ok) throw new Error('Failed to update item');
    },

    async deleteItem(id: string): Promise<void> {
        const response = await fetch(`${API_URL}/items/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Failed to delete item');
    }
};
