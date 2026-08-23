package io.akka.deepwiki.domain;

import java.util.List;
import java.util.Set;

/**
 * SPEC-001 R4. Ported verbatim from the shipped {@code api/data/repo.json} defaults —
 * including the glob-shaped {@code excluded_files} entries (`*.min.js`, `*.env`, ...)
 * that {@link FileTreeReader} matches by exact filename equality, not glob, per
 * question-log row 7. That is current source behaviour, not a bug this port fixes
 * (SPEC-001 §4.3).
 */
public final class FileFilterConfig {

  public static final Set<String> DEFAULT_EXCLUDED_DIRS =
      Set.of(
          ".venv", "venv", "env", "virtualenv", "node_modules", "bower_components", "jspm_packages",
          ".git", ".svn", ".hg", ".bzr", "vendor", "__pycache__", ".pytest_cache", ".mypy_cache",
          ".ruff_cache", ".coverage", "dist", "build", "out", "target", "bin", "obj", "docs", "_docs",
          "site-docs", "_site", ".idea", ".vscode", ".vs", ".eclipse", ".settings", "logs", "log",
          "tmp", "temp");

  public static final Set<String> DEFAULT_EXCLUDED_FILES =
      Set.of(
          "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json", "poetry.lock", "Pipfile.lock",
          "requirements.txt.lock", "Cargo.lock", "composer.lock", ".lock", ".DS_Store", "Thumbs.db",
          "desktop.ini", "*.lnk", ".env", ".env.*", "*.env", "*.cfg", "*.ini", ".flaskenv",
          ".gitignore", ".gitattributes", ".gitmodules", ".github", ".gitlab-ci.yml", ".prettierrc",
          ".eslintrc", ".eslintignore", ".stylelintrc", ".editorconfig", ".jshintrc", ".pylintrc",
          ".flake8", "mypy.ini", "pyproject.toml", "tsconfig.json", "webpack.config.js",
          "babel.config.js", "rollup.config.js", "jest.config.js", "karma.conf.js", "vite.config.js",
          "next.config.js", "*.min.js", "*.min.css", "*.bundle.js", "*.bundle.css", "*.map", "*.gz",
          "*.zip", "*.tar", "*.tgz", "*.rar", "*.7z", "*.iso", "*.dmg", "*.img", "*.msix", "*.appx",
          "*.appxbundle", "*.xap", "*.ipa", "*.deb", "*.rpm", "*.msi", "*.exe", "*.dll", "*.so",
          "*.dylib", "*.o", "*.obj", "*.jar", "*.war", "*.ear", "*.jsm", "*.class", "*.pyc", "*.pyd",
          "*.pyo", "__pycache__", "*.a", "*.lib", "*.lo", "*.la", "*.slo", "*.dSYM", "*.egg",
          "*.egg-info", "*.dist-info", "*.eggs", "node_modules", "bower_components", "jspm_packages",
          "lib-cov", "coverage", "htmlcov", ".nyc_output", ".tox", "dist", "build", "bld", "out",
          "bin", "target", "packages/*/dist", "packages/*/build", ".output");

  public static final List<String> CODE_EXTENSIONS =
      List.of(
          ".py", ".js", ".ts", ".java", ".cpp", ".c", ".h", ".hpp", ".go", ".rs", ".jsx", ".tsx",
          ".html", ".css", ".php", ".swift", ".cs");

  public static final List<String> DOC_EXTENSIONS = List.of(".md", ".txt", ".rst", ".json", ".yaml", ".yml");

  private FileFilterConfig() {}
}
