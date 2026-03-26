package io.seequick.mcp.tool;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StrimziResourceRepository} when a v1beta2 version override is active,
 * simulating clusters running Strimzi operator &le; 0.40.0.
 */
@EnableKubernetesMockClient(crud = true)
class StrimziResourceRepositoryV1Beta2Test {

    KubernetesClient client;

    private StrimziResourceRepository<KafkaTopic, KafkaTopicList> repository;

    @BeforeEach
    void setUp() {
        repository = new StrimziResourceRepository<>(client, KafkaTopic.class, KafkaTopicList.class, "v1beta2");
    }

    @Test
    void createAndGetShouldWorkWithV1Beta2Override() {
        KafkaTopic topic = buildTopic("test-topic", "kafka", "my-cluster", 3, 2);

        KafkaTopic created = repository.create("kafka", topic);

        assertThat(created).isNotNull();
        assertThat(created.getMetadata().getName()).isEqualTo("test-topic");
        assertThat(created.getSpec().getPartitions()).isEqualTo(3);
        assertThat(created.getSpec().getReplicas()).isEqualTo(2);
    }

    @Test
    void getShouldReturnNullWhenNotFoundWithV1Beta2Override() {
        KafkaTopic result = repository.get("kafka", "non-existent");

        assertThat(result).isNull();
    }

    @Test
    void listShouldReturnResourcesWithV1Beta2Override() {
        repository.create("kafka", buildTopic("topic-1", "kafka", "cluster-a", 1, 1));
        repository.create("kafka", buildTopic("topic-2", "kafka", "cluster-a", 1, 1));
        repository.create("other-ns", buildTopic("topic-3", "other-ns", "cluster-b", 1, 1));

        KafkaTopicList list = repository.list("kafka", (String) null);

        assertThat(list.getItems()).hasSize(2);
        assertThat(list.getItems()).extracting(t -> t.getMetadata().getName())
                .containsExactlyInAnyOrder("topic-1", "topic-2");
    }

    @Test
    void listShouldFilterByClusterLabelWithV1Beta2Override() {
        repository.create("kafka", buildTopic("topic-a", "kafka", "cluster-a", 1, 1));
        repository.create("kafka", buildTopic("topic-b", "kafka", "cluster-b", 1, 1));
        repository.create("kafka", buildTopic("topic-c", "kafka", "cluster-a", 1, 1));

        KafkaTopicList list = repository.list("kafka", "cluster-a");

        assertThat(list.getItems()).hasSize(2);
        assertThat(list.getItems()).extracting(t -> t.getMetadata().getName())
                .containsExactlyInAnyOrder("topic-a", "topic-c");
    }

    @Test
    void listShouldListFromAllNamespacesWithV1Beta2Override() {
        repository.create("ns1", buildTopic("topic-1", "ns1", "cluster", 1, 1));
        repository.create("ns2", buildTopic("topic-2", "ns2", "cluster", 1, 1));

        KafkaTopicList list = repository.list(null, (String) null);

        assertThat(list.getItems()).hasSize(2);
    }

    @Test
    void deleteShouldRemoveResourceWithV1Beta2Override() {
        repository.create("kafka", buildTopic("to-delete", "kafka", "cluster", 1, 1));
        assertThat(repository.exists("kafka", "to-delete")).isTrue();

        repository.delete("kafka", "to-delete");

        assertThat(repository.exists("kafka", "to-delete")).isFalse();
    }

    @Test
    void updateShouldModifyResourceWithV1Beta2Override() {
        repository.create("kafka", buildTopic("my-topic", "kafka", "cluster", 1, 1));

        KafkaTopic retrieved = repository.get("kafka", "my-topic");
        retrieved.getSpec().setPartitions(6);
        repository.update("kafka", retrieved);

        KafkaTopic updated = repository.get("kafka", "my-topic");
        assertThat(updated.getSpec().getPartitions()).isEqualTo(6);
    }

    @Test
    void existsShouldReturnTrueWhenResourceExistsWithV1Beta2Override() {
        repository.create("kafka", buildTopic("exists-topic", "kafka", "cluster", 1, 1));

        assertThat(repository.exists("kafka", "exists-topic")).isTrue();
    }

    @Test
    void existsShouldReturnFalseWhenResourceDoesNotExistWithV1Beta2Override() {
        assertThat(repository.exists("kafka", "missing-topic")).isFalse();
    }

    private KafkaTopic buildTopic(String name, String namespace, String cluster, int partitions, int replicas) {
        return new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(partitions)
                    .withReplicas(replicas)
                .endSpec()
                .build();
    }
}
