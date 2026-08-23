package io.akka.deepwiki.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpResponses;
import java.util.Map;

/**
 * The small pieces of the source's own surface that the reused frontend (RENDERING.md
 * R3/R7 — see gui/manifest.json) calls on every page load but that are not this port's
 * ingestion/wiki-generation slice (SPEC-001 §1): auth gating status, language list, and
 * model/provider metadata. Ports of {@code api/routers/{auth,system}.py}'s two small
 * routes; {@code /models/config} is captured verbatim from the source once rather than
 * reimplemented — provider/model catalogs are configuration data, not this port's
 * capability — and declared in ACKNOWLEDGEMENTS.md.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("")
public class ShellEndpoint {

  public record ValidateRequest(String code) {}

  @Get("/auth/status")
  public HttpResponse authStatus() {
    return HttpResponses.ok(Map.of("auth_required", WikiAuth.MODE_ENABLED));
  }

  @Post("/auth/validate")
  public HttpResponse authValidate(ValidateRequest request) {
    return HttpResponses.ok(Map.of("success", WikiAuth.CODE.equals(request.code())));
  }

  @Get("/lang/config")
  public HttpResponse langConfig() {
    return HttpResponses.ok(
        Map.of(
            "supported_languages",
            Map.ofEntries(
                Map.entry("en", "English"),
                Map.entry("ja", "Japanese (日本語)"),
                Map.entry("zh", "Mandarin Chinese (中文)"),
                Map.entry("zh-tw", "Traditional Chinese (繁體中文)"),
                Map.entry("es", "Spanish (Español)"),
                Map.entry("kr", "Korean (한국어)"),
                Map.entry("vi", "Vietnamese (Tiếng Việt)"),
                Map.entry("pt-br", "Brazilian Portuguese (Português Brasileiro)"),
                Map.entry("fr", "Français (French)"),
                Map.entry("ru", "Русский (Russian)")),
            "default",
            "en"));
  }

  @Get("/models/config")
  public HttpResponse modelsConfig() {
    return HttpResponses.staticResource("models-config.json");
  }
}
