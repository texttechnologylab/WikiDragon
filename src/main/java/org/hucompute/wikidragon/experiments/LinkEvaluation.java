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

import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.WikiPageLink;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LinkEvaluation {

    private static Logger logger = LogManager.getLogger(LinkEvaluation.class);

    public static Map<MediaWikiConst.LinkType, LinkEvaluationResult> evaluateLinks(MediaWiki pMediaWiki, ZonedDateTime pTestTimestamp, ZonedDateTime pGoldTimestamp) throws WikiDragonException {
        long lPageCounter = 0;
        TObjectDoubleHashMap<MediaWikiConst.LinkType> lMeanRecall = new TObjectDoubleHashMap<>();
        TObjectDoubleHashMap<MediaWikiConst.LinkType> lMeanPrecision = new TObjectDoubleHashMap<>();
        TObjectDoubleHashMap<MediaWikiConst.LinkType> lMeanFScore = new TObjectDoubleHashMap<>();
        for (Page lPage:pMediaWiki.getPages()) {
            lPageCounter++;
            logger.info("Link Evaluation: "+lPageCounter+" pages processed");
            Map<MediaWikiConst.LinkType, Set<Page>> lTestMap = new HashMap<>();
            Map<MediaWikiConst.LinkType, Set<Page>> lGoldMap = new HashMap<>();
            TObjectIntHashMap<MediaWikiConst.LinkType> lTruePositives = new TObjectIntHashMap<>();
            TObjectDoubleHashMap<MediaWikiConst.LinkType> lRecall = new TObjectDoubleHashMap<>();
            TObjectDoubleHashMap<MediaWikiConst.LinkType> lPrecision = new TObjectDoubleHashMap<>();
            TObjectDoubleHashMap<MediaWikiConst.LinkType> lFScore = new TObjectDoubleHashMap<>();
            for (MediaWikiConst.LinkType lType:MediaWikiConst.LinkType.values()) {
                lTestMap.put(lType, new HashSet<>());
                lGoldMap.put(lType, new HashSet<>());
                lTruePositives.put(lType, 0);
                lPrecision.put(lType, 0);
            }
            for (WikiPageLink lLink:lPage.getWikiPageLinksOut(pTestTimestamp)) {
                lTestMap.get(lLink.getLinkType()).add(lLink.getTarget());
            }
            for (WikiPageLink lLink:lPage.getWikiPageLinksOut(pGoldTimestamp)) {
                lGoldMap.get(lLink.getLinkType()).add(lLink.getTarget());
            }
            for (MediaWikiConst.LinkType lType:MediaWikiConst.LinkType.values()) {
                for (Page lTestPage:lGoldMap.get(lType)) {
                    if (lTestMap.get(lType).contains(lTestPage)) {
                        lTruePositives.adjustOrPutValue(lType, 1, 1);
                    }
                    else {
                        //logger.info(lType.name()+" - "+lPage.getQualifiedTitle()+" - "+lTestPage.getQualifiedTitle());
                    }
                }
            }
            for (MediaWikiConst.LinkType lType:MediaWikiConst.LinkType.values()) {
                lRecall.put(lType, lGoldMap.get(lType).size() > 0 ? lTruePositives.get(lType)/(double)(lGoldMap.get(lType).size()) : 1);
                lPrecision.put(lType, lTestMap.get(lType).size() > 0 ? lTruePositives.get(lType)/(double)(lTestMap.get(lType).size()) : 1);
                lFScore.put(lType, (lRecall.get(lType) + lPrecision.get(lType)) > 0 ? (2 * lRecall.get(lType) * lPrecision.get(lType))/(lRecall.get(lType) + lPrecision.get(lType)) : 0);
                //
                lMeanRecall.adjustOrPutValue(lType, lRecall.get(lType), lRecall.get(lType));
                lMeanPrecision.adjustOrPutValue(lType, lPrecision.get(lType), lPrecision.get(lType));
                lMeanFScore.adjustOrPutValue(lType, lFScore.get(lType), lFScore.get(lType));
            }
        }
        Map<MediaWikiConst.LinkType, LinkEvaluationResult> lResult = new HashMap<>();
        for (MediaWikiConst.LinkType lType:MediaWikiConst.LinkType.values()) {
            lResult.put(lType, new LinkEvaluationResult(lMeanRecall.get(lType)/lPageCounter, lMeanPrecision.get(lType)/lPageCounter, lMeanFScore.get(lType)/lPageCounter, lPageCounter));
        }
        return lResult;
    }

    public static class LinkEvaluationResult {
        protected double recall;
        protected double precision;
        protected double fScore;
        protected long pageCount;

        protected LinkEvaluationResult(double recall, double precision, double fScore, long pageCount) {
            this.recall = recall;
            this.precision = precision;
            this.fScore = fScore;
            this.pageCount = pageCount;
        }

        public double getRecall() {
            return recall;
        }

        public double getPrecision() {
            return precision;
        }

        public double getfScore() {
            return fScore;
        }

        public long getPageCount() {
            return pageCount;
        }
    }

}
