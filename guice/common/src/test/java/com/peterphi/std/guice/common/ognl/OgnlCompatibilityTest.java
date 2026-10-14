package com.peterphi.std.guice.common.ognl;

import ognl.Ognl;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class OgnlCompatibilityTest {
    // Downstream callers use root methods with side effects (job-manager UI actions
    // and nent transcode scripts), as well as properties and temporary variables.
    public static class ScriptRoot {
        public final Map<String, String> values = new HashMap<>();
        public void set(String key, String value) { values.put(key, value); }
        public String get(String key) { return values.get(key); }
    }

    @Test
    public void supportsStatementsAndRootMethodSideEffects() {
        ScriptRoot root = new ScriptRoot();
        new OgnlEvaluator("set('title', 'alice')").evaluate(root);
        assertEquals("ALICE", new OgnlEvaluator("(#name=get('title')), #name.toUpperCase()").getValue(root, String.class));
        assertTrue(new OgnlEvaluator("values['title'] == 'alice'").isTrue(root));
    }

    @Test
    public void supportsNentStyleMultiStatementFormatting() {
        String expression = "(#template='0000000000000'), (#revision='123'), (#title='987'), " +
                "#title + #template.substring(#title.length() + #revision.length()) + #revision";
        assertEquals("9870000000123", new OgnlEvaluator(expression).getValue(new Object(), String.class));
    }

    @Test
    public void supportsDirectOgnlConvenienceCallUsedByDeliveryCoordinator() throws Exception {
        assertEquals("alice", Ognl.getValue("get(0)", List.of("alice"), String.class));
    }

    @Test
    public void preservesResultsAfterAutomaticCompilation() {
        AtomicBoolean compiled = new AtomicBoolean();
        OgnlEvaluator evaluator = new OgnlEvaluator("length()", (expression, instance) -> compiled.set(true));
        for (int i = 0; i <= OgnlEvaluator.COMPILE_THRESHOLD + 1; i++) {
            assertEquals(5, evaluator.evaluate("alice"));
        }
        assertTrue("Expression must actually compile, rather than silently fall back", compiled.get());
        assertEquals(3, evaluator.evaluate("bob"));
    }

    @Test
    public void compiledStringAndCharacterLiteralsMatchInterpretedValues() {
        for (String expression : List.of("\"abc\"", "'x'")) {
            AtomicBoolean compiled = new AtomicBoolean();
            OgnlEvaluator evaluator = new OgnlEvaluator(expression, (text, instance) -> compiled.set(true));
            Object interpreted = evaluator.evaluate(new Object());
            evaluator.compile(new Object());
            assertTrue("Expression must actually compile: " + expression, compiled.get());
            assertEquals(interpreted, evaluator.evaluate(new Object()));
        }
    }
}
