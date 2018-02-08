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

package org.hucompute.wikidragon.core.parsing;

import gplx.xowa.addons.parsers.mediawikis.Xop_mediawiki_loader;
import gplx.xowa.addons.parsers.mediawikis.Xop_mediawiki_mgr;
import gplx.xowa.addons.parsers.mediawikis.Xop_mediawiki_wkr;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bridges WikiDragon with XOWA
 * @author Rüdiger Gleim
 */
public class XOWAParser implements Xop_mediawiki_loader {

    private static Logger logger = LogManager.getLogger(XOWAParser.class);

    protected Pattern galleryPattern = Pattern.compile("<gallery([ ].*?)?>(.*?)</gallery>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    protected Pattern attributesPattern = Pattern.compile("(\\w+)[ ]*=[ ]*\"(.*?)\"");

    protected static final int FREE_WORKER_MEMORY_INTERVAL = 1; // 50

    protected static Xop_mediawiki_mgr xowaMediaWikiManager;

    protected WikiDragonDatabase wikiDragonDatabase;
    protected MediaWiki mediaWiki;
    protected Xop_mediawiki_wkr worker;
    protected ZonedDateTime currentTimestamp;
    protected Map<String, Namespace> namespaceMap;
    protected Map<String, String> cacheMap;
    protected int counter;
    protected String siteName;
    protected String fileNamespaceName;
    protected Set<String> fileNamespaceNames;

    protected static boolean useOldestRevisionWhenNoPreviousVersionExists = true;

    static  {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            xowaMediaWikiManager = new Xop_mediawiki_mgr(new File("xowa_win").getAbsolutePath()+File.separatorChar, true);
        }
        else {
            xowaMediaWikiManager = new Xop_mediawiki_mgr(new File("xowa_linux").getAbsolutePath()+File.separatorChar, true);
        }
    }

    public XOWAParser(MediaWiki pMediaWiki) throws WikiDragonException {
        this(pMediaWiki, new HashMap<>());
    }

    public XOWAParser(MediaWiki pMediaWiki, Map<String, String> pCacheMap) throws WikiDragonException {
        counter = 0;
        cacheMap = pCacheMap == null ? new HashMap<>() : pCacheMap;
        mediaWiki = pMediaWiki;
        namespaceMap = mediaWiki.getNamespaceMap();
        fileNamespaceName = mediaWiki.getNamespace(6).getName();
        fileNamespaceNames = mediaWiki.getNamespace(6).getAllNames();
        wikiDragonDatabase = mediaWiki.getWikiDragonDatabase();
        String lBase = mediaWiki.getBase();
        lBase = lBase.substring(lBase.indexOf("//")+2);
        siteName = lBase.substring(0, lBase.indexOf("/"));
        //siteName = "de.wikipedia.org"; // TODO: Remove me
        synchronized (xowaMediaWikiManager) {
            worker = xowaMediaWikiManager.Make(siteName, this);
        }
    }

    public synchronized String parse(Page pPage, ZonedDateTime pZonedDateTime) throws WikiDragonException {
        Revision lRevision = pPage.getRevisionAt(pZonedDateTime);
        return lRevision == null ? null : parse(pPage.getTitle(), lRevision.getRawText(), pZonedDateTime);
    }

    public synchronized String parse(Page pPage, Revision pRevision) throws WikiDragonException {
        return parse(pPage.getTitle(), pRevision.getRawText(), pRevision.getTimestamp());
    }

    public synchronized String parse(String pPageTitle, String pText, ZonedDateTime pTimestamp) throws WikiDragonException {
        currentTimestamp = pTimestamp;
        try {
            String lResult = worker.Parse(pPageTitle, pText);
            lResult = "<h1>"+StringUtil.encodeXml(pPageTitle)+"</h1>\n"+lResult;
            if (++counter >= FREE_WORKER_MEMORY_INTERVAL) {
                counter = 0;
                worker.Free_memory();
            }
            return lResult;
        }
        catch (Exception e) {
            throw new WikiDragonException("XOWA Parsing Exception", e);
        }
    }

    private String getMediaWiki(String pTitle) {
        SAXBuilder lBuilder = new SAXBuilder();
        String lResult = "";
        try {
            Document lDocument = lBuilder.build("https://"+siteName+"/w/api.php?action=query&prop=revisions&rvlimit=1&rvprop=content&format=xml&titles="+ URLEncoder.encode(pTitle, "UTF-8"));
            for (Element lRev : lDocument.getDescendants(new ElementFilter("rev"))) {
                lResult = lRev.getText();
            }
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return lResult;
    }

    public void clearCache() {
        synchronized (cacheMap) {
            cacheMap.clear();
        }
    }

    @Deprecated
    private String getRedirectString(String pString) {
        String lLowerCase = pString.toLowerCase();
        if (lLowerCase.startsWith("#redirect[[") || lLowerCase.startsWith("#redirect [[")) {
            String lResult = null;
            try {
                lResult = pString.substring(pString.indexOf("[[") + 2, pString.indexOf("]]"));
            }
            catch (Exception e) {
            }
            if (lResult != null) {
                lResult = lResult.trim();
                if (lResult.length() > 0) {
                    return lResult;
                }
            }
        }
        return null;
    }

    @Override
    public String LoadWikitext(String s) {
        int lIndex = s.indexOf(":");
        Namespace lNamespace = namespaceMap.get("");
        String lTitle = s;
        if (lIndex > 0) {
            String lNamespaceString = s.substring(0, s.indexOf(":"));
            if (namespaceMap.containsKey(lNamespaceString)) {
                lNamespace = namespaceMap.get(lNamespaceString);
                if (lIndex <= s.length() - 1) {
                    lTitle = lTitle.substring(lIndex + 1);
                } else {
                    lTitle = "";
                }
            }
        }
        lTitle = lTitle.replace("_", " ");
        String lKey = StringUtil.zonedDateTime2String(currentTimestamp)+"\t"+lNamespace.getId()+"\t"+lTitle;
        String lResult = null;
        synchronized (cacheMap) {
            lResult = cacheMap.get(lKey);
        }
        if (lResult == null) {
            Page lPage = lNamespace.getPage(lTitle);
            if (lPage != null) {
                Revision lRevision = lPage.getRevisionAt(currentTimestamp);
                if ((lRevision == null) && useOldestRevisionWhenNoPreviousVersionExists) {
                    lRevision = lPage.getFirstRevision();
                }
                if (lRevision != null) {
                    try {
                        lResult = lRevision.getRawText();
                        if (lResult != null) {
                            synchronized (cacheMap) {
                                cacheMap.put(lKey, lResult);
                            }
                        }
                    } catch (WikiDragonException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
        return lResult == null ? "" : lResult;
    }

}
