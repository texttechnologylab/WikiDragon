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

package org.hucompute.wikidragon.core.model.neo;

import gnu.trove.map.hash.TLongObjectHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Iterable;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.collection.MapUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * @author Rüdiger Gleim
 */
public class NeoWikiDragonDatabase implements WikiDragonDatabase {

    private static Logger logger = LogManager.getLogger(NeoWikiDragonDatabase.class);

    protected enum NodeType {MEDIAWIKI_COLLECTION, MEDIAWIKI, NAMESPACE, PAGE, REVISION, CONTRIBUTOR, PAGETIER, LEXICON,
        SUPERLEMMA, LEMMA, MEANING, SYNTACTICWORD, LEXICONENTRYGENERALATTRIBUTE, WIKIDATAENTITY};
    protected GraphDatabaseService database;
    protected NeoWikiObjectFactory wikiObjectFactory;

    private File databaseDirectory;
    private Map<String, String> parameters;

    private TLongObjectHashMap<Index<Node>> mediaWikiNodeIndexMap;
    private TLongObjectHashMap<RelationshipIndex> mediaWikiRelationshipIndexMap;

    protected IOManager ioManager;

    public NeoWikiDragonDatabase(File pDatabaseDirectory, boolean pReset) throws WikiDragonException {
        databaseDirectory = pDatabaseDirectory;
        Properties lProperties = new Properties();
        try {
            BufferedReader lReader = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/neo4j.properties"), Charset.forName("UTF-8")));
            lProperties.load(lReader);
            lReader.close();
            parameters = new HashMap<>();
            for (String lKey:lProperties.stringPropertyNames()) {
                parameters.put(lKey, lProperties.getProperty(lKey));
            }
        }
        catch (IOException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
        if (pReset) {
            reset();
        }
        else {
            initialize();
        }
    }

    public NeoWikiDragonDatabase(File pDatabaseDirectory, boolean pReset, Map<String, String> pParameters) throws WikiDragonException {
        databaseDirectory = pDatabaseDirectory;
        parameters = pParameters;
        if (pReset) {
            reset();
        }
        else {
            initialize();
        }
    }

    public NeoWikiDragonDatabase(File pDatabaseDirectory, boolean pReset, File pPropertiesFile) throws WikiDragonException {
        databaseDirectory = pDatabaseDirectory;
        Properties lProperties = new Properties();
        try {
            BufferedReader lReader = new BufferedReader(new InputStreamReader(new FileInputStream(pPropertiesFile), Charset.forName("UTF-8")));
            lProperties.load(lReader);
            lReader.close();
            parameters = new HashMap<>();
            for (String lKey:lProperties.stringPropertyNames()) {
                parameters.put(lKey, lProperties.getProperty(lKey));
            }
        }
        catch (IOException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
        if (pReset) {
            reset();
        }
        else {
            initialize();
        }
    }

    @Override
    public IOManager getIOManager() {
        if (ioManager == null) {
            ioManager = new IOManager(this);
        }
        return ioManager;
    }

    @Override
    public void close() {
        if (database != null) {
            logger.info("Closing database in "+databaseDirectory.getAbsolutePath()+"...");
            database.shutdown();
            database = null;
            logger.info("Closing database in "+databaseDirectory.getAbsolutePath()+"... done");
        }
    }

    @Override
    public void reset() throws WikiDragonException {
        logger.info("Resetting database in "+databaseDirectory.getAbsolutePath()+"...");
        try {
            close();
        }
        catch (Exception e) {
            throw new WikiDragonException(e);
        }
        if (databaseDirectory.exists()) IOUtil.delete(databaseDirectory, true);
        initialize();
        logger.info("Resetting database in "+databaseDirectory.getAbsolutePath()+"... done");
    }

    @Override
    public MediaWikiCollection getMediaWikiCollection() {
        try (Transaction tx = database.beginTx()) {
            MediaWikiCollection lResult = null;
            for (Node lNode:getMediaWikiNodeIndex(NeoWikiObject.GLOBALINDEXID).get(NeoWikiObject.ATTR_WIKIOBJECT_TYPE, NodeType.MEDIAWIKI_COLLECTION.name())) {
                assert (lResult == null);
                lResult = (MediaWikiCollection)wikiObjectFactory.getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public WikiObjectFactory getWikiObjectFactory() {
        return wikiObjectFactory;
    }

    @Override
    public WikiTransaction beginTx() {
        return new NeoWikiTransaction(this);
    }

    @Override
    public Iterable<WikiDataEntity> getWikiDataEntities() {
        return new Iterable<WikiDataEntity>() {
            @Override
            public Iterator<WikiDataEntity> iterator() {
                return getWikiDataEntityIterator();
            }
        };
    }

    @Override
    public WikiDataEntity createWikiDataEntity(String pEntityID) throws WikiDragonException {
        return NeoWikiDataEntity.create(this, pEntityID);
    }

    @Override
    public WikiObjectIterator<WikiDataEntity> getWikiDataEntityIterator() {
        return new NeoWikiObjectIterator<WikiDataEntity>(this, getMediaWikiNodeIndex(NeoWikiObject.GLOBALINDEXID).get(NeoWikiDataEntity.ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.WIKIDATAENTITY.name()));
    }

    @Override
    public WikiDataEntity getWikiDataEntity(String pEntityID) throws WikiDragonException {
        try (Transaction tx = database.beginTx()) {
            WikiDataEntity lResult = null;
            for (Node lNode : getMediaWikiNodeIndex(NeoWikiObject.GLOBALINDEXID).get(NeoWikiDataEntity.ATTR_WIKIDATAENTITY_ENTITYID, pEntityID)) {
                assert (lResult == null);
                lResult = (WikiDataEntity) wikiObjectFactory.getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    protected Index<Node> getMediaWikiNodeIndex(long pMediaWikiNodeId) {
        synchronized (mediaWikiNodeIndexMap) {
            if (!mediaWikiNodeIndexMap.containsKey(pMediaWikiNodeId)) {
                try (Transaction tx = database.beginTx()) {
                    // New Approach
                    mediaWikiNodeIndexMap.put(pMediaWikiNodeId, database.index().forNodes(Long.toString(pMediaWikiNodeId), MapUtil.stringMap("type", "exact", "to_lower_case", "false")));
                    // Old Approach
                    //mediaWikiNodeIndexMap.put(pMediaWikiNodeId, database.index().forNodes(Long.toString(pMediaWikiNodeId), MapUtil.stringMap("type", "exact")));
                    tx.success();
                }
            }
            return mediaWikiNodeIndexMap.get(pMediaWikiNodeId);
        }
    }

    protected RelationshipIndex getMediaWikiRelationshipIndex(long pMediaWikiNodeId) {
        synchronized (mediaWikiRelationshipIndexMap) {
            if (!mediaWikiRelationshipIndexMap.containsKey(pMediaWikiNodeId)) {
                try (Transaction tx = database.beginTx()) {
                    mediaWikiRelationshipIndexMap.put(pMediaWikiNodeId, database.index().forRelationships(Long.toString(pMediaWikiNodeId), MapUtil.stringMap("type", "exact")));
                    tx.success();
                }
            }
            return mediaWikiRelationshipIndexMap.get(pMediaWikiNodeId);
        }
    }

    protected void initialize() throws WikiDragonException {
        logger.info("Initializing database in "+databaseDirectory.getAbsolutePath()+"...");
        if (databaseDirectory.exists()) databaseDirectory.mkdirs();
        GraphDatabaseFactory lGraphDatabaseFactory = new GraphDatabaseFactory();
        GraphDatabaseBuilder lGraphDatabaseBuilder = lGraphDatabaseFactory.newEmbeddedDatabaseBuilder(databaseDirectory);
        if (parameters != null) {
            for (Map.Entry<String, String> lEntry:parameters.entrySet()) {
                lGraphDatabaseBuilder.setConfig(lEntry.getKey(), lEntry.getValue());
            }
        }
        database = lGraphDatabaseBuilder.newGraphDatabase();
        try (Transaction tx = database.beginTx()) {
            // Init Indexes
            mediaWikiNodeIndexMap = new TLongObjectHashMap<>();
            mediaWikiRelationshipIndexMap = new TLongObjectHashMap<>();
            //
            wikiObjectFactory = new NeoWikiObjectFactory(this);
            if (getMediaWikiCollection() == null) {
                logger.debug("Creating initial MediaWikiCollection in " + databaseDirectory.getAbsolutePath());
                NeoMediaWikiCollection.create(this);
            }
            tx.success();
            logger.info("Initializing database in " + databaseDirectory.getAbsolutePath() + "... done");
        }
    }

}
