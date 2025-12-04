export interface ShoppingItem {
    id: string;
    text: string;
    completed: boolean;
}

export interface ShoppingList {
    id: string;
    name: string;
    items: ShoppingItem[];
}

