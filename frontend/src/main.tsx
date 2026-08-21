import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { App } from "./App";
import "./index.css";

const root = document.getElementById("root");
if (!root) {
  throw new Error("#root 가 없다. index.html 이 바뀌었는가");
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
