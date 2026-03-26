package io.seequick.mcp;

/**
 * Holds the detected Strimzi CRD API version for resources that may be on v1beta2
 * on older Strimzi operator deployments (KafkaTopic, KafkaUser).
 */
public class StrimziApiVersion {

    private static volatile String topicUserVersion = "v1";

    private StrimziApiVersion() {}

    public static String getTopicUserVersion() {
        return topicUserVersion;
    }

    public static void setTopicUserVersion(String version) {
        topicUserVersion = version;
    }
}
