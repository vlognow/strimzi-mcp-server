package io.seequick.mcp;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Detects the available API version for Strimzi KafkaTopic/KafkaUser resources.
 *
 * <p>Strimzi operator &le; 0.40.0 only registers {@code kafka.strimzi.io/v1beta2}.
 * Newer operators also register {@code v1}. The Strimzi API library annotates
 * {@code KafkaTopic} and {@code KafkaUser} with {@code @Version("v1")}, which causes
 * Fabric8 to build URLs against the v1 endpoint — resulting in 404s on older clusters.
 *
 * <p>Detection order:
 * <ol>
 *   <li>Env var {@value #ENV_VAR} if set (allows operator override)</li>
 *   <li>K8s API discovery: check whether {@code kafkatopics} exists under
 *       {@code kafka.strimzi.io/v1}</li>
 *   <li>Fall back to {@code v1beta2}</li>
 * </ol>
 */
public class StrimziApiVersionDetector {

    public static final String ENV_VAR = "STRIMZI_API_VERSION";
    static final String V1 = "v1";
    static final String V1BETA2 = "v1beta2";

    private StrimziApiVersionDetector() {}

    public static String detect(KubernetesClient client) {
        String envOverride = System.getenv(ENV_VAR);
        if (envOverride != null && !envOverride.isBlank()) {
            return envOverride.trim();
        }
        try {
            var apiResources = client.getApiResources("kafka.strimzi.io/" + V1);
            if (apiResources != null && apiResources.getResources() != null
                    && apiResources.getResources().stream()
                            .anyMatch(r -> "kafkatopics".equals(r.getName()))) {
                return V1;
            }
        } catch (Exception ignored) {}
        return V1BETA2;
    }
}
