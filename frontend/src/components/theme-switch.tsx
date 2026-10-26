"use client";

import { useEffect, useSyncExternalStore } from "react";

type Theme = "light" | "dark" | "system";
const THEMES: Theme[] = ["light", "dark", "system"];
const ICONS: Record<Theme, string> = { light: "☀", dark: "☾", system: "◐" };
const STORAGE_KEY = "theme";
const CHANGE_EVENT = "themechange";

function apply(theme: Theme) {
  const dark = theme === "dark" || (theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.classList.toggle("dark", dark);
  document.documentElement.classList.toggle("light", !dark);
}

function readStored(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    return "system";
  }
}

// The stored choice is browser state, so it's read through useSyncExternalStore:
// other tabs ("storage") and this component ("themechange") both notify subscribers.
function subscribe(onChange: () => void) {
  window.addEventListener("storage", onChange);
  window.addEventListener(CHANGE_EVENT, onChange);
  return () => {
    window.removeEventListener("storage", onChange);
    window.removeEventListener(CHANGE_EVENT, onChange);
  };
}

export function ThemeSwitch({ labels }: { labels: { label: string } & Record<Theme, string> }) {
  // null on the server, which can't know the stored choice.
  const theme = useSyncExternalStore<Theme | null>(subscribe, readStored, () => null);

  useEffect(() => {
    if (theme == null) return;
    apply(theme);
    if (theme !== "system") return;
    // Follow the device when it switches between light and dark.
    const media = matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => apply("system");
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [theme]);

  function choose(next: Theme) {
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Private mode etc.: still apply it to this page.
      apply(next);
    }
    window.dispatchEvent(new Event(CHANGE_EVENT));
  }

  return (
    <div role="radiogroup" aria-label={labels.label} className="flex rounded-md border border-line p-0.5">
      {THEMES.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={theme === option}
          title={labels[option]}
          onClick={() => choose(option)}
          className={`rounded px-2 py-0.5 text-sm ${
            theme === option ? "bg-chip font-semibold text-fg" : "text-muted hover:text-fg"
          }`}
        >
          <span aria-hidden>{ICONS[option]}</span>
          <span className="sr-only">{labels[option]}</span>
        </button>
      ))}
    </div>
  );
}
