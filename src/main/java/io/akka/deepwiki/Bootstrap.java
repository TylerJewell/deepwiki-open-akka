package io.akka.deepwiki;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import io.akka.deepwiki.application.MarkerFileRepositoryIndexer;
import io.akka.deepwiki.application.RepositoryIndexer;
import io.akka.deepwiki.application.StubContentGenerationClient;
import io.akka.deepwiki.application.WikiCacheStore;
import io.akka.deepwiki.application.WikiTaskOrchestrator;
import java.nio.file.Path;
import java.util.Map;

/**
 * Wires the pluggable content-generation and repository-index boundaries (SPEC-001 §4.1)
 * to their deterministic stand-ins, and shares one {@link WikiTaskOrchestrator} and
 * {@link WikiCacheStore} across every endpoint instance the SDK creates.
 *
 * <p>Storage lives under the OS temp directory, matching the source's own
 * {@code deepwiki_root()} convention (api/utils.py) — a real, persistent-across-restarts
 * cache, not a per-test sandbox. A port-log finding (E-4) traced an apparent flaky
 * integration test back to exactly this: successive local runs share the same wiki cache
 * files, so a repo name a previous run already generated short-circuits to {@code
 * fromCache=true} on a later run (SPEC-001 R11 working as intended) — tests use a
 * randomly-suffixed owner/repo per invocation to avoid colliding with themselves.
 */
@Setup
public class Bootstrap implements ServiceSetup {

  public static final class Runtime {
    private static final Path DATA_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "deepwiki-open-akka");

    public static final WikiCacheStore CACHE_STORE = new WikiCacheStore(DATA_ROOT.resolve("wikicache"));
    public static final RepositoryIndexer INDEXER = new MarkerFileRepositoryIndexer(DATA_ROOT.resolve("index"));
    public static final WikiTaskOrchestrator ORCHESTRATOR =
        new WikiTaskOrchestrator(
            new StubContentGenerationClient(),
            INDEXER,
            CACHE_STORE,
            DATA_ROOT.resolve("repo"),
            Math.max(1, java.lang.Runtime.getRuntime().availableProcessors() / 2),
            1,
            2,
            300);

    private Runtime() {}
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    Map<Class<?>, Object> beans =
        Map.of(WikiTaskOrchestrator.class, Runtime.ORCHESTRATOR, WikiCacheStore.class, Runtime.CACHE_STORE, RepositoryIndexer.class, Runtime.INDEXER);
    return new DependencyProvider() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> T getDependency(Class<T> clazz) {
        return (T) beans.get(clazz);
      }
    };
  }
}
