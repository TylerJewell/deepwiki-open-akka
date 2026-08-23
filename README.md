# deepwiki-open-akka

Clones or reads a repository, filters its files to the ones worth documenting, drives a
wiki-structure-and-page generation process against a pluggable language-model boundary,
turns the model's citations into real links, and tracks and caches the result.

A port of [AsyncFuncAI/deepwiki-open](https://github.com/AsyncFuncAI/deepwiki-open) onto
**Akka**.

---

## Where it came from

deepwiki-open turns a code repository into a browsable wiki: it walks the repository's
files, asks a language model to plan a wiki structure from them, asks the model again for
each page's content, and serves the result through a web interface. This port takes the
ingestion and generation pipeline — everything except the language model's own token
generation and the search index it optionally builds first, both of which are given a
pluggable boundary instead of being reimplemented.

The specifications this port was built from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `deepwiki-open-port/`.

---

## AsyncFuncAI/deepwiki-open → this port

📉 1,482 source lines (the behavioural slice) → **1,858 lines**<br>
📁 9 source files + 2 functions → **30 files**<br>
🖥️ 1 process (FastAPI + Next.js) → **1 process**<br>
🎯 checksum agreement across 4 workloads (repo names, file filtering, structure parsing, citation resolution) → **4/4**<br>
⚡ file-tree filtering, 1,330,192 ns/op → **434,896 ns/op**<br>
🎯 visual match on the reused frontend's homepage, pixel for pixel → **0 changed regions**

Full method and the numbers that did not make this list:
[`bench/REPORT.md`](../deepwiki-open-port/bench/REPORT.md).

---

## What it took to build

⏱️ **2.1 hours** from the first command to the published repository, **2.1** of them active<br>
💬 **936** exchanges with the model<br>
✍️ **562,252** tokens written by the model, **386,831,009** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **59** tests

```bash
python toolkit/tokens.py --port deepwiki-open    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](../port-log).

---

## What it does

From the specification:

- **A repository's file tree is filtered before anything else touches it.** Only files
  with a configured code or documentation extension are considered, and a directory or
  file name on the exclude list drops it, wherever that name appears in the path.
- **The wiki structure comes from one model call over the filtered tree and the
  README; each page comes from one further call over its own file list.** Nothing
  about a page's content is decided by this port — only what gets asked for and how the
  answer is parsed.
- **A malformed or truncated model response is recovered from, not rejected.**
  Missing closing tags, a stray unescaped `&`, or markdown fences around the answer are
  all repaired before parsing is given up on; only a response with no structure tag at
  all fails outright.
- **A citation with empty parentheses is resolved into a real link after the fact.**
  The model is asked to leave `()` empty and cite a path and line range inside the
  brackets; this port fills in the actual repository URL afterward, host by host.
- **A page that keeps failing to generate gets a placeholder instead of failing the
  whole wiki.** Every other page still completes.
- **Submitting the same repository twice while it is still generating joins the
  existing attempt instead of starting a second one; submitting it again after it
  finished serves the cached result instead of generating anything.**

---

## Design decisions

**The language model and the search index are both pluggable, not reimplemented.** Neither
one gives the same answer twice, and timing either would measure a model or an index
rather than this port's own logic. A caller supplies both today only as deterministic
stand-ins; wiring in a real model provider is a small, separate piece of work this port's
interfaces are shaped for but do not do.

**The wiki cache lives on disk under the machine's temp directory, the same way the original keeps it.** A generated wiki is expensive to produce and cheap to store, so losing
it on every restart would be the wrong default — and keeping it exactly where the original
does is what let the same frontend, pointed at either backend, show the same homepage.

**Two verified bugs in how the original's own code calls two of its own functions were fixed rather than copied.** One shuffles four arguments so a repository's include/exclude
filters are silently misapplied; the other hands a list of files to a function that
expects one string, leaving a Python list's punctuation inside the text sent to the model.
Copying them would mean this port's wiki generation is broken by default.

**A wildcard-shaped entry in the default exclusion list is matched as a literal filename, because that is what the original actually does.** `*.min.js` never excludes anything
in the original either — its matching function compares whole filenames, never patterns.
Reproducing that literal behaviour, rather than "fixing" it into a real wildcard, keeps
this port answering the question the original actually answers.

**The reused frontend's own environment variable is the entire integration point.** The original's Next.js app already reads every backend call — including its SSE progress
stream — through one `SERVER_BASE_URL` setting; pointing that at this port's backend
needed no frontend code changes at all, which is what let the homepage comparison below
be pixel-exact rather than approximate.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- Node.js 20 or newer and npm, only for the frontend in `gui/`

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9066**.

### Run the reused frontend

```bash
cd gui
npm install
SERVER_BASE_URL=http://localhost:9066 npm run dev
```

Open the address it prints. The frontend is the original project's own — see
"Where it differs" below for the one visual comparison this port could not complete
without a language-model key.

### Try the API directly

```bash
curl -X POST http://localhost:9066/wiki/tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"/path/to/a/local/repo","type":"local","owner":"me","repo":"myrepo"}'

curl http://localhost:9066/wiki/tasks/local_me_myrepo
```

---

## Model providers

This port never calls a language model directly. `ContentGenerationClient` is the
boundary a real provider integration would implement; the version shipped here
(`StubContentGenerationClient`) returns fixed, deterministic text so the pipeline around
it — parsing, retry, citation resolution, caching — has real input to run against
without a key, a network call, or a bill.

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `akka.javasdk.dev-mode.http-port` | `9066` | set in `application.conf` |
| `DEEPWIKI_AUTH_MODE` | `false` | gates `DELETE /api/wiki_cache` behind `DEEPWIKI_AUTH_CODE`, matching the original |
| `DEEPWIKI_AUTH_CODE` | empty | the code that gate checks against |
| Concurrent repo tasks, page concurrency, page retries, task TTL | half the CPU count, 1, 2, 300 seconds | hardcoded to the original's own defaults; the original reads these from environment variables of its own, not reproduced here |

---

## Where it differs from AsyncFuncAI/deepwiki-open

Everything not listed here behaves the same way on purpose, including the parts that
look like mistakes.

- **Two argument-order bugs in the original's own call sites are fixed, not copied.**
  The original's wiki-generation step calls its file-filtering function with four
  arguments in the wrong order, so a request's include/exclude filters are silently
  reinterpreted as something else; it separately hands that function's list of files to
  a prompt-building function that expects a single string, leaving Python's list
  punctuation inside the text sent to the model. Both are verified by running the
  original's own code with and without the bug present. This port calls the equivalent
  functions correctly.
- **A default exclusion list entry that looks like a wildcard is matched literally, not
  as a wildcard, because that is what the original actually does.** The original ships
  `*.min.js` and similar entries in its default exclusion list, but its matching
  function compares filenames for exact equality, never as a pattern — so a file named
  `app.min.js` is never excluded by that entry. Verified by running the original's own
  matching function. This port reproduces the literal-match behaviour rather than
  "fixing" it, since nothing indicates the original considers it broken.
- **The language model call and the search index are pluggable interfaces with
  deterministic stand-ins, not real integrations.** See "Design decisions" above.
- **The heartbeat mechanism on the repository-preparation endpoint is not reproduced.**
  The original sends a periodic keep-alive message while its search index builds, which
  can take a long time against a real embedding provider. This port's index step is a
  fast, deterministic stand-in, so the endpoint returns a single result instead of a
  stream of heartbeats.
- **A wiki-generation task that disappears from the registry mid-stream ends the
  progress stream silently, rather than sending the original's explicit "task no
  longer available" message.** This only happens to a task that finished several
  minutes ago and was already cleaned up; the ordinary progress-to-completion path is
  unchanged.
- **A repository name or citation language is accepted as given, without checking it
  against a list of supported languages.** The original validates or substitutes an
  unsupported language code on some of its routes; this port treats the value as an
  opaque token throughout, since the list of supported languages is configuration data
  outside this port's ingestion/generation slice.
- **The reused frontend's wiki-viewing screen (once a wiki has actually finished
  generating) was not compared against the original visually.** Its homepage was —
  pixel for pixel, with zero changed regions, driven by the same frontend code pointed
  at each backend in turn. Generating real, comparable content for the finished-wiki
  screen needs a working language-model provider key on the original side, which this
  environment does not have; see `gui/manifest.json` for exactly what was and was not
  checked.

---

## Licence

AsyncFuncAI/deepwiki-open is MIT-licensed, © 2024 Sheing Ng. This port reimplements its
ingestion and generation pipeline and reuses its frontend verbatim; see
`ACKNOWLEDGEMENTS.md`.
