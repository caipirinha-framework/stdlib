/*
 * =============================================================================
 *
 *   Copyright (c) 2011-2026 Thymeleaf (http://www.thymeleaf.org)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * =============================================================================
 */
package org.thymeleaf.web.servlet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Locale;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServletWebExchangeLocaleTest {

    @Test
    public void usesRequestLocaleUnlessExplicitlyResolved() {
        ServletContext servletContext = proxy(ServletContext.class, (target, method, args) -> null);
        Locale requestLocale = Locale.forLanguageTag("es-ES");
        HttpServletRequest request = proxy(HttpServletRequest.class, (target, method, args) -> {
            switch (method.getName()) {
                case "getServletContext": return servletContext;
                case "getLocale": return requestLocale;
                default: return null;
            }
        });
        HttpServletResponse response = proxy(HttpServletResponse.class, (target, method, args) ->
                method.getName().equals("getLocale") ? Locale.US : null);

        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        assertEquals(requestLocale, application.buildExchange(request, response).getLocale());
        assertEquals(requestLocale, application.buildExchange(request, response, null).getLocale());
        assertEquals(Locale.FRANCE, application.buildExchange(request, response, Locale.FRANCE).getLocale());
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
