package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.user.CreateUserTool;
import io.seequick.mcp.tool.user.DeleteUserTool;
import io.seequick.mcp.tool.user.DescribeUserTool;
import io.seequick.mcp.tool.user.GetUserCredentialsTool;
import io.seequick.mcp.tool.user.GetUserOperatorStatusTool;
import io.seequick.mcp.tool.user.ListUserAclsTool;
import io.seequick.mcp.tool.user.ListUsersTool;
import io.seequick.mcp.tool.user.UpdateUserAclsTool;
import io.seequick.mcp.tool.user.UpdateUserQuotasTool;

import java.util.List;

/**
 * Factory for User Operator-related tools.
 */
public class UserToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                new ListUsersTool(clientResolver),
                new DescribeUserTool(clientResolver),
                new CreateUserTool(clientResolver),
                new DeleteUserTool(clientResolver),
                new GetUserCredentialsTool(clientResolver),
                new GetUserOperatorStatusTool(clientResolver),
                new UpdateUserAclsTool(clientResolver),
                new UpdateUserQuotasTool(clientResolver),
                new ListUserAclsTool(clientResolver)
        );
    }
}
