package io.seequick.mcp.tool;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * Generic repository for Strimzi Kubernetes resources.
 * Provides common CRUD operations with filtering by namespace and cluster label.
 *
 * <p>When a {@code versionOverride} is supplied and differs from the resource class's
 * {@code @Version} annotation, all operations are routed through
 * {@link KubernetesClient#genericKubernetesResources(ResourceDefinitionContext)} so that
 * the correct API path is used (e.g. {@code v1beta2} on Strimzi &le; 0.40.0).
 *
 * @param <T>     The resource type
 * @param <TList> The resource list type
 */
public class StrimziResourceRepository<T extends HasMetadata, TList extends KubernetesResourceList<T>> {

    private final KubernetesClient client;
    private final Class<T> resourceClass;
    private final Class<TList> listClass;
    private final String versionOverride;

    public StrimziResourceRepository(KubernetesClient client, Class<T> resourceClass, Class<TList> listClass) {
        this(client, resourceClass, listClass, null);
    }

    public StrimziResourceRepository(KubernetesClient client, Class<T> resourceClass, Class<TList> listClass,
            String versionOverride) {
        this.client = client;
        this.resourceClass = resourceClass;
        this.listClass = listClass;
        this.versionOverride = versionOverride;
    }

    // -------------------------------------------------------------------------
    // Version-override helpers
    // -------------------------------------------------------------------------

    private boolean needsVersionOverride() {
        if (versionOverride == null) return false;
        Version ann = resourceClass.getAnnotation(Version.class);
        String classVersion = ann != null ? ann.value() : "v1";
        return !versionOverride.equals(classVersion);
    }

    private ResourceDefinitionContext buildContext() {
        Group groupAnn = resourceClass.getAnnotation(Group.class);
        String group = groupAnn != null ? groupAnn.value() : "";

        Plural pluralAnn = resourceClass.getAnnotation(Plural.class);
        String plural = pluralAnn != null ? pluralAnn.value()
                : resourceClass.getSimpleName().toLowerCase() + "s";

        return new ResourceDefinitionContext.Builder()
                .withGroup(group)
                .withVersion(versionOverride)
                .withKind(resourceClass.getSimpleName())
                .withPlural(plural)
                .withNamespaced(true)
                .build();
    }

    private T convertToTyped(GenericKubernetesResource generic) {
        try {
            String json = Serialization.jsonMapper().writeValueAsString(generic);
            return Serialization.jsonMapper().readValue(json, resourceClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert generic resource to " + resourceClass.getSimpleName(), e);
        }
    }

    private GenericKubernetesResource toGeneric(T resource) {
        try {
            Group groupAnn = resourceClass.getAnnotation(Group.class);
            String group = groupAnn != null ? groupAnn.value() : "";
            String json = Serialization.jsonMapper().writeValueAsString(resource);
            GenericKubernetesResource generic = Serialization.jsonMapper()
                    .readValue(json, GenericKubernetesResource.class);
            generic.setApiVersion(group + "/" + versionOverride);
            return generic;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert resource to generic", e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Lists resources with optional namespace and cluster label filtering.
     */
    public TList list(String namespace, String clusterLabel) {
        return list(namespace, StrimziLabels.CLUSTER, clusterLabel);
    }

    /**
     * Lists resources with optional namespace and custom label filtering.
     */
    public TList list(String namespace, String labelKey, String labelValue) {
        if (needsVersionOverride()) {
            return listWithOverride(namespace, labelKey, labelValue);
        }
        if (namespace != null && !namespace.isEmpty()) {
            var resource = client.resources(resourceClass, listClass).inNamespace(namespace);
            if (labelValue != null && !labelValue.isEmpty()) {
                return resource.withLabel(labelKey, labelValue).list();
            }
            return resource.list();
        } else {
            var resource = client.resources(resourceClass, listClass).inAnyNamespace();
            if (labelValue != null && !labelValue.isEmpty()) {
                return resource.withLabel(labelKey, labelValue).list();
            }
            return resource.list();
        }
    }

    private TList listWithOverride(String namespace, String labelKey, String labelValue) {
        ResourceDefinitionContext context = buildContext();
        GenericKubernetesResourceList genericList;

        if (namespace != null && !namespace.isEmpty()) {
            var op = client.genericKubernetesResources(context).inNamespace(namespace);
            genericList = (labelValue != null && !labelValue.isEmpty())
                    ? op.withLabel(labelKey, labelValue).list()
                    : op.list();
        } else {
            var op = client.genericKubernetesResources(context).inAnyNamespace();
            genericList = (labelValue != null && !labelValue.isEmpty())
                    ? op.withLabel(labelKey, labelValue).list()
                    : op.list();
        }

        try {
            String json = Serialization.jsonMapper().writeValueAsString(genericList);
            return Serialization.jsonMapper().readValue(json, listClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize list as " + listClass.getSimpleName(), e);
        }
    }

    /**
     * Gets a single resource by namespace and name.
     */
    public T get(String namespace, String name) {
        if (needsVersionOverride()) {
            GenericKubernetesResource generic = client.genericKubernetesResources(buildContext())
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
            return generic != null ? convertToTyped(generic) : null;
        }
        return client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    /**
     * Creates a resource in the specified namespace.
     */
    public T create(String namespace, T resource) {
        if (needsVersionOverride()) {
            GenericKubernetesResource created = client.genericKubernetesResources(buildContext())
                    .inNamespace(namespace)
                    .resource(toGeneric(resource))
                    .create();
            return convertToTyped(created);
        }
        return client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .resource(resource)
                .create();
    }

    /**
     * Updates a resource in the specified namespace.
     */
    public T update(String namespace, T resource) {
        if (needsVersionOverride()) {
            GenericKubernetesResource updated = client.genericKubernetesResources(buildContext())
                    .inNamespace(namespace)
                    .resource(toGeneric(resource))
                    .update();
            return convertToTyped(updated);
        }
        return client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .resource(resource)
                .update();
    }

    /**
     * Deletes a resource by namespace and name.
     */
    public void delete(String namespace, String name) {
        if (needsVersionOverride()) {
            client.genericKubernetesResources(buildContext())
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();
            return;
        }
        client.resources(resourceClass, listClass)
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    /**
     * Checks if a resource exists.
     */
    public boolean exists(String namespace, String name) {
        return get(namespace, name) != null;
    }
}
