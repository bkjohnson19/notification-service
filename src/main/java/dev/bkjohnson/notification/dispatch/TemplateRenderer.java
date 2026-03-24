package dev.bkjohnson.notification.dispatch;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders Thymeleaf templates stored in the database with provided variables.
 */
@Component
public class TemplateRenderer {

  private final SpringTemplateEngine templateEngine;

  public TemplateRenderer(
      @Qualifier("notificationTemplateEngine") SpringTemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  /**
   * Renders a Thymeleaf template string with the given variables.
   *
   * @param templateBody the Thymeleaf template content
   * @param variables    the template variables
   * @return the rendered output
   */
  public String render(String templateBody, Map<String, Object> variables) {
    Context context = new Context();
    if (variables != null) {
      context.setVariables(variables);
    }
    return templateEngine.process(templateBody, context);
  }
}
