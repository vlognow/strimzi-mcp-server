package io.seequick.mcp;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily creates and caches KubernetesClient instances per kubeconfig context.
 */
public class KubernetesClientResolver {

    private static final String DEFAULT_KEY = "__default__";
    private final ConcurrentHashMap<String, KubernetesClient> clients = new ConcurrentHashMap<>();

    public KubernetesClient resolve(String context) {
        String key = (context != null && !context.isBlank()) ? context : DEFAULT_KEY;
        return clients.computeIfAbsent(key, this::createClient);
    }

    public KubernetesClient resolveDefault() {
        return resolve(null);
    }

    private KubernetesClient createClient(String key) {
        if (DEFAULT_KEY.equals(key)) {
            return new KubernetesClientBuilder().build();
        }
        Config config = Config.autoConfigure(key);
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    /**
     * Returns a resolver that always returns the given client, regardless of context.
     * Useful for testing with mock clients.
     */
    public static KubernetesClientResolver fixed(KubernetesClient client) {
        return new KubernetesClientResolver() {
            @Override
            public KubernetesClient resolve(String context) {
                return client;
            }

            @Override
            public KubernetesClient resolveDefault() {
                return client;
            }
        };
    }
}
