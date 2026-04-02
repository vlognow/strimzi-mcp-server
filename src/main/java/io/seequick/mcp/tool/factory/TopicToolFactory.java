package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.topic.CompareTopicConfigTool;
import io.seequick.mcp.tool.topic.CreateTopicTool;
import io.seequick.mcp.tool.topic.DeleteTopicTool;
import io.seequick.mcp.tool.topic.DescribeTopicTool;
import io.seequick.mcp.tool.topic.GetTopicOperatorStatusTool;
import io.seequick.mcp.tool.topic.GetUnreadyTopicsTool;
import io.seequick.mcp.tool.topic.ListTopicsTool;
import io.seequick.mcp.tool.topic.UpdateTopicConfigTool;

import java.util.List;

/**
 * Factory for Topic Operator-related tools.
 */
public class TopicToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                new ListTopicsTool(clientResolver),
                new DescribeTopicTool(clientResolver),
                new CreateTopicTool(clientResolver),
                new DeleteTopicTool(clientResolver),
                new UpdateTopicConfigTool(clientResolver),
                new GetUnreadyTopicsTool(clientResolver),
                new GetTopicOperatorStatusTool(clientResolver),
                new CompareTopicConfigTool(clientResolver)
        );
    }
}
