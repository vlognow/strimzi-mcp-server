package io.seequick.mcp;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KubernetesClientResolverTest {

    @Mock
    private KubernetesClient mockClient;

    @Test
    void fixedShouldAlwaysReturnSameClient() {
        KubernetesClientResolver resolver = KubernetesClientResolver.fixed(mockClient);

        assertThat(resolver.resolve(null)).isSameAs(mockClient);
        assertThat(resolver.resolve("")).isSameAs(mockClient);
        assertThat(resolver.resolve("any-context")).isSameAs(mockClient);
        assertThat(resolver.resolveDefault()).isSameAs(mockClient);
    }

    @Test
    void resolveShouldCacheClients() {
        KubernetesClientResolver resolver = KubernetesClientResolver.fixed(mockClient);

        KubernetesClient first = resolver.resolve("ctx");
        KubernetesClient second = resolver.resolve("ctx");

        assertThat(first).isSameAs(second);
    }

    @Test
    void resolveDefaultShouldReturnSameAsResolveNull() {
        KubernetesClientResolver resolver = KubernetesClientResolver.fixed(mockClient);

        assertThat(resolver.resolveDefault()).isSameAs(resolver.resolve(null));
    }
}
