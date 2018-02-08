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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.MediaWikiCollection;
import org.hucompute.wikidragon.core.revcompression.NoneRevisionCompressor;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressor;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public class NeoMediaWikiCollection extends NeoWikiObject implements MediaWikiCollection {

    private static Logger logger = LogManager.getLogger(NeoMediaWikiCollection.class);

    /**
     * Array of nodes representing MediaWikis. Not indexed.
     */
    protected static final String ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS = "ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS";

    protected NeoMediaWikiCollection(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
    }

    @Override
    public MediaWiki getMediaWiki(String pDbName) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            MediaWiki lResult = null;
            for (Node lNode : wikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).get(NeoMediaWiki.ATTR_MEDIAWIKI_DBNAME, pDbName)) {
                assert lResult == null;
                lResult = (NeoMediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public MediaWiki createMediaWiki(String pDbName) throws WikiDragonException {
        return NeoMediaWiki.create(wikiDragonDatabase, pDbName);
    }

    @Override
    public Collection<MediaWiki> getMediaWikis() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Set<MediaWiki> lResult = new HashSet<>();
            for (long l:(long[])node.getProperty(ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS, new long[0])) {
                lResult.add((NeoMediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l));
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public MediaWiki importMediaWiki(InputStream pInputStream, String pCharset) throws WikiDragonException {
        return importMediaWiki(pInputStream, pCharset, new NoneRevisionCompressor());
    }

    @Override
    public MediaWiki importMediaWiki(InputStream pInputStream, String pCharset, RevisionCompressor pRevisionCompressor) throws WikiDragonException {
        NeoMediaWikiDumpImporter lNeoMediaWikiDumpImporter = new NeoMediaWikiDumpImporter(this, pRevisionCompressor);
        lNeoMediaWikiDumpImporter.importMediaWikiDump(pInputStream, pCharset);
        return lNeoMediaWikiDumpImporter.getMediaWiki();
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            default: return false;
        }
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        return false;
    }

    protected static NeoMediaWikiCollection create(NeoWikiDragonDatabase pNeoWikiDragonDatabase) throws WikiDragonException {
        try (Transaction tx = pNeoWikiDragonDatabase.database.beginTx()) {
            NeoMediaWikiCollection lResult = null;
            // Check if it already exists
            if (pNeoWikiDragonDatabase.getMediaWikiCollection() != null) throw new WikiDragonException("MediaWikiCollection already exists");
            Node lNode = pNeoWikiDragonDatabase.database.createNode();
            lNode.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.MEDIAWIKI_COLLECTION.name());
            pNeoWikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).add(lNode, ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.MEDIAWIKI_COLLECTION.name());
            lResult = (NeoMediaWikiCollection)pNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode);
            tx.success();
            return lResult;
        }
    }
}
