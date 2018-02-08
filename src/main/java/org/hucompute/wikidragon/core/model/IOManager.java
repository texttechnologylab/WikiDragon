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

package org.hucompute.wikidragon.core.model;

import gnu.trove.map.hash.TObjectLongHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.hucompute.wikidragon.html.HTML2TEIParser;

import java.io.*;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Rüdiger Gleim
 */
public class IOManager {

    private static Logger logger = LogManager.getLogger(IOManager.class);

    protected WikiDragonDatabase wikiDragonDatabase;

    public IOManager(WikiDragonDatabase pWikiDragonDatabase) {
        wikiDragonDatabase = pWikiDragonDatabase;
    }

    public void exportRevisionsTEICorpus(Collection<Revision> pRevisions, File pFile) throws WikiDragonException, IOException {
        exportRevisionsTEICorpus(pRevisions, new FileOutputStream(pFile));
    }

    public void exportRevisionsTEICorpus(Collection<Revision> pRevisions, OutputStream pOutputStream) throws WikiDragonException, IOException {
        exportRevisionsTEICorpus(pRevisions, new OutputStreamWriter(pOutputStream, Charset.forName("UTF-8")));
    }

    public String getTEI(Revision pRevision) throws WikiDragonException {
        return getTEI(pRevision, true);
    }

    public String getTEI(Revision pRevision, boolean pRemoveCategoryLinksSection) throws WikiDragonException {
        Pattern lTitlePattern = Pattern.compile("<title>(.*?)</title>");
        Page lPage = pRevision.getPage();
        String lHtml = pRevision.getHtml();
        if (lHtml != null) {
            Matcher lMatcher = lTitlePattern.matcher(lHtml);
            Map<String, String> lMap = new HashMap<>();
            String lQualifiedTitle = lPage.getQualifiedTitle();
            if (lMatcher.find()) {
                lHtml = lHtml.substring(0, lMatcher.start(1)) + StringUtil.encodeXml(lQualifiedTitle) + lHtml.substring(lMatcher.end(1));
            }
            MediaWiki lMediaWiki = lPage.getMediaWiki();
            String lBase = lMediaWiki.getBase();
            if (!lBase.endsWith("/")) lBase = lBase + "/";
            lMap.put("pageURL", lBase + lQualifiedTitle);
            lMap.put("dbName", lMediaWiki.getDbName());
            lMap.put("pageID", Long.toString(lPage.getId()));
            lMap.put("revisionID", Long.toString(pRevision.getId()));
            lMap.put("namespaceID", Integer.toString(lPage.getNamespaceID()));
            lMap.put("namespace", lPage.getMediaWiki().getNamespace(lPage.getNamespaceID()).getName());
            lMap.put("timestamp", pRevision.getTimestamp().toString());
            return HTML2TEIParser.html2TEI(lHtml, lMap, pRemoveCategoryLinksSection);
        }
        else {
            return null;
        }
    }

    public String getTEI(PageTier pPageTier) throws WikiDragonException {
        Pattern lTitlePattern = Pattern.compile("<title>(.*?)</title>");
        Page lPage = pPageTier.getPage();
        String lHtml = pPageTier.getTierAttribute(PageTier.TierAttribute.HTML);
        Matcher lMatcher = lTitlePattern.matcher(lHtml);
        Map<String, String> lMap = new HashMap<>();
        String lQualifiedTitle = lPage.getQualifiedTitle();
        if (lMatcher.find()) {
            lHtml = lHtml.substring(0, lMatcher.start(1)) + StringUtil.encodeXml(lQualifiedTitle)+lHtml.substring(lMatcher.end(1));
        }
        MediaWiki lMediaWiki = lPage.getMediaWiki();
        String lBase = lMediaWiki.getBase();
        if (!lBase.endsWith("/")) lBase = lBase+"/";
        lMap.put("pageURL", lBase+lQualifiedTitle);
        lMap.put("dbName", lMediaWiki.getDbName());
        lMap.put("pageID", Long.toString(lPage.getId()));
        lMap.put("pageTierID", pPageTier.getUniqueId());
        lMap.put("namespaceID", Integer.toString(lPage.getNamespaceID()));
        lMap.put("namespace", lPage.getMediaWiki().getNamespace(lPage.getNamespaceID()).getName());
        lMap.put("timestamp", pPageTier.getTimestamp().toString());
        return HTML2TEIParser.html2TEI(lHtml, lMap);
    }

    public void exportRevisionsTEICorpus(Collection<Revision> pRevisions, Writer pWriter) throws WikiDragonException, IOException {
        pWriter.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        pWriter.write("<teiCorpus>\n");
        long lCounter = 0;
        for (Revision lRevision:pRevisions) {
            String lTEI = getTEI(lRevision);
            pWriter.write(lTEI);
            pWriter.write("\n");
            lCounter++;
            if (lCounter % 100 == 0) {
                logger.info(lCounter+" revisions exported");
            }
        }
        pWriter.write("</teiCorpus>\n");
    }

    public void exportPageTiersGraphML(Collection<PageTier> pPageTiers, File pFile, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException, IOException {
        BufferedWriter lWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pFile), Charset.forName("UTF-8")));
        exportPageTiersGraphML(pPageTiers, lWriter, pLinkTypes);
        lWriter.flush();
        lWriter.close();
    }

    public void exportPageTiersGraphML(Collection<PageTier> pPageTiers, Writer pWriter, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException, IOException {
        Set<MediaWikiConst.LinkType> lLinkSet = new HashSet<>();
        for (MediaWikiConst.LinkType lType:pLinkTypes) {
            lLinkSet.add(lType);
        }
        Set<PageTier> lSet = new HashSet<>(pPageTiers);
        pWriter.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        pWriter.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
        pWriter.write("<key id=\"d0\" for=\"node\" attr.name=\"title\" attr.type=\"string\"/>\n");
        pWriter.write("<key id=\"d1\" for=\"node\" attr.name=\"ns\" attr.type=\"integer\"/>\n");
        pWriter.write("<key id=\"d2\" for=\"node\" attr.name=\"articleID\" attr.type=\"integer\"/>\n");
        pWriter.write("<key id=\"d3\" for=\"node\" attr.name=\"timestamp\" attr.type=\"string\"/>\n");
        pWriter.write("<key id=\"d4\" for=\"edge\" attr.name=\"type\" attr.type=\"string\"/>\n");
        TObjectLongHashMap<String> lNodeMap = new TObjectLongHashMap<>();
        long lNodeIDCounter = 0;
        for (PageTier lTier:pPageTiers) {
            lNodeIDCounter++;
            Page lPage = lTier.getPage();
            lNodeMap.put(lPage.getId()+"\t"+StringUtil.zonedDateTime2String(lTier.getTimestamp(), ZoneId.of("UTC")), lNodeIDCounter);
            pWriter.write("<node id=\"n"+lNodeIDCounter+"\">\n");
            pWriter.write("<data key=\"d0\">"+StringUtil.encodeXml(lPage.getTitle())+"</data>\n");
            pWriter.write("<data key=\"d1\">"+lPage.getNamespaceID()+"</data>\n");
            pWriter.write("<data key=\"d2\">"+lPage.getId()+"</data>\n");
            pWriter.write("<data key=\"d3\">"+StringUtil.encodeXml(StringUtil.zonedDateTime2String(lTier.getTimestamp(), ZoneId.of("UTC")))+"</data>\n");
            pWriter.write("</node>\n");
        }
        long lEdgeIDCounter = 0;
        for (PageTier lTier:pPageTiers) {
            long lSourceID = lNodeMap.get(lTier.getPage().getId()+"\t"+StringUtil.zonedDateTime2String(lTier.getTimestamp(), ZoneId.of("UTC")));
            for (WikiPageLink lLink:lTier.getWikiPageLinksOut()) {
                if (lLinkSet.contains(lLink.getLinkType())) {
                    lEdgeIDCounter++;
                    long lTargetID = lNodeMap.get(lLink.getTarget().getId()+"\t"+StringUtil.zonedDateTime2String(lLink.getTimestamp(), ZoneId.of("UTC")));
                    if (lTargetID > 0) {
                        pWriter.write("<edge id=\"e"+lEdgeIDCounter+"\" source=\"n"+lSourceID+"\" target=\"n"+lTargetID+"\">\n");
                        pWriter.write("<data key=\"d4\">"+StringUtil.encodeXml(lLink.getLinkType().name())+"</data>\n");
                        pWriter.write("</edge>\n");
                    }
                }
            }
        }
        pWriter.write("</graphml>\n");
    }

    public void exportPageTiersBF(Collection<PageTier> pPageTiers, File pFile, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException, IOException {
        BufferedWriter lWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pFile), Charset.forName("UTF-8")));
        exportPageTiersBF(pPageTiers, lWriter, pLinkTypes);
        lWriter.flush();
        lWriter.close();
    }

    public void exportPageTiersBF(Collection<PageTier> pPageTiers, Writer pWriter, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException, IOException {
        Set<MediaWikiConst.LinkType> lLinkSet = new HashSet<>();
        for (MediaWikiConst.LinkType lType:pLinkTypes) {
            lLinkSet.add(lType);
        }
        Set<PageTier> lSet = new HashSet<>(pPageTiers);
        pWriter.write("directed\n");
        pWriter.write("SimilarityGraph\n");
        pWriter.write("Vertex Attributes:[Title¤String];[NSID¤Integer];[Timestamp¤String];\n");
        pWriter.write("Edge Attributes:[Type¤String];\n");
        pWriter.write("ProbabilityMassOfGraph: 0\n");
        pWriter.write("Vertices:\n");
        TObjectLongHashMap<String> lNodeMap = new TObjectLongHashMap<>();
        long lNodeIDCounter = 0;
        for (PageTier lTier:pPageTiers) {
            lNodeIDCounter++;
            Page lPage = lTier.getPage();
            lNodeMap.put(lPage.getId()+"\t"+StringUtil.zonedDateTime2String(lTier.getTimestamp(), ZoneId.of("UTC")), lNodeIDCounter);
            pWriter.write("n"+lNodeIDCounter+"¤[Title¤"+StringUtil.encodeBF(lPage.getTitle())+"¤]¤[NSID¤"+lPage.getNamespaceID()+"¤]¤[Timestamp¤"+StringUtil.encodeBF(StringUtil.zonedDateTime2String(lTier.getTimestamp(), ZoneId.of("UTC")))+"¤]¤\n");
        }
        pWriter.write("Edges:\n");
        long lEdgeIDCounter = 0;
        for (PageTier lTier:pPageTiers) {
            long lSourceID = lNodeMap.get(lTier.getPage().getId()+"\t"+StringUtil.zonedDateTime2String(lTier.getTimestamp(), ZoneId.of("UTC")));
            for (WikiPageLink lLink:lTier.getWikiPageLinksOut()) {
                if (lLinkSet.contains(lLink.getLinkType())) {
                    lEdgeIDCounter++;
                    long lTargetID = lNodeMap.get(lLink.getTarget().getId()+"\t"+StringUtil.zonedDateTime2String(lLink.getTimestamp(), ZoneId.of("UTC")));
                    if (lTargetID > 0) {
                        pWriter.write("n"+lSourceID+"¤n"+lTargetID+"¤1.0¤[Type¤"+StringUtil.encodeBF(lLink.getLinkType().name())+"¤]¤\n");
                    }
                }
            }
        }
    }

    public void exportPageTiersSQL(Collection<Page> pPages, File pFile, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException, IOException {
        BufferedWriter lWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pFile), Charset.forName("UTF-8")));
        exportPageTiersSQL(pPages, lWriter, pLinkTypes);
        lWriter.flush();
        lWriter.close();
    }

    public void exportPageTiersSQL(Collection<Page> pPages, Writer pWriter, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException, IOException {
        Set<MediaWikiConst.LinkType> lLinkSet = new HashSet<>();
        for (MediaWikiConst.LinkType lType:pLinkTypes) {
            lLinkSet.add(lType);
        }
        Set<Page> lSet = new HashSet<>(pPages);
        pWriter.write("directed\n");
        pWriter.write("SimilarityGraph\n");
        pWriter.write("Vertex Attributes:[Title¤String];[NSID¤Integer];[Timestamp¤String];\n");
        pWriter.write("Edge Attributes:[Type¤String];\n");
        pWriter.write("ProbabilityMassOfGraph: 0\n");
        pWriter.write("Vertices:\n");
        TObjectLongHashMap<String> lNodeMap = new TObjectLongHashMap<>();
        long lNodeIDCounter = 0;
        for (Page lPage:pPages) {
            lNodeIDCounter++;
            lNodeMap.put(lPage.getId()+"\t"+StringUtil.zonedDateTime2String(WikiDragonConst.NULLDATETIME, ZoneId.of("UTC")), lNodeIDCounter);
            pWriter.write("n"+lNodeIDCounter+"¤[Title¤"+StringUtil.encodeBF(lPage.getTitle())+"¤]¤[NSID¤"+lPage.getNamespaceID()+"¤]¤[Timestamp¤"+StringUtil.encodeBF(StringUtil.zonedDateTime2String(WikiDragonConst.NULLDATETIME, ZoneId.of("UTC")))+"¤]¤\n");
        }
        pWriter.write("Edges:\n");
        long lEdgeIDCounter = 0;
        for (Page lPage:pPages) {
            long lSourceID = lNodeMap.get(lPage.getId()+"\t"+StringUtil.zonedDateTime2String(WikiDragonConst.NULLDATETIME, ZoneId.of("UTC")));
            for (WikiPageLink lLink:lPage.getWikiPageLinksOut()) {
                if (lLinkSet.contains(lLink.getLinkType())) {
                    lEdgeIDCounter++;
                    long lTargetID = lNodeMap.get(lLink.getTarget().getId()+"\t"+StringUtil.zonedDateTime2String(WikiDragonConst.NULLDATETIME, ZoneId.of("UTC")));
                    if (lTargetID > 0) {
                        pWriter.write("n"+lSourceID+"¤n"+lTargetID+"¤1.0¤[Type¤"+StringUtil.encodeBF(lLink.getLinkType().name())+"¤]¤\n");
                    }
                }
            }
        }
    }

    public void exportPageTiersTEICorpus(Collection<PageTier> pPageTiers, Writer pWriter) throws WikiDragonException, IOException {
        pWriter.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        pWriter.write("<teiCorpus>\n");
        long lCounter = 0;
        for (PageTier lPageTiers:pPageTiers) {
            String lTEI = getTEI(lPageTiers);
            pWriter.write(lTEI);
            pWriter.write("\n");
            lCounter++;
            if (lCounter % 100 == 0) {
                logger.info(lCounter+" pageTiers exported");
            }
        }
        pWriter.write("</teiCorpus>\n");
    }

    public void exportBorlandFormatCurrent(MediaWiki pMediaWiki, File pFile, int... pNamespaceIDs) throws WikiDragonException, IOException {
        exportBorlandFormatCurrent(pMediaWiki, new OutputStreamWriter(new FileOutputStream(pFile), Charset.forName("UTF-8")), pNamespaceIDs);
    }

    public void exportBorlandFormatCurrent(MediaWiki pMediaWiki, Writer pWriter, int... pNamespaceIDs) throws WikiDragonException, IOException {
        try (WikiTransaction tx = wikiDragonDatabase.beginTx()) {
            Map<Integer, Namespace> lMap = pMediaWiki.getNamespaceIdMap();
            Set<Page> lPages = new HashSet<>();
            Set<Integer> lNamespaceIDs = new HashSet<>();
            if (pNamespaceIDs.length == 0) {
                lNamespaceIDs.addAll(lMap.keySet());
            }
            else {
                for (int i:pNamespaceIDs) {
                    lNamespaceIDs.add(i);
                }
            }
            for (int i:lNamespaceIDs) {
                for (Page lPage:lMap.get(i).getPages()) {
                    lPages.add(lPage);
                }
            }
            exportBorlandFormatCurrent(pWriter, lPages);
            tx.success();
        }
    }

    public void exportBorlandFormatCurrent(File pFile, Set<Page> pPages) throws WikiDragonException, IOException {
        exportBorlandFormatCurrent(new OutputStreamWriter(new FileOutputStream(pFile), Charset.forName("UTF-8")), pPages);

    }

    public void exportBorlandFormatCurrent(Writer pWriter, Set<Page> pPages) throws WikiDragonException, IOException {
        try (WikiTransaction tx = wikiDragonDatabase.beginTx()) {
            PrintWriter lWriter = new PrintWriter(pWriter);
            lWriter.write("directed\n");
            lWriter.write("SimilarityGraph\n");
            lWriter.write("Vertex Attributes:[Title¤String];[NSID¤Integer];[Timestamp¤String];\n");
            lWriter.write("Edge Attributes:[Type¤String];\n");
            lWriter.write("ProbabilityMassOfGraph: 0\n");
            lWriter.write("Vertices:\n");
            Map<Page, String> lAcceptedPagesMap = new HashMap<>();
            for (Page lPage : pPages) {
                Revision lLatestRevision = lPage.getLatestRevision();
                String lKey = lPage.getId() + "_" + lLatestRevision.getId();
                lAcceptedPagesMap.put(lPage, lKey);
                pWriter.write(lKey + "¤[Title¤" + StringUtil.encodeBF(lPage.getTitle()) + "¤]¤[NSID¤" + lPage.getNamespaceID() + "¤]¤[Timestamp¤" + StringUtil.encodeBF(StringUtil.zonedDateTime2String(lLatestRevision.getTimestamp(), ZoneId.of("UTC"))) + "¤]¤\n");
            }
            lWriter.write("Edges:\n");
            for (Map.Entry<Page, String> lEntry : lAcceptedPagesMap.entrySet()) {
                for (WikiPageLink lLink : lEntry.getKey().getWikiPageLinksOut()) {
                    if (lAcceptedPagesMap.containsKey(lLink.getTarget())) {
                        pWriter.write(lEntry.getValue() + "¤" + lAcceptedPagesMap.get(lLink.getTarget()) + "¤1.0¤[Type¤" + StringUtil.encodeBF(lLink.getLinkType().name()) + "¤]¤\n");
                    }
                }
            }
            lWriter.flush();
            lWriter.close();
            tx.success();
        }
    }

}
