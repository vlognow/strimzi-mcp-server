package io.seequick.mcp.tool.factory;

import io.seequick.mcp.KubernetesClientResolver;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.security.GetCertificateExpiryTool;
import io.seequick.mcp.tool.security.ListCertificatesTool;
import io.seequick.mcp.tool.security.RotateUserCredentialsTool;

import java.util.List;

/**
 * Factory for security-related tools.
 */
public class SecurityToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClientResolver clientResolver) {
        return List.of(
                new RotateUserCredentialsTool(clientResolver),
                new ListCertificatesTool(clientResolver),
                new GetCertificateExpiryTool(clientResolver)
        );
    }
}
