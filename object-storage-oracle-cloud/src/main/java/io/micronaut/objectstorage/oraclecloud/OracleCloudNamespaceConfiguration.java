package io.micronaut.objectstorage.oraclecloud;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.Toggleable;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

@EachProperty(OracleCloudNamespaceConfiguration.PREFIX)
public class OracleCloudNamespaceConfiguration implements Toggleable {

    /**
     * Configuration Prefix.
     */
    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + ".oracle-cloud-dynamic"; // todo: tbd

    private boolean enabled = true;

    @NonNull
    private String namespace;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
