package dev.bkjohnson.notification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class TemplateRendererTest {

  private TemplateRenderer renderer;

  @BeforeEach
  void setUp() {
    SpringTemplateEngine engine = new SpringTemplateEngine();
    StringTemplateResolver resolver = new StringTemplateResolver();
    resolver.setTemplateMode(TemplateMode.HTML);
    engine.setTemplateResolver(resolver);
    renderer = new TemplateRenderer(engine);
  }

  @Test
  void testRenderWithVariables() {
    String template = "<p th:text=\"${name}\">placeholder</p>";
    Map<String, Object> variables = Map.of("name", "Alice");

    String result = renderer.render(template, variables);

    assertThat(result).contains("<p>Alice</p>");
    assertThat(result).doesNotContain("placeholder");
  }

  @Test
  void testRenderWithAllVariableTypes() {
    String template = "<div>"
        + "<span th:text=\"${name}\"></span>"
        + "<span th:text=\"${count}\"></span>"
        + "<span th:text=\"${date}\"></span>"
        + "<ul><li th:each=\"item : ${items}\" th:text=\"${item}\"></li></ul>"
        + "</div>";

    Map<String, Object> variables = Map.of(
        "name", "Bob",
        "count", 42,
        "date", LocalDate.of(2026, 3, 23),
        "items", List.of("alpha", "beta", "gamma")
    );

    String result = renderer.render(template, variables);

    assertThat(result).contains("Bob");
    assertThat(result).contains("42");
    assertThat(result).contains("2026-03-23");
    assertThat(result).contains("alpha");
    assertThat(result).contains("beta");
    assertThat(result).contains("gamma");
  }

  @Test
  void testRenderMissingVariables() {
    String template = "<p th:text=\"${missing}\">default</p>";
    Map<String, Object> variables = Map.of();

    String result = renderer.render(template, variables);

    // Thymeleaf replaces body with empty string when variable is null
    assertThat(result).contains("<p></p>");
  }

  @Test
  void testRenderNullVariables() {
    String template = "<p>Hello</p>";

    String result = renderer.render(template, null);

    assertThat(result).contains("<p>Hello</p>");
  }

  @Test
  void testRenderEmptyTemplate() {
    String result = renderer.render("", Map.of());

    assertThat(result).isEmpty();
  }

  @Test
  void testRenderHtmlEscaping() {
    String template = "<p th:text=\"${userInput}\"></p>";
    Map<String, Object> variables = Map.of(
        "userInput", "<script>alert('xss')</script>"
    );

    String result = renderer.render(template, variables);

    assertThat(result).doesNotContain("<script>");
    assertThat(result).contains("&lt;script&gt;");
    assertThat(result).contains("&lt;/script&gt;");
  }
}
