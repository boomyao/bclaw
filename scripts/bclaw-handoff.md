# bclaw-handoff

Print a `bclaw1://` connection string for the Android client and copy it to the clipboard on macOS.

Example:

```bash
openssl rand -hex 32 > ~/.codex/ws.token && scripts/bclaw-handoff --host ws://100.101.102.103:8765 --cwd ~/projects/foo
```

Options:

- `--host <ws://host:port>`: required
- `--token-file <path>`: optional, defaults to `~/.codex/ws.token`
- `--cwd <abs-path>`: optional and repeatable
