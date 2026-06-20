package com.katixo.ai.privacy;

import java.net.URI;
import java.util.Set;

/**
 * Tiny helper that decides whether a configured endpoint is host-local.
 *
 * <p>This is the mechanical core of the privacy guarantee: if every AI endpoint the service can
 * reach is loopback, then by construction no document bytes can travel off-host.
 */
public final class LocalhostEndpoints {

    private static final Set<String> LOCAL_HOSTS = Set.of(
            "localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private LocalhostEndpoints() {
    }

    /** @return true if {@code url}'s host is a loopback address / localhost. */
    public static boolean isLocal(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            // IPv6 literals come back bracketed, e.g. "[::1]" - normalise before lookup.
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            return LOCAL_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
