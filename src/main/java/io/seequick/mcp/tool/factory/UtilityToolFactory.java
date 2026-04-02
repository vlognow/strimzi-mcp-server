package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.utility.ExportResourceYamlTool;
import io.seequick.mcp.tool.utility.GetStrimziVersionTool;
import io.seequick.mcp.tool.utility.ListAllResourcesTool;

import java.util.List;

/**
 * Factory for utility tools.
 */
public class UtilityToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                new ExportResourceYamlTool(clientResolver),
                new GetStrimziVersionTool(clientResolver),
                new ListAllResourcesTool(clientResolver)
        );
    }
}
