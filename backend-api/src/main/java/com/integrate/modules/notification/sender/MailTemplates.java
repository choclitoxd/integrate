package com.integrate.modules.notification.sender;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Plantillas de correo con Thymeleaf (ADR-0011 §5), en {@code resources/mail/}: {@code <código>.html},
 * {@code <código>.txt} y {@code <código>.subject.txt}. Solo el motor, sin vistas web. Las variables se escapan por
 * defecto ({@code th:text}): un nombre con HTML no se inyecta en el correo.
 */
@Component
public class MailTemplates {

    private static final Locale LOCALE = Locale.forLanguageTag("es-CO");

    private final TemplateEngine html = engine(TemplateMode.HTML, ".html");
    private final TemplateEngine text = engine(TemplateMode.TEXT, ".txt");

    public Rendered render(String templateCode, Map<String, Object> variables) {
        Context context = new Context(LOCALE, variables);
        return new Rendered(text.process(templateCode + ".subject", context).strip(),
                text.process(templateCode, context), html.process(templateCode, context));
    }

    private static TemplateEngine engine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    public record Rendered(String subject, String textBody, String htmlBody) {
    }
}
