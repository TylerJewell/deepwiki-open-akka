# Acknowledgements

This project is a port of **[AsyncFuncAI/deepwiki-open](https://github.com/AsyncFuncAI/deepwiki-open)**.

- **Licence.** `AsyncFuncAI/deepwiki-open` is MIT-licensed, copyright (c) 2024 Sheing Ng
  (its `LICENSE` file, first two lines). This port is MIT-licensed as well.

- **The frontend (`gui/`) is copied verbatim, not derived.** `gui/src/`, `gui/public/`,
  `next.config.ts`, `tsconfig.json`, `package.json`, `postcss.config.mjs`, and
  `eslint.config.mjs` are the source's own Next.js application, copied without
  modification (RENDERING.md R3: "the interface that already exists is the one the port
  ships... it does not build its own"). Its `SERVER_BASE_URL` environment variable is
  pointed at this port's backend instead of the source's own FastAPI process; nothing
  else changed. Its own `LICENSE` file is carried into `gui/` alongside it.

- **Three small endpoints the frontend needs on every page load, reimplemented rather
  than copied:** `GET /auth/status`, `POST /auth/validate` (`io/akka/deepwiki/api/
  ShellEndpoint.java`, porting `api/routers/auth.py`'s two routes rule for rule), and
  `GET /lang/config` (the language-name table, matching `WikiPromptBuilder`'s own
  `LANGUAGE_NAMES`, which is itself ported from `api/services/wiki/prompts.py`).

- **`GET /models/config`'s response body (`src/main/resources/static-resources/
  models-config.json`) is captured verbatim from the source's own running
  `/models/config` endpoint, not reimplemented.** It is a catalogue of LLM provider and
  model names/ids — configuration data, not a rule this port's ingestion/wiki-generation
  slice (SPEC-001 §1) governs — served as a static resource so the vendored frontend's
  model-selection UI renders without erroring.

- **The two LLM prompt templates in `WikiPromptBuilder.java` (`buildStructurePrompt`,
  `buildPagePrompt`) are copied verbatim from `api/services/wiki/prompts.py`**, including
  every instruction sentence and the XML schema blocks. This is not incidental — SPEC-001
  R7 is that this port produces byte-identical prompts to the source (verified in
  `WikiPromptBuilderTest` and `bench/REPORT.md`'s checksums), so reproducing the prompt
  text exactly is the rule under test, not a shortcut around writing one.

- **Test fixtures mirroring the source's own test suite.** `WikiStructureParserTest`,
  `WikiContentPostProcessorTest`, and `RepoIdentityTest`'s XML documents, citation
  strings, and URL/path examples are the same fixtures `tests/backend/services/
  test_wiki_{structure,content}.py` and `tests/test_extract_repo_name.py` use — found by
  `python toolkit/copied_strings.py deepwiki-open --source deepwiki-open-src` and checked
  against those files. Reusing the source's own fixtures is what makes this port's tests
  a check against the *original's* stated behaviour rather than against a fixture this
  port invented and could get equally wrong on both sides.

- **Everything else `copied_strings.py` found is coincidence, not copying:** short field
  names (`comprehensive`, `importance`, `description`, `page_count`, `generated_at`,
  `repository`, `auth_required`, `supported_languages`), XML tag names (`section_ref`),
  the default exclusion-list entries in `FileFilterConfig.java` (`node_modules`,
  `__pycache__`, `bower_components`, `jspm_packages`, `packages/*/dist`, ...,
  literal filenames from `api/data/repo.json`'s own default config, reproduced because
  they *are* the configuration this port ports, not incidental text), Mermaid diagram
  syntax keywords (`participant`, `sequenceDiagram`) that are part of the copied prompt
  text above, and this port's own error strings for states the two systems both need a
  word for (`"unknown error"`, `"already indexed"`, `"task no longer available"`).

- **Is behaviour derived even where no text was copied?** Yes, throughout — the whole
  point of this port. See `specs/SPEC-001-deepwiki-open.md` §3 for the full rule-by-rule
  mapping and `docs/question-log.md` for how each was checked.

## Also used

- Akka
