#!/bin/bash
# Script to cache Gradle dependencies for offline builds
# Run this script with network access, then commit gradle/local-repo/

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOCAL_REPO="$PROJECT_DIR/gradle/local-repo"

echo "=== Caching Klein dependencies for offline builds ==="
echo "Project directory: $PROJECT_DIR"
echo "Local repo: $LOCAL_REPO"
echo ""

# Ensure local repo directory exists
mkdir -p "$LOCAL_REPO"

# Step 1: Run Gradle to download all dependencies to cache
echo "Step 1: Downloading all dependencies..."
cd "$PROJECT_DIR"
./gradlew dependencies --refresh-dependencies || true
./gradlew :klein-lib:dependencies --refresh-dependencies || true

# Step 2: Build to ensure all compile-time dependencies are resolved
echo ""
echo "Step 2: Running build to resolve all dependencies..."
./gradlew :klein-lib:compileKotlinJvm :klein-lib:jvmTest --dry-run || true

# Step 3: Export dependencies to local Maven repository
echo ""
echo "Step 3: Exporting to local Maven repository format..."

# Find Gradle cache location
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"

if [ -d "$GRADLE_CACHE" ]; then
    echo "Found Gradle cache at: $GRADLE_CACHE"

    # Copy artifacts in Maven repository layout
    find "$GRADLE_CACHE" -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \) | while read -r file; do
        # Extract group/artifact/version from path
        # Format: files-2.1/group.name/artifact/version/hash/filename
        rel_path="${file#$GRADLE_CACHE/}"

        # Parse the path components
        IFS='/' read -ra parts <<< "$rel_path"
        if [ ${#parts[@]} -ge 5 ]; then
            group="${parts[0]}"
            artifact="${parts[1]}"
            version="${parts[2]}"
            filename="${parts[4]}"

            # Convert group to directory structure (org.jetbrains -> org/jetbrains)
            # Use tr to avoid bash escaping issues with path separators
            group_path=$(echo "$group" | tr '.' '/')

            # Create target directory
            target_dir="$LOCAL_REPO/$group_path/$artifact/$version"
            mkdir -p "$target_dir"

            # Copy file
            cp "$file" "$target_dir/" 2>/dev/null || true
        fi
    done

    echo "Dependencies exported to $LOCAL_REPO"
else
    echo "WARNING: Gradle cache not found at $GRADLE_CACHE"
    echo "Run './gradlew build' first to populate the cache"
fi

echo ""
echo "=== Done ==="
echo ""
echo "To enable offline builds:"
echo "1. Verify the build works: ./gradlew :klein-lib:jvmTest --offline"
echo "2. Commit the local repo: git add gradle/local-repo && git commit -m 'Cache offline dependencies'"
echo ""
echo "Note: gradle/local-repo may be large (200-400MB). Consider using Git LFS for binary files."
