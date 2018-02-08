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

package org.hucompute.wikidragon.core.dump;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.events.ImportListener;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.*;

import static org.hucompute.wikidragon.core.model.MediaWikiConst.Model.*;

/**
 * @author Rüdiger Gleim
 */
public class MediaWikiDumpParser extends DefaultHandler2 {

    private static Logger logger = LogManager.getLogger(MediaWikiDumpParser.class);

    private StringBuilder characters;
    private Set<ImportListener> importListeners;
    private InputStream inputStream;
    private String[] elementStack;
    private int elementStackPos;

    private String siteInfoSiteName;
    private String siteInfoDbName;
    private String siteInfoBase;
    private String siteInfoApiUrl;
    private String siteInfoGenerator;
    private MediaWikiConst.Case siteInfoCase;

    private String pageTitle;
    private int pageNSID;
    private long pageID;
    private boolean pageReported;

    private long revisionID;
    private long revisionParentID;
    private ZonedDateTime revisionTimestamp;
    private String revisionComment;
    private int revisionBytes;
    private MediaWikiConst.Model revisionModel;
    private MediaWikiConst.Format revisionFormat;
    private String revisionText;
    private boolean revisionMinor;
    private String revisionSha1;
    private String revisionIp;
    private String revisionUserName;
    private long revisionUserId;
    private Map<String, String> documentRootProperties;


    public MediaWikiDumpParser()  {
        importListeners = new HashSet<>();
    }

    public void addImportListener(ImportListener pImportListener) {
        importListeners.add(pImportListener);
    }

    public void removeImportListener(ImportListener pImportListener) {
        importListeners.remove(pImportListener);
    }

    public void parse(InputStream pInputStream) throws ParserConfigurationException, SAXException, IOException {
        parse(pInputStream, null);
    }

    public void parse(InputStream pInputStream, Charset pCharset) throws ParserConfigurationException, SAXException, IOException {
        parse(new InputStreamReader(pInputStream, pCharset));
    }

    public void parse(Reader pReader) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory lSAXParserFactory  = SAXParserFactory.newInstance();
        lSAXParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        SAXParser lParser = lSAXParserFactory.newSAXParser();
        XMLReader lXmlReader = lParser.getXMLReader();
        lXmlReader.setContentHandler(this);
        lXmlReader.setErrorHandler(this);
        lXmlReader.parse(new InputSource(pReader));
    }

    @Override
    public void startDocument() throws SAXException {
        elementStackPos = -1;
        elementStack = new String[32];
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            elementStack[++elementStackPos] = qName;
            characters = new StringBuilder();
            switch (qName) {
                case "mediawiki": {
                    documentRootProperties = new HashMap<>();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        documentRootProperties.put(attributes.getQName(i), attributes.getValue(i));
                    }
                    break;
                }
                case "siteinfo": {
                    siteInfoSiteName = null;
                    siteInfoDbName = null;
                    siteInfoBase = null;
                    siteInfoApiUrl = null;
                    siteInfoGenerator = null;
                    siteInfoCase = null;
                    break;
                }
                case "page": {
                    pageTitle = null;
                    pageNSID = -1;
                    pageID = -1;
                    pageReported = false;
                    break;
                }
                case "revision": {
                    if (!pageReported) {
                        for (ImportListener l : importListeners) {
                            l.page(pageTitle, pageNSID, pageID);
                        }
                        pageReported = true;
                    }
                    revisionID = WikiDragonConst.NULLNODEID;
                    revisionParentID = WikiDragonConst.NULLNODEID;
                    revisionTimestamp = null;
                    revisionComment = null;
                    revisionModel = null;
                    revisionFormat = null;
                    revisionText = "";
                    revisionMinor = false;
                    revisionSha1 = null;
                    revisionIp = null;
                    revisionUserName = null;
                    revisionUserId = WikiDragonConst.NULLNODEID;
                    revisionBytes = 0;
                    break;
                }
                case "text": {
                    String lBytesString = attributes.getValue("bytes");
                    if (lBytesString != null) {
                        revisionBytes = Integer.parseInt(lBytesString);
                    }
                    else {
                        revisionBytes = 0;
                    }
                    break;
                }
            }
        }
        catch (WikiDragonException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            elementStackPos--;
            switch (qName) {
                case "sitename": {
                    siteInfoSiteName = characters.toString();
                    break;
                }
                case "dbname": {
                    siteInfoDbName = characters.toString();
                    break;
                }
                case "text": {
                    revisionText = characters.toString();
                    if (revisionBytes == 0) {
                        try {
                            revisionBytes = revisionText.getBytes("UTF-8").length;
                        }
                        catch (UnsupportedEncodingException e) {
                            throw new SAXException(e);
                        }
                    }
                    break;
                }
                case "base": {
                    siteInfoBase = characters.toString();
                    siteInfoApiUrl = null;
                    if (siteInfoBase.contains("/wiki/")) {
                        siteInfoApiUrl = siteInfoBase.substring(0, siteInfoBase.indexOf("/wiki/")) + "/w/api.php";
                    } else {
                        throw new SAXException("Could not extract ApiUrl from base: " + siteInfoBase);
                    }
                    break;
                }
                case "generator": {
                    siteInfoGenerator = characters.toString();
                    break;
                }
                case "case": {
                    siteInfoCase = MediaWikiConst.getCase(characters.toString());
                    break;
                }
                case "siteinfo": {
                    for (ImportListener l : importListeners) {
                        l.mediaWiki(documentRootProperties, siteInfoSiteName, siteInfoDbName, siteInfoBase, siteInfoGenerator, siteInfoCase, siteInfoApiUrl);
                    }
                    try {
                        getNamespacesOnline();
                    } catch (Exception e) {
                        throw new SAXException(e);
                    }
                    break;
                }
                case "page": {
                    if (!pageReported) {
                        for (ImportListener l : importListeners) {
                            l.page(pageTitle, pageNSID, pageID);
                        }
                    }
                    break;
                }
                case "revision": {
                    for (ImportListener l : importListeners) {
                        if (revisionUserId != -1) {
                            l.revision(revisionID, revisionParentID, revisionTimestamp, revisionUserName, revisionUserId, revisionComment, revisionMinor, revisionModel, revisionFormat, revisionSha1, revisionText, revisionBytes);
                        } else {
                            l.revision(revisionID, revisionParentID, revisionTimestamp, revisionIp, revisionComment, revisionMinor, revisionModel, revisionFormat, revisionSha1, revisionText, revisionBytes);
                        }
                    }
                    break;
                }
                case "minor": {
                    revisionMinor = true;
                    break;
                }
                case "ns": {
                    pageNSID = Integer.parseInt(characters.toString());
                    break;
                }
                case "title": {
                    pageTitle = characters.toString();
                    break;
                }
                case "sha1": {
                    revisionSha1 = characters.toString();
                    break;
                }
                case "parentid": {
                    revisionParentID = Long.parseLong(characters.toString());
                    break;
                }
                case "id": {
                    switch (elementStack[elementStackPos]) {
                        case "page": {
                            pageID = Long.parseLong(characters.toString());
                            break;
                        }
                        case "revision": {
                            revisionID = Long.parseLong(characters.toString());
                            break;
                        }
                        case "contributor": {
                            revisionUserId = Long.parseLong(characters.toString());
                            break;
                        }
                    }
                    break;
                }
                case "timestamp": {
                    revisionTimestamp = ZonedDateTime.parse(characters.toString());
                    break;
                }
                case "username": {
                    revisionUserName = characters.toString();
                    break;
                }
                case "ip": {
                    revisionIp = characters.toString();
                    break;
                }
                case "comment": {
                    revisionComment = characters.toString();
                    break;
                }
                case "format": {
                    revisionFormat = MediaWikiConst.getFormat(characters.toString());
                    break;
                }
                case "model": {
                    revisionModel = MediaWikiConst.getModel(characters.toString());
                    break;
                }
            }
            characters = new StringBuilder();
        }
        catch (WikiDragonException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        characters.append(ch, start, length);
    }

    protected void getNamespacesOnline() throws IOException, JDOMException {
        try {
            Document lDocument = IOUtil.getDocument(siteInfoApiUrl + "?action=query&meta=siteinfo&siprop=general|namespaces|namespacealiases&format=xml");
            {
                Iterator<Element> i = lDocument.getDescendants(new ElementFilter("namespacealiases"));
                Map<Integer, Set<String>> lAliasMap = new HashMap<Integer, Set<String>>();
                if (i.hasNext()) {
                    Element lNamespacesElement = i.next();
                    i = lNamespacesElement.getDescendants(new ElementFilter("ns"));
                    while (i.hasNext()) {
                        Element lNS = i.next();
                        int lNamespaceID = Integer.parseInt(lNS.getAttributeValue("id"));
                        String lNamespaceAliasName = lNS.getText();
                        if (!lAliasMap.containsKey(lNamespaceID)) {
                            lAliasMap.put(lNamespaceID, new HashSet<String>());
                        }
                        lAliasMap.get(lNamespaceID).add(lNamespaceAliasName);
                    }
                }
                i = lDocument.getDescendants(new ElementFilter("namespaces"));
                if (i.hasNext()) {
                    Element lNamespacesElement = i.next();
                    i = lNamespacesElement.getDescendants(new ElementFilter("ns"));
                    while (i.hasNext()) {
                        Element lNS = i.next();
                        int lNamespaceID = Integer.parseInt(lNS.getAttributeValue("id"));
                        MediaWikiConst.Case lNamespaceCase = null;
                        switch (lNS.getAttributeValue("case")) {
                            case "first-letter": {
                                lNamespaceCase = MediaWikiConst.Case.FIRST_LETTER;
                                break;
                            }
                            case "case-sensitive": {
                                lNamespaceCase = MediaWikiConst.Case.CASE_SENSITIVE;
                                break;
                            }
                            default: {
                                throw new IOException("Unexpected Case: " + characters.toString());
                            }
                        }
                        String lNamespaceName = lNS.getText();
                        String lNamespaceCanonical = lNS.getAttributeValue("canonical");
                        String lNamespaceSubpages = lNS.getAttributeValue("subpages");
                        MediaWikiConst.Model lNamespaceModel = WIKITEXT;
                        if (lNS.getAttributeValue("defaultcontentmodel") != null) {
                            switch (lNS.getAttributeValue("defaultcontentmodel")) {
                                case "GadgetDefinition": {
                                    lNamespaceModel = GADGET_DEFINITION;
                                    break;
                                }
                                case "flow-board": {
                                    lNamespaceModel = FLOW_BOARD;
                                    break;
                                }
                                case "wikitext": {
                                    lNamespaceModel = WIKITEXT;
                                    break;
                                }
                                case "css": {
                                    lNamespaceModel = CSS;
                                    break;
                                }
                                case "javascript": {
                                    revisionModel = JAVASCRIPT;
                                    break;
                                }
                                case "json": {
                                    revisionModel = JSON;
                                    break;
                                }
                                case "Scribunto": {
                                    revisionModel = SCRIBUNTO;
                                    break;
                                }
                                default: {
                                    throw new IOException("Unexpected DefaultContentModel: " + lNS.getAttributeValue("defaultcontentmodel"));
                                }
                            }
                        }
                        for (ImportListener l : importListeners) {
                            l.namespace(lNamespaceID, lNamespaceCase, lNamespaceName, lNamespaceCanonical, lAliasMap.containsKey(lNamespaceID) ? lAliasMap.get(lNamespaceID) : new HashSet<String>(), lNamespaceSubpages != null, lNamespaceModel);
                        }
                    }
                }
            }
        }
        catch (WikiDragonException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
