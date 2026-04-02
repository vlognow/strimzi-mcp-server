package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.observability.DescribeKafkaPodTool;
import io.seequick.mcp.tool.observability.GetKafkaEventsTool;
import io.seequick.mcp.tool.observability.GetKafkaLogsTool;
import io.seequick.mcp.tool.observability.GetOperatorLogsTool;
import io.seequick.mcp.tool.observability.HealthCheckTool;

import java.util.List;

/**
 * Factory for observability-related tools.
 */
public class ObservabilityToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                new GetKafkaLogsTool(clientResolver),
                new GetOperatorLogsTool(clientResolver),
                new GetKafkaEventsTool(clientResolver),
                new DescribeKafkaPodTool(clientResolver),
                new HealthCheckTool(clientResolver)
        );
    }
}
