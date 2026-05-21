package com.jio.eim.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MutableHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> extraHeaders = new HashMap<>();
    private final Set<String> removedHeaders = new HashSet<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        extraHeaders.put(name, value);
    }

    public void removeHeader(String name) {
        removedHeaders.add(name);
    }

    @Override
    public String getHeader(String name) {
        if (removedHeaders.contains(name)) {
            return null;
        }
        String injected = extraHeaders.get(name);
        return injected != null ? injected : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
        names.addAll(extraHeaders.keySet());
        names.removeAll(removedHeaders);
        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (removedHeaders.contains(name)) {
            return Collections.emptyEnumeration();
        }
        if (extraHeaders.containsKey(name)) {
            return Collections.enumeration(Collections.singletonList(extraHeaders.get(name)));
        }
        return super.getHeaders(name);
    }
}
