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

package org.hucompute.wikidragon.experiments;

import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.model.neo.NeoWikiDragonDatabase;
import org.hucompute.wikidragon.core.model.neobat.NeoBatWikiDragonDatabase;
import org.hucompute.wikidragon.core.parsing.filter.XOWATierMassParserAllFilter;
import org.hucompute.wikidragon.core.revcompression.diff.DiffRevisionCompressor;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.hucompute.wikidragon.core.util.StringUtil;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Code to conduct experiments published in LREC 2018:
 * Gleim, Mehler, Song: "WikiDragon: A Java Framework For Diachronic Content And Network Analysis Of MediaWikis"
 * @author Rüdiger Gleim
 */
public class SimpleWikiLRECExperiments {

    private static void printSyntax() {
        System.out.println("-db <db-directory> <command> [params]");
        System.out.println("Reset database and perform basic import");
        System.out.println("  SimpleWikiLRECExperiments -db dbdirectory dumpimport mydumpfile.xml");
        System.out.println("Create page tier and page network at specific timestamp");
        System.out.println("  SimpleWikiLRECExperiments -db dbdirectory pagetier 2004-01-01");
        System.out.println("Import SQL link dumps");
        System.out.println("  SimpleWikiLRECExperiments -db dbdirectory importlinkdumps pagelinks.sql categorylinks.sql redirects.sql");
        System.out.println("Evaluate Links from 2017-10-01 with SQL dump");
        System.out.println("  SimpleWikiLRECExperiments -db dbdirectory evaluatelinks 2017-10-01");
        System.out.println("Export article graph (based on SQL dump links");
        System.out.println("  SimpleWikiLRECExperiments -db dbdirectory exportgraph result.graphml");
        System.out.println("Export article graph (based on pagetier at at 2004-01-01)");
        System.out.println("  SimpleWikiLRECExperiments -db dbdirectory exportgraph 2004-01-01 result_2004-01-01.graphml");
        System.exit(0);
    }

    public static void main(String[] args) throws Exception {
        long lStart = System.currentTimeMillis();
        if (args.length < 3) printSyntax();
        File lDBDir = new File(args[1]);
        String lCommand = args[2];
        switch (lCommand) {
            case "dumpimport": {
                try (WikiDragonDatabase lDB = new NeoBatWikiDragonDatabase(lDBDir, true)) {
                    lDB.getMediaWikiCollection().importMediaWiki(IOUtil.getInputStream(new File(args[3])), "UTF-8", new DiffRevisionCompressor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors()*2));
                    //lDB.getMediaWikiCollection().importMediaWiki(IOUtil.getInputStream(new File(args[3])), "UTF-8", new NoneRevisionCompressor());
                }
                break;
            }
            case "pagetier": {
                try (WikiDragonDatabase lDB = new NeoBatWikiDragonDatabase(lDBDir, false)) {
                    MediaWiki lMediaWiki = lDB.getMediaWikiCollection().getMediaWikis().iterator().next();
                    ZonedDateTime lTimestamp = StringUtil.string2ZonedDateTime(args[3]+"T00:00:00Z");
                    // Parse HTML and create PageTiers
                    lMediaWiki.createPageTiersHTML(lTimestamp, new XOWATierMassParserAllFilter(), Runtime.getRuntime().availableProcessors());
                    // Extract Links
                    lMediaWiki.extractPageTierNetwork(lTimestamp);
                }
                break;
            }
            case "importlinkdumps": {
                try (WikiDragonDatabase lDB = new NeoBatWikiDragonDatabase(lDBDir, false)) {
                    MediaWiki lMediaWiki = lDB.getMediaWikiCollection().getMediaWikis().iterator().next();
                    lMediaWiki.importLinkDump(new File(args[3]), MediaWikiConst.LinkType.ARTICLE);
                    lMediaWiki.importLinkDump(new File(args[4]), MediaWikiConst.LinkType.CATEGORIZATION);
                    lMediaWiki.importLinkDump(new File(args[5]), MediaWikiConst.LinkType.REDIRECT);
                }
                break;
            }
            case "evaluatelinks": {
                try (WikiDragonDatabase lDB = new NeoWikiDragonDatabase(lDBDir, false)) {
                    try (WikiTransaction tx = lDB.beginTx()) {
                        MediaWiki lMediaWiki = lDB.getMediaWikiCollection().getMediaWikis().iterator().next();
                        ZonedDateTime lTimestamp = StringUtil.string2ZonedDateTime(args[3] + "T00:00:00Z");
                        Map<MediaWikiConst.LinkType, LinkEvaluation.LinkEvaluationResult> lEvaluationResults = LinkEvaluation.evaluateLinks(lMediaWiki, lTimestamp, WikiDragonConst.NULLDATETIME);
                        for (MediaWikiConst.LinkType lType : new MediaWikiConst.LinkType[]{MediaWikiConst.LinkType.ARTICLE, MediaWikiConst.LinkType.CATEGORIZATION, MediaWikiConst.LinkType.REDIRECT}) {
                            System.out.println("Precision "+lType.name() + "\t" + lEvaluationResults.get(lType).getPrecision());
                            System.out.println("Recall "+lType.name() + "\t" + lEvaluationResults.get(lType).getRecall());
                            System.out.println("FScore "+lType.name() + "\t" + lEvaluationResults.get(lType).getfScore());
                        }
                        tx.success();
                    }
                }
                break;
            }
            case "exportgraph": {
                try (WikiDragonDatabase lDB = new NeoWikiDragonDatabase(lDBDir, false)) {
                    try (WikiTransaction tx = lDB.beginTx()) {
                        MediaWiki lMediaWiki = lDB.getMediaWikiCollection().getMediaWikis().iterator().next();
                        ZonedDateTime lTimestamp = null;
                        File lExportFile = null;
                        if (args.length > 4) {
                            lTimestamp = StringUtil.string2ZonedDateTime(args[3] + "T00:00:00Z");
                            lExportFile = new File(args[4]);
                        } else {
                            lExportFile = new File(args[3]);
                        }
                        if (lTimestamp != null) {
                            List<PageTier> lPageTiers = new ArrayList<>();
                            for (Page lPage : lMediaWiki.getPages()) {
                                for (PageTier lTier : lPage.getPageTierList()) {
                                    System.out.println(lTier.getTimestamp().toString());
                                }
                                PageTier lPageTier = lPage.getPageTierAt(lTimestamp);
                                if (lPageTier != null) {
                                    lPageTiers.add(lPageTier);
                                }
                            }
                            lDB.getIOManager().exportPageTiersBF(lPageTiers, lExportFile, MediaWikiConst.LinkType.ARTICLE, MediaWikiConst.LinkType.CATEGORIZATION, MediaWikiConst.LinkType.REDIRECT);
                        }
                        else {
                            lDB.getIOManager().exportBorlandFormatCurrent(lExportFile, new HashSet<>(lMediaWiki.getPagesList()));
                        }
                        tx.success();
                    }
                }
                break;
            }
        }
        System.out.println("Elapsed Time: "+(System.currentTimeMillis()-lStart)+"ms");
    }

}
