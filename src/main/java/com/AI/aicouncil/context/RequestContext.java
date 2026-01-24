package com.AI.aicouncil.context;

public class RequestContext {

    private static final ThreadLocal<String> IDENTITY = new ThreadLocal<>();

    public static void setIdentity(String identity) {
        IDENTITY.set(identity);
    }

    public static String getIdentity() {
        return IDENTITY.get();
    }

    public static void clear() {
        IDENTITY.remove();
    }
}
