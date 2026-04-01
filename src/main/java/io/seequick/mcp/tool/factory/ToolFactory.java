package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;

import java.util.List;

/**
 * Factory interface for creating groups of related Strimzi tools.
 */
public interface ToolFactory {

    /**
     * Creates all tools managed by this factory.
     *
     * @param clientResolver The Kubernetes client resolver to use for tool operations
     * @return List of tools created by this factory
     */
    List<StrimziTool> createTools(KubernetesClientResolver clientResolver);
}
