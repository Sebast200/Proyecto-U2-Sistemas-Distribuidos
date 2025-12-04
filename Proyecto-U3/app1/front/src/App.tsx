import { useState, useEffect } from 'react';
import './App.css';
import { ShoppingList } from './components/Shopping/ShoppingList';
import { BigList } from './components/Shopping/bigList';
import type { ShoppingList as ShoppingListType } from './types';
import { api } from './api';

function App() {
  const [lists, setLists] = useState<ShoppingListType[]>([]);
  const [activeListId, setActiveListId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadLists();
  }, []);

  const loadLists = async () => {
    try {
      setLoading(true);
      const data = await api.getLists();
      setLists(data);
      setError(null);
    } catch (err) {
      setError('Failed to load lists');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const activeList = activeListId ? lists.find((l) => l.id === activeListId) : null;

  const handleAddItem = async (text: string) => {
    if (!activeListId) return;
    try {
      const newItem = await api.createItem(activeListId, text);
      setLists(
        lists.map((list) =>
          list.id === activeListId
            ? { ...list, items: [newItem, ...list.items] }
            : list
        )
      );
    } catch (err) {
      console.error('Failed to add item', err);
    }
  };

  const handleToggleItem = async (itemId: string) => {
    if (!activeListId) return;
    const list = lists.find(l => l.id === activeListId);
    const item = list?.items.find(i => i.id === itemId);
    if (!item) return;

    try {
      await api.updateItem(itemId, { completed: !item.completed });
      setLists(
        lists.map((list) =>
          list.id === activeListId
            ? {
              ...list,
              items: list.items.map((item) =>
                item.id === itemId ? { ...item, completed: !item.completed } : item
              ),
            }
            : list
        )
      );
    } catch (err) {
      console.error('Failed to toggle item', err);
    }
  };

  const handleDeleteItem = async (itemId: string) => {
    if (!activeListId) return;
    try {
      await api.deleteItem(itemId);
      setLists(
        lists.map((list) =>
          list.id === activeListId
            ? { ...list, items: list.items.filter((item) => item.id !== itemId) }
            : list
        )
      );
    } catch (err) {
      console.error('Failed to delete item', err);
    }
  };

  const handleAddList = async () => {
    const name = `Shopping List ${lists.length + 1}`;
    try {
      const newList = await api.createList(name);
      setLists([...lists, newList]);
    } catch (err) {
      console.error('Failed to create list', err);
    }
  };

  const handleDeleteList = async (listId: string) => {
    try {
      await api.deleteList(listId);
      setLists(lists.filter((list) => list.id !== listId));
      setActiveListId(null);
    } catch (err) {
      console.error('Failed to delete list', err);
    }
  };

  const handleUpdateList = async (listId: string, newName: string) => {
    try {
      await api.updateList(listId, newName);
      setLists(lists.map((list) =>
        list.id === listId ? { ...list, name: newName } : list
      ));
    } catch (err) {
      console.error('Failed to update list', err);
    }
  };

  if (loading && lists.length === 0) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <>
      <h1>Shopping List</h1>
      {activeList ? (
        <ShoppingList
          list={activeList}
          onAddItem={handleAddItem}
          onToggleItem={handleToggleItem}
          onDeleteItem={handleDeleteItem}
          onDeleteList={handleDeleteList}
          onUpdateList={handleUpdateList}
          onBack={() => setActiveListId(null)}
        />
      ) : (
        <BigList lists={lists} onSelect={setActiveListId} onAddList={handleAddList} />
      )}
    </>
  );
}

export default App;
