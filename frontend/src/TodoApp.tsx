import React, { useEffect, useState } from "react";

type TodoItem = {
  id: string;
  title: string;
  completed: boolean;
  createdAt: string;
};

// Change this to your real API domain after deployment
const API_BASE = "https://api.visuvisualverse.com";

const TodoApp: React.FC = () => {
  const [todos, setTodos] = useState<TodoItem[]>([]);
  const [newTitle, setNewTitle] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load todos when component mounts
  useEffect(() => {
    const fetchTodos = async () => {
      try {
        setLoading(true);
        setError(null);

        const res = await fetch(`${API_BASE}/todos`);
        if (!res.ok) {
          throw new Error(`Failed to load todos: ${res.status}`);
        }

        const data = (await res.json()) as TodoItem[];
        setTodos(data);
      } catch (err: any) {
        setError(err.message || "Unknown error");
      } finally {
        setLoading(false);
      }
    };

    fetchTodos();
  }, []);

  const handleAdd = async () => {
    const title = newTitle.trim();
    if (!title) return;

    try {
      setLoading(true);
      setError(null);

      const res = await fetch(`${API_BASE}/todos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title }),
      });

      if (!res.ok) {
        throw new Error(`Create failed: ${res.status}`);
      }

      const created: TodoItem = await res.json();
      setTodos((prev) => [...prev, created]);
      setNewTitle("");
    } catch (err: any) {
      setError(err.message || "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      setLoading(true);
      setError(null);

      const res = await fetch(`${API_BASE}/todos/${id}`, {
        method: "DELETE",
      });

      if (!res.ok && res.status !== 204) {
        throw new Error(`Delete failed: ${res.status}`);
      }

      setTodos((prev) => prev.filter((t) => t.id !== id));
    } catch (err: any) {
      setError(err.message || "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div style={styles.inputRow}>
        <input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="Add a new todo"
          style={styles.input}
        />
        <button onClick={handleAdd} disabled={loading}>
          Add
        </button>
      </div>

      {loading && <p>Loading...</p>}
      {error && <p style={styles.error}>{error}</p>}

      <ul style={styles.list}>
        {todos.map((todo) => (
          <li key={todo.id} style={styles.listItem}>
            <span>{todo.title}</span>
            <button onClick={() => handleDelete(todo.id)} disabled={loading}>
              Delete
            </button>
          </li>
        ))}

        {todos.length === 0 && !loading && !error && (
          <li>No todos yet. Add your first one!</li>
        )}
      </ul>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  inputRow: {
    display: "flex",
    gap: 8,
    marginBottom: 16,
  },
  input: {
    flex: 1,
    padding: 8,
    fontSize: 16,
  },
  list: {
    listStyle: "none",
    padding: 0,
    margin: 0,
  },
  listItem: {
    display: "flex",
    justifyContent: "space-between",
    padding: "8px 0",
    borderBottom: "1px solid #eee",
  },
  error: {
    color: "red",
  },
};

export default TodoApp;
