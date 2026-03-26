package io.seequick.mcp;

import io.fabric8.kubernetes.api.model.APIResourceBuilder;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient
class StrimziApiVersionDetectorTest {

    KubernetesClient client;
    KubernetesMockServer server;

    @Test
    void detectShouldReturnV1WhenKafkaTopicsServedUnderV1() {
        server.expect().get()
                .withPath("/apis/kafka.strimzi.io/v1")
                .andReturn(200, new APIResourceListBuilder()
                        .addToResources(new APIResourceBuilder()
                                .withName("kafkatopics")
                                .withKind("KafkaTopic")
                                .withNamespaced(true)
                                .build())
                        .build())
                .once();

        assertThat(StrimziApiVersionDetector.detect(client)).isEqualTo("v1");
    }

    @Test
    void detectShouldReturnV1Beta2WhenKafkaTopicsNotFoundUnderV1() {
        server.expect().get()
                .withPath("/apis/kafka.strimzi.io/v1")
                .andReturn(200, new APIResourceListBuilder().build())
                .once();

        assertThat(StrimziApiVersionDetector.detect(client)).isEqualTo("v1beta2");
    }

    @Test
    void detectShouldReturnV1Beta2WhenDiscoveryEndpointReturns404() {
        server.expect().get()
                .withPath("/apis/kafka.strimzi.io/v1")
                .andReturn(404, "{\"kind\":\"Status\",\"code\":404}")
                .once();

        assertThat(StrimziApiVersionDetector.detect(client)).isEqualTo("v1beta2");
    }

    @Test
    void detectShouldReturnV1Beta2WhenDiscoveryReturnsOtherResources() {
        server.expect().get()
                .withPath("/apis/kafka.strimzi.io/v1")
                .andReturn(200, new APIResourceListBuilder()
                        .addToResources(new APIResourceBuilder()
                                .withName("kafkas")
                                .withKind("Kafka")
                                .withNamespaced(true)
                                .build())
                        .build())
                .once();

        assertThat(StrimziApiVersionDetector.detect(client)).isEqualTo("v1beta2");
    }
}
