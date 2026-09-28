"use strict";

module.exports = [
  {
    ignores: ["node_modules/**"],
  },
  {
    files: ["**/*.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "commonjs",
      globals: {
        console: "readonly",
        __dirname: "readonly",
        Buffer: "readonly",
        AbortSignal: "readonly",
        structuredClone: "readonly",
        exports: "writable",
        fetch: "readonly",
        Intl: "readonly",
        process: "readonly",
        require: "readonly",
      },
    },
    rules: {
      "no-undef": "error",
      "no-unused-vars": ["error", {argsIgnorePattern: "^_"}],
    },
  },
];
