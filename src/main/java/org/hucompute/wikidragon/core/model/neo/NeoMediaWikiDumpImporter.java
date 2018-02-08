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
import org.hucompute.wikidragon.core.dump.MediaWikiDumpParser;
import org.hucompute.wikidragon.core.events.ImportListener;
import org.hucompute.wikidragon.core.events.RevisionCompressionListener;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.revcompression.NoneRevisionCompressor;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressor;
import org.hucompute.wikidragon.core.util.BandWidthEvalInputStream;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public class NeoMediaWikiDumpImporter implements ImportListener, RevisionCompressionListener {

    private static Logger logger = LogManager.getLogger(NeoMediaWikiDumpImporter.class);

    private NeoWikiDragonDatabase wikiDragonDatabase;
    private NeoMediaWikiCollection mediaWikiCollection;
    private NeoMediaWiki mediaWiki;
    private NeoPage currentPage;
    private String currentPageTitle;
    private int currentPageNamespaceID;
    private long currentPageId;
    private Map<Integer, Namespace> namespaceMap;
    private RevisionCompressor revisionCompressor;
    private Transaction transaction;
    private long revisionCounter = 0;
    private BandWidthEvalInputStream inputStream;
    private long pageCounter = 0;
    private long startTime;
    private long elapsedTimeCreatePages;
    private long elapsedTimeCreateRevisions;
    private long elapsedTimeCreateContributors;
    private MediaWikiDumpImportFilter mediaWikiDumpImportFilter;

    private TLongObjectHashMap<Contributor> contributorCache;

    public NeoMediaWikiDumpImporter(NeoMediaWikiCollection pNeoMediaWikiCollection, RevisionCompressor pRevisionCompressor, MediaWikiDumpImportFilter pMediaWikiDumpImportFilter) {
        mediaWikiDumpImportFilter = new MediaWikiDumpImportFilterAll();
        mediaWikiCollection = pNeoMediaWikiCollection;
        wikiDragonDatabase = mediaWikiCollection.wikiDragonDatabase;
        revisionCompressor = pRevisionCompressor;
        revisionCompressor.addRevisionCompressionListener(this);
        mediaWikiDumpImportFilter = pMediaWikiDumpImportFilter;
    }

    public NeoMediaWikiDumpImporter(NeoMediaWikiCollection pNeoMediaWikiCollection, RevisionCompressor pRevisionCompressor) {
        this(pNeoMediaWikiCollection, pRevisionCompressor, new MediaWikiDumpImportFilterAll());
    }

    public NeoMediaWikiDumpImporter(NeoMediaWikiCollection pNeoMediaWikiCollection) {
        this(pNeoMediaWikiCollection, new NoneRevisionCompressor());
    }

    public RevisionCompressor getRevisionCompressor() {
        return revisionCompressor;
    }

    public NeoMediaWiki getMediaWiki() {
        return mediaWiki;
    }

    public NeoMediaWiki importMediaWikiDump(InputStream pInputStream, String pCharSet) throws WikiDragonException {
        if (startTime == 0) startTime = System.currentTimeMillis();
        inputStream = new BandWidthEvalInputStream(pInputStream);
        InputStreamReader lReader = new InputStreamReader(inputStream, Charset.forName(pCharSet));
        contributorCache = new TLongObjectHashMap<>();
        mediaWiki = null;
        transaction = wikiDragonDatabase.database.beginTx();
        try {
            MediaWikiDumpParser lMediaWikiDumpParser = new MediaWikiDumpParser();
            lMediaWikiDumpParser.addImportListener(this);
            lMediaWikiDumpParser.parse(lReader);
            lReader.close();
            revisionCompressor.close();
            transaction.success();
            return mediaWiki;
        }
        catch (Exception e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
        finally {
            if (transaction != null) transaction.close();
        }
    }

    @Override
    public void mediaWiki(Map<String, String> pRootAttributes, String pSiteName, String pDbName, String pBase, String pGenerator, MediaWikiConst.Case pCase, String pApiUrl) throws WikiDragonException {
        mediaWiki = (NeoMediaWiki) wikiDragonDatabase.getMediaWikiCollection().getMediaWiki(pDbName);
        if (mediaWiki != null) {
            logger.info("Using the existing MediaWiki '"+pDbName+"' for import");
        }
        else {
            logger.info("Creating a new MediaWiki '"+pDbName+"' for import");
            mediaWiki = (NeoMediaWiki) wikiDragonDatabase.getMediaWikiCollection().createMediaWiki(pDbName);
            mediaWiki.setSiteName(pSiteName);
            mediaWiki.setBase(pBase);
            mediaWiki.setGenerator(pGenerator);
            mediaWiki.setCase(pCase);
            mediaWiki.setApiUrl(pApiUrl);
        }
    }

    @Override
    public void namespace(int pId, MediaWikiConst.Case pCase, String pName, String pCanonicalName, Set<String> pAliases, boolean pSubPages, MediaWikiConst.Model pDefaultContentModel) throws WikiDragonException {
        if (mediaWiki.getNamespace(pName) == null) {
            logger.info("Creating a new Namespace '"+pName+"'");
            mediaWiki.createNamespace(pId, pCase, pName, pCanonicalName, pAliases, pSubPages, pDefaultContentModel);
        }
        else {
            logger.info("Skipping Namespace '"+pName+"' because it already exists");
        }
    }

    @Override
    public void page(String pTitle, int pNamespaceId, long pId) throws WikiDragonException {
        currentPageTitle = pTitle;
        currentPageNamespaceID = pNamespaceId;
        currentPageId = pId;
        if (!mediaWikiDumpImportFilter.acceptPage(pId, pNamespaceId, pTitle)) return;
        long lStart = System.currentTimeMillis();
        if (namespaceMap == null) namespaceMap = mediaWiki.getNamespaceIdMap();
        currentPage = (NeoPage)mediaWiki.getPage(pId);
        if (currentPage == null) {
            if ((pNamespaceId != 0) && (pTitle.contains(":"))) {
                pTitle = pTitle.substring(pTitle.indexOf(":")+1);
            }
            long lElapsed = System.currentTimeMillis()-startTime;
            double lPagePerc = (elapsedTimeCreatePages*100)/(double)lElapsed;
            double lRevisionPerc = (elapsedTimeCreateRevisions*100)/(double)lElapsed;
            double lContributorPerc = (elapsedTimeCreateContributors*100)/(double)lElapsed;
            logger.info("Importing currentPage '" + pTitle + "', namespaceId=" + pNamespaceId + ", pageId=" + pId+", pageCounter="+(++pageCounter)+", pages="+lPagePerc+", rev="+lRevisionPerc+", contr="+lContributorPerc);
            currentPage = NeoPage.create(mediaWiki, pId, (NeoNamespace)namespaceMap.get(pNamespaceId), pTitle);
        }
        else {
            logger.info("Skipping creating of existing currentPage '" + pTitle + "', namespaceId=" + pNamespaceId + ", pageId=" + pId);
        }
        elapsedTimeCreatePages += System.currentTimeMillis()-lStart;
    }

    @Override
    public void revision(long pId, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException {
        if (!mediaWikiDumpImportFilter.acceptRevision(currentPageId, currentPageNamespaceID, currentPageTitle)) return;
        Node lRevisionNode = ((NeoWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(mediaWiki.node.getId()).get(NeoRevision.ATTR_REVISION_ID, pId).getSingle();
        if (lRevisionNode == null) {
            revisionCompressor.submitRevision(currentPage, pId, pParentId, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
        }
    }

    @Override
    public void revision(long pId, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException {
        if (!mediaWikiDumpImportFilter.acceptRevision(currentPageId, currentPageNamespaceID, currentPageTitle)) return;
        Node lRevisionNode = ((NeoWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(mediaWiki.node.getId()).get(NeoRevision.ATTR_REVISION_ID, pId).getSingle();
        if (lRevisionNode == null) {
            revisionCompressor.submitRevision(currentPage, pId, pParentId, pTimestamp, pUserName, pUserID, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
        }
    }

    @Override
    public void revisionCompressed(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, byte[] pCompressedText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        long lStart = System.currentTimeMillis();
        Contributor lContributor = contributorCache.get(pUserID);
        if (lContributor == null) {
            lContributor = mediaWiki.getContributor(pUserID);
            if (lContributor == null) {
                lContributor = NeoContributor.create(mediaWiki, pUserName, pUserID);
            }
            contributorCache.put(lContributor.getId(), lContributor);
        }
        elapsedTimeCreateContributors += System.currentTimeMillis()-lStart;
        lStart = System.currentTimeMillis();
        pPage.createRevision(pRevisionID, pParentId, pTimestamp, lContributor, pComment, pMinor, pModel, pFormat, pSHA1, pCompressedText, pCompression, pBytes);
        revisionCounter++;
        if (revisionCounter % 100 == 0) {
            transaction.success();
            transaction.close();
            transaction = wikiDragonDatabase.database.beginTx();
        }
        elapsedTimeCreateRevisions += System.currentTimeMillis()-lStart;
    }

    @Override
    public void revisionCompressed(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, byte[] pCompressedText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        long lStart = System.currentTimeMillis();
        pPage.createRevision(pRevisionID, pParentId, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pCompressedText, pCompression, pBytes);
        revisionCounter++;
        if (revisionCounter % 100 == 0) {
            transaction.success();
            transaction.close();
            transaction = wikiDragonDatabase.database.beginTx();
        }
        elapsedTimeCreateRevisions += System.currentTimeMillis()-lStart;
    }
}
