package io.seequick.mcp.tool.observability.health;

import io.seequick.mcp.StrimziApiVersion;
import io.seequick.mcp.tool.StrimziResourceRepository;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;

import java.util.List;

/**
 * Health checker for Kafka topics.
 */
public class TopicHealthChecker implements HealthChecker {

    @Override
    public String getSectionTitle() {
        return "TOPICS";
    }

    @Override
    public void check(HealthCheckContext context, HealthCheckResult result) {
        result.startSection(getSectionTitle());

        List<KafkaTopic> topics = listTopics(context);

        long unreadyTopics = topics.stream()
                .filter(this::isUnready)
                .count();

        result.append("  Total: ").append(String.valueOf(topics.size())).newLine();
        result.append("  Ready: ").append(String.valueOf(topics.size() - unreadyTopics)).newLine();

        if (unreadyTopics > 0) {
            result.append("  Not Ready: ").append(String.valueOf(unreadyTopics)).append(" \u26A0\n");
            result.addWarning();
        }

        result.newLine();
    }

    private List<KafkaTopic> listTopics(HealthCheckContext context) {
        StrimziResourceRepository<KafkaTopic, KafkaTopicList> repo =
                new StrimziResourceRepository<>(context.getClient(), KafkaTopic.class, KafkaTopicList.class,
                        StrimziApiVersion.getTopicUserVersion());

        String namespace = context.hasNamespaceFilter() ? context.getNamespace() : null;
        String cluster = context.hasClusterFilter() ? context.getKafkaCluster() : null;
        return repo.list(namespace, cluster).getItems();
    }

    private boolean isUnready(KafkaTopic topic) {
        if (topic.getStatus() == null || topic.getStatus().getConditions() == null) {
            return true;
        }
        return topic.getStatus().getConditions().stream()
                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
