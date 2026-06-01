# Contributing to Backpack 🎒

Thank you for your interest in contributing to Backpack! We welcome contributions from the community to help make Android database backups easier, safer, and more secure.

Please take a moment to review the guidelines below before you get started.

---

## How to Contribute

### 1. Reporting Bugs
If you find a bug:
* Search the existing [Issues](https://github.com/vedraj360/Backpack/issues) to see if it has already been reported.
* If not, open a new issue. Please include:
  * A clear description of the bug.
  * Steps to reproduce the issue.
  * Device/Android version details.
  * Relevant stack traces or logcat output.

### 2. Suggesting Features
If you have ideas for new features or improvements:
* Open an issue with the tag `enhancement`.
* Explain the use case and why this feature would benefit developers using the library.

### 3. Submitting Pull Requests
We accept pull requests! To submit a change:
1. **Fork** the repository and create your branch from `master`.
2. Use descriptive branch names (e.g., `feature/backup-encryption-improvement` or `bugfix/oauth-connection-leak`).
3. If you've added new code, make sure to add comments explaining the functionality where appropriate.
4. Ensure the demo app compiles and runs successfully with your changes.
5. Open a Pull Request detailing the changes made and the motivation behind them.

---

## Coding Standards & Style

* **Language:** The project is written 100% in Kotlin.
* **Code Style:** Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and [Android Code Style Guidelines](https://source.android.com/docs/setup/contribute/code-style).
* **Architecture:** Backpack uses Kotlin Coroutines and Flows for async operations. Ensure new features leverage asynchronous APIs non-blockingly.

---

## License

By contributing to Backpack, you agree that your contributions will be licensed under the project's Apache License, Version 2.0.
