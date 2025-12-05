import React from "react";
import TodoApp from "./TodoApp";

const App: React.FC = () => {
  return (
    <div style={styles.container}>
      <h1 style={styles.title}>Lambda TODO App</h1>
      <TodoApp />
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    maxWidth: 520,
    margin: "0 auto",
    padding: 16,
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, sans-serif",
  },
  title: {
    textAlign: "center",
    marginBottom: 24,
  },
};

export default App;
