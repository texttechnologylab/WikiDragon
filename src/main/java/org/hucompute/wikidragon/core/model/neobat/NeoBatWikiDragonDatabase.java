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

import gnu.trove.map.hash.TLongObjectHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Iterable;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.neo4j.index.lucene.unsafe.batchinsert.LuceneBatchInserterIndexProvider;
import org.neo4j.unsafe.batchinsert.BatchInserterIndex;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatWikiDragonDatabase implements WikiDragonDatabase {

    private static Logger logger = LogManager.getLogger(NeoBatWikiDragonDatabase.class);

    protected enum NodeType {MEDIAWIKI_COLLECTION, MEDIAWIKI, NAMESPACE, PAGE, REVISION, CONTRIBUTOR, PAGETIER, LEXICON,
        SUPERLEMMA, LEMMA, MEANING, SYNTACTICWORD, LEXICONENTRYGENERALATTRIBUTE, WIKIDATAENTITY};
    protected SynchronizedBatchInserter database;
    protected LuceneBatchInserterIndexProvider luceneBatchInserterIndexProvider;
    protected NeoBatWikiObjectFactory wikiObjectFactory;

    private File databaseDirectory;
    private Map<String, String> parameters;

    private TLongObjectHashMap<BatchInserterIndex> mediaWikiNodeIndexMap;
    private TLongObjectHashMap<BatchInserterIndex> mediaWikiRelationshipIndexMap;

    protected IOManager ioManager;

    public NeoBatWikiDragonDatabase(File pDatabaseDirectory, boolean pReset) throws WikiDragonException {
        databaseDirectory = pDatabaseDirectory;
        Properties lProperties = new Properties();
        try {
            BufferedReader lReader = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/neobat4j.properties"), Charset.forName("UTF-8")));
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

    public NeoBatWikiDragonDatabase(File pDatabaseDirectory, boolean pReset, Map<String, String> pParameters) throws WikiDragonException {
        databaseDirectory = pDatabaseDirectory;
        parameters = pParameters;
        if (pReset) {
            reset();
        }
        else {
            initialize();
        }
    }

    public NeoBatWikiDragonDatabase(File pDatabaseDirectory, boolean pReset, File pPropertiesFile) throws WikiDragonException {
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
            logger.debug("Flushing...");
            flush();
            logger.debug("Shutdown IndexProvider...");
            luceneBatchInserterIndexProvider.shutdown();
            logger.debug("Shutdown Database...");
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
        MediaWikiCollection lResult = null;
        for (Long lNode:getMediaWikiNodeIndex(NeoBatWikiObject.GLOBALINDEXID).get(NeoBatWikiObject.ATTR_WIKIOBJECT_TYPE, NodeType.MEDIAWIKI_COLLECTION.name())) {
            assert (lResult == null);
            lResult = (MediaWikiCollection)wikiObjectFactory.getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public WikiObjectFactory getWikiObjectFactory() {
        return wikiObjectFactory;
    }

    @Override
    public WikiTransaction beginTx() {
        return new NeoBatWikiTransaction(this);
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
        return NeoBatWikiDataEntity.create(this, pEntityID);
    }

    @Override
    public WikiObjectIterator<WikiDataEntity> getWikiDataEntityIterator() {
        return new NeoBatWikiObjectIterator<WikiDataEntity>(this, getMediaWikiNodeIndex(NeoBatWikiObject.GLOBALINDEXID).get(NeoBatWikiDataEntity.ATTR_WIKIOBJECT_TYPE, NodeType.WIKIDATAENTITY.name()));
    }

    @Override
    public WikiDataEntity getWikiDataEntity(String pEntityID) throws WikiDragonException {
        WikiDataEntity lResult = null;
        for (Long lNode:getMediaWikiNodeIndex(NeoBatWikiObject.GLOBALINDEXID).get(NeoBatWikiDataEntity.ATTR_WIKIDATAENTITY_ENTITYID, pEntityID)) {
            assert (lResult == null);
            lResult = (WikiDataEntity)wikiObjectFactory.getWikiObject(lNode);
        }
        return lResult;
    }

    protected void flush() {
        for (BatchInserterIndex lIndex:mediaWikiNodeIndexMap.valueCollection()) {
            lIndex.flush();
        }
        for (BatchInserterIndex lIndex:mediaWikiRelationshipIndexMap.valueCollection()) {
            lIndex.flush();
        }
    }

    protected BatchInserterIndex getMediaWikiNodeIndex(long pMediaWikiNodeId) {
        synchronized (mediaWikiNodeIndexMap) {
            if (!mediaWikiNodeIndexMap.containsKey(pMediaWikiNodeId)) {
                Map<String, String> lIndexConfig = new HashMap<>();
                lIndexConfig.put("type", "exact");
                lIndexConfig.put("to_lower_case", "false");
                mediaWikiNodeIndexMap.put(pMediaWikiNodeId, luceneBatchInserterIndexProvider.nodeIndex(Long.toString(pMediaWikiNodeId), lIndexConfig));
            }
            return mediaWikiNodeIndexMap.get(pMediaWikiNodeId);
        }
    }

    protected BatchInserterIndex getMediaWikiRelationshipIndex(long pMediaWikiNodeId) {
        synchronized (mediaWikiNodeIndexMap) {
            if (!mediaWikiNodeIndexMap.containsKey(pMediaWikiNodeId)) {
                Map<String, String> lIndexConfig = new HashMap<>();
                lIndexConfig.put("type", "exact");
                lIndexConfig.put("to_lower_case", "false");
                mediaWikiNodeIndexMap.put(pMediaWikiNodeId, luceneBatchInserterIndexProvider.relationshipIndex(Long.toString(pMediaWikiNodeId), lIndexConfig));
            }
            return mediaWikiNodeIndexMap.get(pMediaWikiNodeId);
        }
    }

    protected void initialize() throws WikiDragonException {
        logger.info("Initializing database in "+databaseDirectory.getAbsolutePath()+"...");
        if (!databaseDirectory.exists()) databaseDirectory.mkdirs();

        try {
            database = parameters == null ? new SynchronizedBatchInserter(databaseDirectory) : new SynchronizedBatchInserter(databaseDirectory, parameters);
        }
        catch (IOException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }

        // Init Indexes
        mediaWikiNodeIndexMap = new TLongObjectHashMap<>();
        mediaWikiRelationshipIndexMap = new TLongObjectHashMap<>();
        luceneBatchInserterIndexProvider = new LuceneBatchInserterIndexProvider(database);
        //
        wikiObjectFactory = new NeoBatWikiObjectFactory(this);
        if (getMediaWikiCollection() == null) {
            logger.debug("Creating initial MediaWikiCollection in " + databaseDirectory.getAbsolutePath());
            NeoBatMediaWikiCollection.create(this);
        }
        flush();
        logger.info("Initializing database in " + databaseDirectory.getAbsolutePath() + "... done");
    }

}
