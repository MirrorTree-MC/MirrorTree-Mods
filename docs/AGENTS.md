# Documentation maintenance

Follow the root [AGENTS.md](../AGENTS.md).

- `INSTALLATION.md` is the user installation, upgrade and compatibility guide; `UPSTREAM.md` records lasting provenance and license facts. Link to these instead of duplicating development instructions.
- `skill-tree-source.json` is a live byte-verification input for `tools/verify_distribution.py`, not a development log. Preserve it and the source skill-tree JSON; do not regenerate hashes to conceal resource changes.
- Keep implementation invariants and targeted test commands in module/tool AGENTS.md files. Remove superseded plans and execution diaries after retaining their lasting constraints; test logs and results belong in ignored build output.
- When moving or removing documentation, update incoming links and check local targets. Do not remove licenses, compatibility fixtures, example datapacks or actual source data.
