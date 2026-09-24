import { mount } from "svelte";
import "./app.css";
import App from "./App.svelte";
import { runDemo } from "./lib/demo";

const app = mount(App, {
  target: document.getElementById("app")!,
});

void runDemo();

export default app;
