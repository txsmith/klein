#!/bin/bash
# Script to cache Gradle dependencies for offline builds
# Run this script with network access, then commit gradle/local-repo.zip

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOCAL_REPO="$PROJECT_DIR/gradle/local-repo"
LOCAL_REPO_ZIP="$PROJECT_DIR/gradle/local-repo.zip"

echo "=== Caching Klein dependencies for offline builds ==="
echo "Project directory: $PROJECT_DIR"
echo "Local repo: $LOCAL_REPO"
echo ""

# Clean up any existing local repo
rm -rf "$LOCAL_REPO"
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
    exit 1
fi

# Step 4: Create zip archive
echo ""
echo "Step 4: Creating zip archive..."
cd "$PROJECT_DIR/gradle"
rm -f local-repo.zip
zip -rq local-repo.zip local-repo
echo "Created: $LOCAL_REPO_ZIP"
echo "Size: $(du -h local-repo.zip | cut -f1)"

# Clean up the unzipped directory (only the zip is committed)
rm -rf "$LOCAL_REPO"

echo ""
echo "=== Done ==="
echo ""
echo "To enable offline builds:"
echo "1. Commit the zip: git add gradle/local-repo.zip && git commit -m 'Cache offline dependencies'"
echo "2. The build will auto-extract on first run"
echo ""
echo "To verify: ./gradlew :klein-lib:jvmTest --offline"
