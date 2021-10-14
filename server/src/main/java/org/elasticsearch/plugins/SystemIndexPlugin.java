/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugins;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.snapshots.features.ResetFeatureStateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.AssociatedIndexDescriptor;
import org.elasticsearch.indices.SystemDataStreamDescriptor;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.indices.SystemIndices;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for defining system indices. Extends {@link ActionPlugin} because system indices must be accessed via APIs
 * added by the plugin that owns the system index, rather than standard APIs.
 */
public interface SystemIndexPlugin extends ActionPlugin {

    /**
     * Returns a {@link Collection} of {@link SystemIndexDescriptor}s that describe this plugin's system indices, including
     * name, mapping, and settings.
     * @param settings The node's settings
     * @return Descriptions of the system indices managed by this plugin.
     */
    default Collection<SystemIndexDescriptor> getSystemIndexDescriptors(Settings settings) {
        return Collections.emptyList();
    }

    default Collection<SystemDataStreamDescriptor> getSystemDataStreamDescriptors() {
        return Collections.emptyList();
    }

    /**
     * @return The name of the feature, as used for specifying feature states in snapshot creation and restoration.
     */
    String getFeatureName();

    /**
     * @return A description of the feature, as used for the Get Snapshottable Features API.
     */
    String getFeatureDescription();

    /**
     * Returns a list of descriptors for "associated indices": indices which depend on this plugin's system indices, but are not
     * themselves system indices.
     *
     * @return A list of descriptors of indices which depend on the contents of this plugin's system indices, but are not themselves system
     * indices
     */
    default Collection<AssociatedIndexDescriptor> getAssociatedIndexDescriptors() {
        return Collections.emptyList();
    }

    /**
     * Cleans up the state of the feature by deleting system indices and associated indices.
     * Override to do more for cleanup (e.g. cancelling tasks).
     * @param clusterService Cluster service to provide cluster state
     * @param client A client, for executing actions
     * @param listener Listener for post-cleanup result
     */
    default void cleanUpFeature(
        ClusterService clusterService, Client client,
        ActionListener<ResetFeatureStateResponse.ResetFeatureStateStatus> listener) {

        SystemIndices.Feature.cleanUpFeature(
            getSystemIndexDescriptors(clusterService.getSettings()),
            getAssociatedIndexDescriptors(),
            getFeatureName(),
            clusterService,
            client,
            listener
        );
    }

    /**
     * A method used to signal that the system indices owned by this plugin are about to be upgraded.
     *
     * This method will typically be called once, before any changes are made to the system indices owned by this plugin. However, if there
     * is a master failover at exactly the wrong time during the upgrade process, this may be called more than once, though this should be
     * very rare.
     *
     * This method can also store metadata to be passed to
     * {@link SystemIndexPlugin#indicesMigrationComplete(Map, ClusterService, Client, ActionListener)} when it is called; see the
     * {@code listener} parameter for details.
     *
     * @param clusterService The cluster service.
     * @param client A {@link org.elasticsearch.client.ParentTaskAssigningClient} with the parent task set to the upgrade task. Does not set
     *               the origin header, so implementors of this method will likely want to wrap it in an
     *               {@link org.elasticsearch.client.OriginSettingClient}.
     * @param listener A listener that should have {@link ActionListener#onResponse(Object)} called once all necessary preparations for the
     *                 upgrade of indices owned by this plugin have been completed. The {@link Map} passed to the listener will be stored
     *                 and passed to {@link #indicesMigrationComplete(Map, ClusterService, Client, ActionListener)}. Note the contents of
     *                 the map *must* be writeable using {@link org.elasticsearch.common.io.stream.StreamOutput#writeGenericValue(Object)}.
     */
    default void prepareForIndicesMigration(ClusterService clusterService, Client client, ActionListener<Map<String, Object>> listener) {
        listener.onResponse(Collections.emptyMap());
    }

    /**
     * A method used to signal that the system indices owned by this plugin have been upgraded and all restrictions (i.e. write blocks)
     * necessary for the upgrade have been lifted from the indices owned by this plugin.
     *
     * This method will be called once, after all system indices owned by this plugin have been upgraded. Note that the upgrade may not have
     * completed successfully, but if not, all write blocks/etc. will have been removed from the indices in question anyway as the upgrade
     * process tries not to leave anything in an unusable state.
     *
     * Note: This method may need additional parameters when we support breaking mapping changes, as in that case we can't assume that
     * any indices which were not upgraded can still be used (whereas we can assume that while the upgrade process is limited to reindexing,
     * with no data format changes allowed).
     *
     * @param preUpgradeMetadata The metadata that was given to the listener by
     *                           {@link #prepareForIndicesMigration(ClusterService, Client, ActionListener)}.
     * @param clusterService The cluster service.
     * @param client A {@link org.elasticsearch.client.ParentTaskAssigningClient} with the parent task set to the upgrade task. Does not set
     *               the origin header, so implementors of this method will likely want to wrap it in an
     *               {@link org.elasticsearch.client.OriginSettingClient}.
     * @param listener A listener that should have {@code ActionListener.onResponse(true)} called once all actions following the upgrade
     *                 of this plugin's system indices have been completed.
     */
    default void indicesMigrationComplete(
        Map<String, Object> preUpgradeMetadata,
        ClusterService clusterService,
        Client client,
        ActionListener<Boolean> listener
    ) {
        listener.onResponse(true);
    }
}
