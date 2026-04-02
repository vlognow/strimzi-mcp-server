package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.kafka.GetKafkaListenersTool;
import io.seequick.mcp.tool.kafka.GetKafkaStatusTool;
import io.seequick.mcp.tool.kafka.ListKafkasTool;
import io.seequick.mcp.tool.kafka.RestartKafkaBrokerTool;
import io.seequick.mcp.tool.kafka.ScaleNodePoolTool;

import java.util.List;

/**
 * Factory for Kafka cluster-related tools.
 */
public class KafkaToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                new ListKafkasTool(clientResolver),
                new GetKafkaStatusTool(clientResolver),
                new GetKafkaListenersTool(clientResolver),
                new RestartKafkaBrokerTool(clientResolver),
                new ScaleNodePoolTool(clientResolver)
        );
    }
}
