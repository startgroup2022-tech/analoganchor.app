import os
import shutil

base_dir = r"c:\com.analoganchor.app"
java_dir = os.path.join(base_dir, "app", "src", "main", "java")

old_pkg_dir = os.path.join(java_dir, "com", "example")
new_pkg_dir = os.path.join(java_dir, "com", "analoganchor", "app")

if os.path.exists(old_pkg_dir):
    os.makedirs(os.path.dirname(new_pkg_dir), exist_ok=True)
    if os.path.exists(new_pkg_dir):
        shutil.rmtree(new_pkg_dir)
    shutil.move(old_pkg_dir, new_pkg_dir)
    print(f"Moved {old_pkg_dir} to {new_pkg_dir}")

# Cleanup empty com/example if exists
empty_example = os.path.join(java_dir, "com", "example")
if os.path.exists(empty_example):
    shutil.rmtree(empty_example)

# Now replace package/import names in all files under app/src
src_dir = os.path.join(base_dir, "app", "src")

file_count = 0
for root, dirs, files in os.walk(src_dir):
    for f in files:
        if f.endswith(".kt") or f.endswith(".xml") or f.endswith(".kts"):
            path = os.path.join(root, f)
            with open(path, "r", encoding="utf-8", errors="ignore") as file:
                content = file.read()
            
            new_content = content.replace("com.example", "com.analoganchor.app")
            if content != new_content:
                with open(path, "w", encoding="utf-8") as file:
                    file.write(new_content)
                file_count += 1

print(f"Updated {file_count} files.")
