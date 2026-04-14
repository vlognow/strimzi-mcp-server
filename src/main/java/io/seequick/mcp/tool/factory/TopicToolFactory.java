package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;


import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.topic.CompareTopicConfigTool;
import io.seequick.mcp.tool.topic.DescribeTopicTool;
import io.seequick.mcp.tool.topic.GetTopicOperatorStatusTool;
import io.seequick.mcp.tool.topic.GetUnreadyTopicsTool;
import io.seequick.mcp.tool.topic.ListTopicsTool;

import java.util.List;

/**
 * Factory for Topic Operator-related tools.
 */
public class TopicToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                new ListTopicsTool(client),
                new DescribeTopicTool(client),
                new GetUnreadyTopicsTool(client),
                new GetTopicOperatorStatusTool(client),
                new CompareTopicConfigTool(client)
                // Write tools (re-enable by importing and adding):
                // new CreateTopicTool(client),
                // new DeleteTopicTool(client),
                // new UpdateTopicConfigTool(client)
        );
    }
}
