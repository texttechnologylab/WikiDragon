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

package org.hucompute.wikidragon.core.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Rüdiger Gleim
 */
public class HTMLWikiLinkExtraction {

    private static Logger logger = LogManager.getLogger(HTMLWikiLinkExtraction.class);

    /**
     * Fetch all WikiLinks which actually exist at that specific point in time.
     * The HTML Code has to be valid for the source page at the requested point in time.
     * @param pMediaWiki
     * @param pSourcePage
     * @param pHtml
     * @param pTimestamp
     * @param pCacheMap
     * @return
     * @throws WikiDragonException
     */
    public static Set<WikiPageLink> extractWikiPageLinks(MediaWiki pMediaWiki, Page pSourcePage, String pHtml, ZonedDateTime pTimestamp, Map<Integer, Map<String, Page>> pCacheMap) throws WikiDragonException {
        Map<String, Namespace> lNSMap = pMediaWiki.getNamespaceMap();
        Set<WikiPageLink> lResult = new HashSet<>();
        String lHtml = pHtml.replaceAll("<!--.*?-->", ""); // Remove Comments
        boolean lIsRedirect = false;
        if (lHtml.toUpperCase().contains("REDIRECT")) {
            if (lHtml.substring(lHtml.indexOf("</h1>")+5).replaceAll("<.*?>", "").trim().toUpperCase().startsWith("REDIRECT")) {
                lIsRedirect = true;
            }
        }
        if (lHtml.contains("class=\"redirectText\"") || lHtml.contains("class=\"redirectMsg\"")) {
            lIsRedirect = true;
        }
        int lCatLinksDivIndex = lHtml.indexOf("id=\"catlinks\"");
        Pattern lPattern = Pattern.compile("<a[ ][^>]*?href=\"([^\">]+)");
        Matcher lMatcher = lPattern.matcher(lHtml);
        int lLinkCount = 0;
        int lSourceNSID = pSourcePage.getNamespaceID();
        if (!pCacheMap.containsKey(lSourceNSID)) {
            pCacheMap.put(lSourceNSID, new HashMap<>());
        }
        // Put current source Page to Cache
        pCacheMap.get(lSourceNSID).put(pSourcePage.getTitle(), pSourcePage);
        while (lMatcher.find()) {
            lLinkCount++;
            String lLink = lMatcher.group(1);
            if (lLink.contains("#")) {
                lLink = lLink.substring(0, lLink.indexOf("#"));
            }
            if (lLink.startsWith("/wiki/") && (lLink.length() > 6)) {
                try {
                    lLink = URLDecoder.decode(lLink.substring(6).replace("_", " "), "UTF-8");
                }
                catch (UnsupportedEncodingException e) {
                    logger.warn(e.getMessage(), e);
                }
                catch (IllegalArgumentException e) {
                    logger.warn(e.getMessage(), e);
                }
                if (lLink != null) {
                    String lNamespaceString = "";
                    int lColonIndex = lLink.indexOf(":");
                    Namespace lTargetNS = lNSMap.get("");
                    if (lColonIndex >= 0) {
                        lNamespaceString = lLink.substring(0, lColonIndex);
                        lTargetNS = lNSMap.get(lNamespaceString);
                        if (lTargetNS == null) {
                            lTargetNS = lNSMap.get("");
                        }
                    }
                    if (lTargetNS.getId() != 0) {
                        if (lColonIndex == lLink.length() - 1) {
                            lLink = "";
                        } else {
                            lLink = lLink.substring(lColonIndex + 1);
                        }
                    }
                    Page lTargetPage = null;
                    int lTargetNSID = lTargetNS != null ? lTargetNS.getId() : 0;
                    if (!pCacheMap.containsKey(lTargetNSID)) pCacheMap.put(lTargetNSID, new HashMap<>());

                    // Attempt to fetch target page by link
                    lTargetPage = pCacheMap.get(lTargetNSID).get(lLink);
                    if (lTargetPage == null) {
                        // Not found - attempt to fetch from database
                        lTargetPage = pMediaWiki.getPage(lTargetNSID, lLink);
                        if (lTargetPage != null) {
                            // Found it- check whether the timestamp is valid
                            if (lTargetPage.getRevisionAt(pTimestamp) != null) {
                                // Yes- the target page already existed at that point in time
                                pCacheMap.get(lTargetNSID).put(lLink, lTargetPage);
                            }
                        }
                    }

                    if (lTargetPage != null) {
                        if (lIsRedirect && (lLinkCount == 1)) {
                            // The SQL Dumps represent redirect links as redirect AND Article- so do we...
                            lResult.add(new WikiPageLink(MediaWikiConst.LinkType.REDIRECT, pSourcePage, lTargetPage, WikiPageLink.WikiPageLinkSource.HtmlParsedAdHoc, pTimestamp));
                            lResult.add(new WikiPageLink(MediaWikiConst.LinkType.ARTICLE, pSourcePage, lTargetPage, WikiPageLink.WikiPageLinkSource.HtmlParsedAdHoc, pTimestamp));
                        } else if (lTargetNS.getId() == 14) {
                            if ((lCatLinksDivIndex >= 0) && (lMatcher.start() > lCatLinksDivIndex)) {
                                lResult.add(new WikiPageLink(MediaWikiConst.LinkType.CATEGORIZATION, pSourcePage, lTargetPage, WikiPageLink.WikiPageLinkSource.HtmlParsedAdHoc, pTimestamp));
                            }
                            else {
                                lResult.add(new WikiPageLink(MediaWikiConst.LinkType.ARTICLE, pSourcePage, lTargetPage, WikiPageLink.WikiPageLinkSource.HtmlParsedAdHoc, pTimestamp));
                            }
                        } else {
                            lResult.add(new WikiPageLink(MediaWikiConst.LinkType.ARTICLE, pSourcePage, lTargetPage, WikiPageLink.WikiPageLinkSource.HtmlParsedAdHoc, pTimestamp));
                        }
                    }
                }
            }
        }
        return lResult;
    }

}
