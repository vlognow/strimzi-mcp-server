package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.cluster.ApproveRebalanceTool;
import io.seequick.mcp.tool.cluster.CreateConnectorTool;
import io.seequick.mcp.tool.cluster.CreateMirrorMaker2Tool;
import io.seequick.mcp.tool.cluster.CreateRebalanceTool;
import io.seequick.mcp.tool.cluster.DeleteConnectorTool;
import io.seequick.mcp.tool.cluster.DescribeBridgeTool;
import io.seequick.mcp.tool.cluster.DescribeConnectorTool;
import io.seequick.mcp.tool.cluster.DescribeKafkaConnectTool;
import io.seequick.mcp.tool.cluster.DescribeMirrorMaker2Tool;
import io.seequick.mcp.tool.cluster.DescribeNodePoolTool;
import io.seequick.mcp.tool.cluster.DescribeRebalanceTool;
import io.seequick.mcp.tool.cluster.GetClusterOperatorStatusTool;
import io.seequick.mcp.tool.cluster.ListBridgesTool;
import io.seequick.mcp.tool.cluster.ListConnectPluginsTool;
import io.seequick.mcp.tool.cluster.ListConnectorsTool;
import io.seequick.mcp.tool.cluster.ListKafkaConnectsTool;
import io.seequick.mcp.tool.cluster.ListMirrorMaker2sTool;
import io.seequick.mcp.tool.cluster.ListNodePoolsTool;
import io.seequick.mcp.tool.cluster.ListRebalancesTool;
import io.seequick.mcp.tool.cluster.PauseConnectorTool;
import io.seequick.mcp.tool.cluster.RefreshRebalanceTool;
import io.seequick.mcp.tool.cluster.RestartConnectorTool;
import io.seequick.mcp.tool.cluster.ResumeConnectorTool;
import io.seequick.mcp.tool.cluster.StopRebalanceTool;
import io.seequick.mcp.tool.cluster.UpdateConnectorConfigTool;

import java.util.List;

/**
 * Factory for Cluster Operator-related tools (NodePools, Connect, Connectors, Rebalances, MirrorMaker2, Bridges).
 */
public class ClusterToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                // Node Pools
                new ListNodePoolsTool(clientResolver),
                new DescribeNodePoolTool(clientResolver),

                // Kafka Connect
                new ListKafkaConnectsTool(clientResolver),
                new DescribeKafkaConnectTool(clientResolver),
                new ListConnectPluginsTool(clientResolver),

                // Connectors
                new ListConnectorsTool(clientResolver),
                new DescribeConnectorTool(clientResolver),
                new CreateConnectorTool(clientResolver),
                new DeleteConnectorTool(clientResolver),
                new PauseConnectorTool(clientResolver),
                new ResumeConnectorTool(clientResolver),
                new RestartConnectorTool(clientResolver),
                new UpdateConnectorConfigTool(clientResolver),

                // Rebalances (Cruise Control)
                new ListRebalancesTool(clientResolver),
                new DescribeRebalanceTool(clientResolver),
                new CreateRebalanceTool(clientResolver),
                new ApproveRebalanceTool(clientResolver),
                new StopRebalanceTool(clientResolver),
                new RefreshRebalanceTool(clientResolver),

                // MirrorMaker2
                new ListMirrorMaker2sTool(clientResolver),
                new DescribeMirrorMaker2Tool(clientResolver),
                new CreateMirrorMaker2Tool(clientResolver),

                // Bridges
                new ListBridgesTool(clientResolver),
                new DescribeBridgeTool(clientResolver),

                // Cluster Operator
                new GetClusterOperatorStatusTool(clientResolver)
        );
    }
}
