#!/usr/bin/env bash
# Shared helpers for Little Chemistry worktree scripts.

WORKTREE_SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)"
WORKTREE_CHECKOUT_ROOT="$(dirname "$(dirname "$WORKTREE_SCRIPT_DIR")")"
WORKTREE_COMMON_GIT_DIR="$(git -C "$WORKTREE_CHECKOUT_ROOT" rev-parse --path-format=absolute --git-common-dir)"
LITTLE_CHEMISTRY_ROOT="$(cd "$(dirname "$WORKTREE_COMMON_GIT_DIR")" && pwd -P)"
LITTLE_CHEMISTRY_WORKTREES_DIR="$LITTLE_CHEMISTRY_ROOT/.worktrees"

worktree_die() {
  printf '\n  ✗ %s\n\n' "$1" >&2
  exit 1
}

resolve_worktree_dir() {
  local input="${1:-}"
  local candidate=""

  [[ -n "$input" ]] || worktree_die "Usage: <worktree-name|path>"

  if [[ "$input" == /* ]]; then
    candidate="$input"
  elif [[ "$input" == .worktrees/* ]]; then
    candidate="$LITTLE_CHEMISTRY_ROOT/$input"
  else
    candidate="$LITTLE_CHEMISTRY_WORKTREES_DIR/$input"
  fi

  candidate="$(realpath -m -- "$candidate")"
  case "$candidate" in
    "$LITTLE_CHEMISTRY_WORKTREES_DIR"/*) ;;
    *) worktree_die "Worktree must be inside $LITTLE_CHEMISTRY_WORKTREES_DIR: $candidate" ;;
  esac

  printf '%s\n' "$candidate"
}

registered_worktree_branch() {
  local worktree_dir="$1"

  git -C "$LITTLE_CHEMISTRY_ROOT" worktree list --porcelain | awk -v target="$worktree_dir" '
    $1 == "worktree" { in_target = ($0 == "worktree " target); next }
    in_target && $1 == "branch" {
      sub(/^refs\/heads\//, "", $2)
      print $2
      exit
    }
  '
}

is_registered_worktree() {
  local worktree_dir="$1"
  git -C "$LITTLE_CHEMISTRY_ROOT" worktree list --porcelain |
    grep -Fqx "worktree $worktree_dir"
}

prepare_gradle_worktree() {
  local worktree_dir="$1"
  local marker="$worktree_dir/.gradle/little-chemistry-worktree-ready"

  [[ -f "$worktree_dir/gradlew" ]] || worktree_die "No Gradle wrapper in $worktree_dir"
  [[ -x "$worktree_dir/gradlew" ]] || chmod +x "$worktree_dir/gradlew"
  [[ -f "$marker" ]] && return 0

  # Gradle has no checkout-local dependency installation step. Running `help`
  # validates the wrapper/JDK/build configuration and primes Gradle's shared user
  # cache without compiling the mod, running Minecraft, or installing into Prism.
  (cd "$worktree_dir" && ./gradlew --no-daemon --quiet help)
  mkdir -p "$(dirname "$marker")"
  touch "$marker"
}

processes_using_worktree() {
  local worktree_dir="$1"
  local proc pid cwd cmd env_line found

  for proc in /proc/[0-9]*; do
    [[ -d "$proc" ]] || continue
    pid="${proc##*/}"
    found=0

    cwd="$(readlink "$proc/cwd" 2>/dev/null || true)"
    if [[ "$cwd" == "$worktree_dir" || "$cwd" == "$worktree_dir"/* ]]; then
      found=1
    fi

    if [[ $found -eq 0 && -r "$proc/environ" ]]; then
      while IFS= read -r env_line; do
        case "$env_line" in
          "PWD=$worktree_dir"|"PWD=$worktree_dir"/*)
            found=1
            break
            ;;
        esac
      done < <({ tr '\0' '\n' < "$proc/environ"; } 2>/dev/null || true)
    fi

    if [[ $found -eq 1 ]]; then
      cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
      printf '    %s %s\n' "$pid" "${cmd:-?}"
    fi
  done
}

cleanup_worktree_config() {
  # Build output, Gradle state, optional local.properties, and Minecraft run data
  # all live inside the linked checkout and disappear with it. Only stale Git
  # metadata and an empty worktree container can remain.
  git -C "$LITTLE_CHEMISTRY_ROOT" worktree prune >/dev/null 2>&1 || true
  rmdir "$LITTLE_CHEMISTRY_WORKTREES_DIR" 2>/dev/null || true
}
