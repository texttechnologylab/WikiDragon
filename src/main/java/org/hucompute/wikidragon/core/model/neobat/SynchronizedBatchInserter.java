/*
 * Copyright 2018
 * Text-Technology Lab
 * Johann Wolfgang Goethe-Universität Frankfurt am Main
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/agpl-3.0.en.html.
 */

package org.hucompute.wikidragon.core.model.neobat;


import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.schema.ConstraintCreator;
import org.neo4j.graphdb.schema.IndexCreator;
import org.neo4j.helpers.Service;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.impl.index.IndexConfigStore;
import org.neo4j.kernel.impl.store.id.IdGeneratorFactory;
import org.neo4j.unsafe.batchinsert.BatchRelationship;
import org.neo4j.unsafe.batchinsert.internal.BatchInserterImpl;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Rüdiger Gleim
 */
public class SynchronizedBatchInserter extends BatchInserterImpl {

    public SynchronizedBatchInserter(File storeDir) throws IOException {
        this(storeDir, MapUtil.stringMap(new String[0]));
    }

    public SynchronizedBatchInserter(File storeDir, FileSystemAbstraction fs) throws IOException {
        this(storeDir, fs, MapUtil.stringMap(new String[0]), loadKernelExtension());
    }

    public SynchronizedBatchInserter(File storeDir, Map<String, String> config) throws IOException {
        this(storeDir, new DefaultFileSystemAbstraction(), config, loadKernelExtension());
    }

    public SynchronizedBatchInserter(File storeDir, FileSystemAbstraction fs, Map<String, String> config) throws IOException {
        this(storeDir, fs, config, loadKernelExtension());
    }

    public SynchronizedBatchInserter(File storeDir, Map<String, String> config, Iterable<KernelExtensionFactory<?>> kernelExtensions) throws IOException {
        this(storeDir, new DefaultFileSystemAbstraction(), config, kernelExtensions);
    }

    public SynchronizedBatchInserter(File storeDir, FileSystemAbstraction fileSystem, Map<String, String> stringParams, Iterable<KernelExtensionFactory<?>> kernelExtensions) throws IOException {
        super(storeDir, fileSystem, stringParams, kernelExtensions);
    }

    private static Iterable loadKernelExtension() {
        return Service.load(KernelExtensionFactory.class);
    }

    @Override
    public synchronized boolean nodeHasProperty(long node, String propertyName) {
        return super.nodeHasProperty(node, propertyName);
    }

    @Override
    public synchronized boolean relationshipHasProperty(long relationship, String propertyName) {
        return super.relationshipHasProperty(relationship, propertyName);
    }

    @Override
    public synchronized void setNodeProperty(long node, String propertyName, Object propertyValue) {
        super.setNodeProperty(node, propertyName, propertyValue);
    }

    @Override
    public synchronized void setRelationshipProperty(long relationship, String propertyName, Object propertyValue) {
        super.setRelationshipProperty(relationship, propertyName, propertyValue);
    }

    @Override
    public synchronized void removeNodeProperty(long node, String propertyName) {
        super.removeNodeProperty(node, propertyName);
    }

    @Override
    public synchronized void removeRelationshipProperty(long relationship, String propertyName) {
        super.removeRelationshipProperty(relationship, propertyName);
    }

    @Override
    public synchronized IndexCreator createDeferredSchemaIndex(Label label) {
        return super.createDeferredSchemaIndex(label);
    }

    @Override
    public synchronized ConstraintCreator createDeferredConstraint(Label label) {
        return super.createDeferredConstraint(label);
    }

    @Override
    public synchronized long createNode(Map<String, Object> properties, Label... labels) {
        return super.createNode(properties, labels);
    }

    @Override
    public synchronized void createNode(long id, Map<String, Object> properties, Label... labels) {
        super.createNode(id, properties, labels);
    }

    @Override
    public synchronized void setNodeLabels(long node, Label... labels) {
        super.setNodeLabels(node, labels);
    }

    @Override
    public synchronized Iterable<Label> getNodeLabels(long node) {
        return super.getNodeLabels(node);
    }

    @Override
    public synchronized boolean nodeHasLabel(long node, Label label) {
        return super.nodeHasLabel(node, label);
    }

    @Override
    public synchronized long createRelationship(long node1, long node2, RelationshipType type, Map<String, Object> properties) {
        return super.createRelationship(node1, node2, type, properties);
    }

    @Override
    public synchronized void setNodeProperties(long node, Map<String, Object> properties) {
        super.setNodeProperties(node, properties);
    }

    @Override
    public synchronized void setRelationshipProperties(long rel, Map<String, Object> properties) {
        super.setRelationshipProperties(rel, properties);
    }

    @Override
    public synchronized boolean nodeExists(long nodeId) {
        return super.nodeExists(nodeId);
    }

    @Override
    public synchronized Map<String, Object> getNodeProperties(long nodeId) {
        return super.getNodeProperties(nodeId);
    }

    @Override
    public synchronized Iterable<Long> getRelationshipIds(long nodeId) {
        return super.getRelationshipIds(nodeId);
    }

    @Override
    public synchronized Iterable<BatchRelationship> getRelationships(long nodeId) {
        return super.getRelationships(nodeId);
    }

    @Override
    public synchronized BatchRelationship getRelationshipById(long relId) {
        return super.getRelationshipById(relId);
    }

    @Override
    public synchronized Map<String, Object> getRelationshipProperties(long relId) {
        return super.getRelationshipProperties(relId);
    }

    @Override
    public synchronized void shutdown() {
        super.shutdown();
    }

    @Override
    public synchronized String getStoreDir() {
        return super.getStoreDir();
    }

    @Override
    public synchronized IndexConfigStore getIndexStore() {
        return super.getIndexStore();
    }

    @Override
    public synchronized IdGeneratorFactory getIdGeneratorFactory() {
        return super.getIdGeneratorFactory();
    }
}
