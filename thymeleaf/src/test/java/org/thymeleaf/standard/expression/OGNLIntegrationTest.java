package org.thymeleaf.standard.expression;

import org.junit.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class OGNLIntegrationTest {
    private final TemplateEngine engine = createEngine();

    private static TemplateEngine createEngine() {
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        return engine;
    }

    @Test
    public void rendersPropertiesMethodsAndExpressionObjects() {
        Context context = new Context();
        context.setVariable("names", List.of("alice", "bob"));
        assertEquals("<p>ALICE:2</p>", engine.process(
                "<p th:text=\"${#strings.toUpperCase(names[0]) + ':' + names.size()}\">placeholder</p>", context));
    }

    @Test
    public void rendersAllowedExpressionObjectsInRestrictedContext() {
        Context context = new Context();
        context.setVariable("name", "alice");
        assertEquals("<p>ALICE</p>", engine.process(
                "<p th:utext=\"${#strings.toUpperCase(name)}\">placeholder</p>", context));
    }

    @Test(expected = TemplateProcessingException.class)
    public void rejectsRestrictedExpressionObjects() {
        engine.process("<p th:utext=\"${#ctx.getVariable('name')}\"></p>", new Context());
    }

    @Test(expected = TemplateProcessingException.class)
    public void rejectsRestrictedRequestParameters() {
        Context context = new Context();
        context.setVariable("param", "secret");
        engine.process("<p th:utext=\"${param.toString()}\"></p>", context);
    }

    @Test
    public void resolvesContextAndReservedRootObjects() {
        Context context = new Context();
        context.setVariable("name", "alice");
        for (String expression : List.of("#ctx.getVariable('name')", "#vars.getVariable('name')", "#root.name", "#this.name")) {
            assertEquals(expression, "<p>alice</p>", engine.process(
                    "<p th:text=\"${" + expression + "}\"></p>", context));
        }
    }

    @Test
    public void supportsTemporaryVariablesWithoutReplacingExpressionObjects() {
        assertEquals("<p>ALICE</p>", engine.process(
                "<p th:text=\"${(#name='alice'), #strings.toUpperCase(#name)}\"></p>", new Context()));
    }

    @Test(expected = TemplateProcessingException.class)
    public void rejectsReplacingExpressionObjects() {
        engine.process("<p th:text=\"${#strings='replacement'}\"></p>", new Context());
    }

    @Test(expected = TemplateProcessingException.class)
    public void rejectsForbiddenClasses() {
        engine.process("<p th:text=\"${@java.lang.Runtime@getRuntime()}\"></p>", new Context());
    }
}
