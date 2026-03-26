package io.seequick.mcp.tool.observability.health;

import io.seequick.mcp.StrimziApiVersion;
import io.seequick.mcp.tool.StrimziLabels;
import io.seequick.mcp.tool.StrimziResourceRepository;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;

import java.util.List;

/**
 * Health checker for Kafka users.
 */
public class UserHealthChecker implements HealthChecker {

    @Override
    public String getSectionTitle() {
        return "USERS";
    }

    @Override
    public void check(HealthCheckContext context, HealthCheckResult result) {
        result.startSection(getSectionTitle());

        List<KafkaUser> users = listUsers(context);

        long unreadyUsers = users.stream()
                .filter(this::isUnready)
                .count();

        result.append("  Total: ").append(String.valueOf(users.size())).newLine();
        result.append("  Ready: ").append(String.valueOf(users.size() - unreadyUsers)).newLine();

        if (unreadyUsers > 0) {
            result.append("  Not Ready: ").append(String.valueOf(unreadyUsers)).append(" \u26A0\n");
            result.addWarning();
        }

        result.newLine();
    }

    private List<KafkaUser> listUsers(HealthCheckContext context) {
        StrimziResourceRepository<KafkaUser, KafkaUserList> repo =
                new StrimziResourceRepository<>(context.getClient(), KafkaUser.class, KafkaUserList.class,
                        StrimziApiVersion.getTopicUserVersion());

        String namespace = context.hasNamespaceFilter() ? context.getNamespace() : null;
        String cluster = context.hasClusterFilter() ? context.getKafkaCluster() : null;
        return repo.list(namespace, cluster).getItems();
    }

    private boolean isUnready(KafkaUser user) {
        if (user.getStatus() == null || user.getStatus().getConditions() == null) {
            return true;
        }
        return user.getStatus().getConditions().stream()
                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
