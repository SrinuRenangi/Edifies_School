# Open-Source Development Tools & Reference Repositories
## Staff Attendance Management System

This directory maintains local reference clones of open-source development tools and agent capabilities used during project engineering. These tools are strictly for development/authoring workflows and are excluded from production runtimes and container packaging.

---

### Repository Inventory

#### 1. Graphify
- **Official GitHub URL:** [https://github.com/Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify)
- **Purpose:** AST-based code relationship mapping and codebase knowledge graph navigation. Assists coding agents in inspecting module dependencies, route maps, and entity relations before modifications.
- **Exact Checked-out Commit:** `847a93f3cab5e2c82ea9170014b21cd2c52a4417`
- **Verified License:** Apache License 2.0 (Verified in `tools/open-source/graphify/LICENSE`)
- **Project-Local Skill Installed:** Yes — registered in `.agents/skills/graphify/`
- **Scope:** Development & agent navigation only (excluded via `.dockerignore`)
- **Update Instructions:**
  ```bash
  cd tools/open-source/graphify
  git fetch origin
  git checkout <tag-or-commit>
  ```

#### 2. Ponytail
- **Official GitHub URL:** [https://github.com/DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail)
- **Purpose:** Senior developer discipline engine enforcing minimal, maintainable changes (YAGNI), preventing premature abstractions and bloated dependencies, and validating trust boundaries.
- **Exact Checked-out Commit:** `09c434ab73ca43761c5d8b0c5d341926c2fba710`
- **Verified License:** MIT License (Copyright (c) 2026 DietrichGebert, verified in `tools/open-source/ponytail/LICENSE`)
- **Project-Local Skill Installed:** Yes — registered in `.agents/skills/ponytail/`
- **Scope:** Development review & agent discipline only (excluded via `.dockerignore`)
- **Update Instructions:**
  ```bash
  cd tools/open-source/ponytail
  git fetch origin
  git checkout <tag-or-commit>
  ```

---

### Production Isolation Invariant
Both repositories reside under `tools/open-source/` and are explicitly excluded from production packaging via `.dockerignore` and `.gitignore`. No source code or runtime dependencies from these tools are bundled into the Spring Boot executable JAR or Angular distribution.
