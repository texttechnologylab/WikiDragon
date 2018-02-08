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

package org.hucompute.wikidragon.html;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Rüdiger Gleim
 */
public class HTML2TEIParser {

    public static String html2TEI(String pHtml, Map<String, String> pIdnoMap) throws WikiDragonException {
        return html2TEI(pHtml, pIdnoMap, true);
    }

    public static String html2TEI(String pHtml, Map<String, String> pIdnoMap, boolean pRemoveCategoryLinksSection) throws WikiDragonException {
        try {
            Document lDocument = Jsoup.parse(pHtml);
            Element lRemoveMe = null;
            for (Element lElement:lDocument.getElementsByAttributeValue("id", "catlinks")) {
                lRemoveMe = lElement;
            }
            if (lRemoveMe != null) {
                lRemoveMe.remove();
            }
            StringWriter lWriter = new StringWriter();
            XMLStreamWriter lXMLWriter = new IndentingXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(lWriter));
            List<XMLStreamException> lErrors = new ArrayList<>();
            lDocument.traverse(new NodeVisitor() {
                @Override
                public void head(Node node, int i) {
                    try {
                        if (node instanceof Element) {
                            Element lElement = (Element) node;
                            switch (lElement.nodeName()) {
                                case "h1":
                                case "h2":
                                case "h3":
                                case "h4":
                                case "h5":
                                case "h6": {
                                    lXMLWriter.writeStartElement("head");
                                    break;
                                }
                                case "html": {
                                    lXMLWriter.writeStartElement("TEI");
                                    break;
                                }
                                case "head": {
                                    lXMLWriter.writeStartElement("teiHeader");
                                    lXMLWriter.writeStartElement("fileDesc");
                                    lXMLWriter.writeStartElement("publicationStmt");
                                    if (pIdnoMap != null) {
                                        for (Map.Entry<String, String> lEntry:pIdnoMap.entrySet()) {
                                            lXMLWriter.writeStartElement("idno");
                                            lXMLWriter.writeAttribute("type", lEntry.getKey());
                                            lXMLWriter.writeCharacters(lEntry.getValue());
                                            lXMLWriter.writeEndElement();
                                        }
                                    }
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                    break;
                                }
                                case "title": {
                                    lXMLWriter.writeStartElement("titleStmt");
                                    lXMLWriter.writeStartElement("title");
                                    break;
                                }
                                case "body": {
                                    lXMLWriter.writeStartElement("text");
                                    break;
                                }
                                case "p": {
                                    lXMLWriter.writeStartElement("p");
                                    break;
                                }
                                case "div": {
                                    lXMLWriter.writeStartElement("div");
                                    break;
                                }
                                case "ol":
                                case "ul": {
                                    lXMLWriter.writeStartElement("list");
                                    break;
                                }
                                case "li": {
                                    lXMLWriter.writeStartElement("item");
                                    break;
                                }
                                case "br": {
                                    lXMLWriter.writeStartElement("lb");
                                    break;
                                }
                                case "a": {
                                    lXMLWriter.writeStartElement("link");
                                    String lHref = lElement.attr("href");
                                    if (lHref != null) {
                                        lXMLWriter.writeAttribute("target", lHref);
                                    }
                                    break;
                                }
                                case "b": {
                                    lXMLWriter.writeStartElement("hi");
                                    lXMLWriter.writeAttribute("rend", "bold");
                                    break;
                                }
                                case "i": {
                                    lXMLWriter.writeStartElement("hi");
                                    lXMLWriter.writeAttribute("rend", "italic");
                                    break;
                                }
                                case "u": {
                                    lXMLWriter.writeStartElement("hi");
                                    lXMLWriter.writeAttribute("rend", "underline");
                                    break;
                                }
                                case "s": {
                                    lXMLWriter.writeStartElement("del");
                                    break;
                                }
                                case "dl": {
                                    lXMLWriter.writeStartElement("list");
                                    break;
                                }
                                case "dt": {
                                    lXMLWriter.writeStartElement("item");
                                    break;
                                }
                                case "dd": {
                                    lXMLWriter.writeStartElement("item");
                                    break;
                                }
                                case "em": {
                                    lXMLWriter.writeStartElement("emph");
                                    break;
                                }
                                case "table": {
                                    lXMLWriter.writeStartElement("table");
                                    break;
                                }
                                case "tr": {
                                    lXMLWriter.writeStartElement("row");
                                    break;
                                }
                                case "th": {
                                    lXMLWriter.writeStartElement("cell");
                                    break;
                                }
                                case "td": {
                                    lXMLWriter.writeStartElement("cell");
                                    break;
                                }
                            }
                        }
                        else if (node instanceof TextNode) {
                            TextNode lTextNode = (TextNode)node;
                            if (lTextNode.parent().nodeName().equals("body") && (lTextNode.text().trim().length() > 0)) {
                                lXMLWriter.writeStartElement("p");
                                lXMLWriter.writeCharacters(lTextNode.text());
                                lXMLWriter.writeEndElement();
                            }
                            else {
                                lXMLWriter.writeCharacters(lTextNode.text());
                            }
                        }
                    }
                    catch (XMLStreamException e) {
                        lErrors.add(e);
                    }
                }

                @Override
                public void tail(Node node, int i) {
                    try {
                        if (node instanceof Element) {
                            Element lElement = (Element) node;
                            switch (lElement.nodeName()) {
                                case "h1":
                                case "h2":
                                case "h3":
                                case "h4":
                                case "h5":
                                case "h6":
                                case "html":
                                case "head":
                                case "p":
                                case "div":
                                case "ol":
                                case "ul":
                                case "a":
                                case "b":
                                case "i":
                                case "u":
                                case "s":
                                case "li":
                                case "table":
                                case "tr":
                                case "th":
                                case "td":
                                case "em":
                                case "dl":
                                case "dt":
                                case "dd":
                                case "br":
                                case "body": {
                                    lXMLWriter.writeEndElement();
                                    break;
                                }
                                case "title": {
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                }
                            }
                        }
                    }
                    catch (XMLStreamException e) {
                        lErrors.add(e);
                    }
                }
            });
            if (lErrors.size() > 0) throw lErrors.get(0);
            lXMLWriter.flush();
            lWriter.flush();
            return lWriter.toString();
        }
        catch (XMLStreamException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

    public static String html2TEIFlat(String pHtml, Map<String, String> pIdnoMap) throws WikiDragonException {
        try {
            Document lDocument = Jsoup.parse(pHtml);
            StringWriter lWriter = new StringWriter();
            XMLStreamWriter lXMLWriter = new IndentingXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(lWriter));
            List<XMLStreamException> lErrors = new ArrayList<>();
            lDocument.traverse(new NodeVisitor() {
                @Override
                public void head(Node node, int i) {
                    try {
                        if (node instanceof Element) {
                            Element lElement = (Element) node;
                            switch (lElement.nodeName()) {
                                case "html": {
                                    lXMLWriter.writeStartElement("TEI");
                                    break;
                                }
                                case "head": {
                                    lXMLWriter.writeStartElement("teiHeader");
                                    lXMLWriter.writeStartElement("fileDesc");
                                    lXMLWriter.writeStartElement("publicationStmt");
                                    if (pIdnoMap != null) {
                                        for (Map.Entry<String, String> lEntry:pIdnoMap.entrySet()) {
                                            lXMLWriter.writeStartElement("idno");
                                            lXMLWriter.writeAttribute("type", lEntry.getKey());
                                            lXMLWriter.writeCharacters(lEntry.getValue());
                                            lXMLWriter.writeEndElement();
                                        }
                                    }
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                    break;
                                }
                                case "title": {
                                    lXMLWriter.writeStartElement("titleStmt");
                                    lXMLWriter.writeStartElement("title");
                                    break;
                                }
                                case "body": {
                                    lXMLWriter.writeStartElement("text");
                                    lXMLWriter.writeStartElement("body");
                                    lXMLWriter.writeStartElement("div");
                                    lXMLWriter.writeStartElement("p");
                                    break;
                                }
                            }
                        }
                        else if (node instanceof TextNode) {
                            TextNode lTextNode = (TextNode)node;
                            lXMLWriter.writeCharacters(lTextNode.text());
                        }
                    }
                    catch (XMLStreamException e) {
                        lErrors.add(e);
                    }
                }

                @Override
                public void tail(Node node, int i) {
                    try {
                        if (node instanceof Element) {
                            Element lElement = (Element) node;
                            switch (lElement.nodeName()) {
                                case "html":
                                case "head": {
                                    lXMLWriter.writeEndElement();
                                    break;
                                }
                                case "body": {
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                    break;
                                }
                                case "title": {
                                    lXMLWriter.writeEndElement();
                                    lXMLWriter.writeEndElement();
                                }
                            }
                        }
                    }
                    catch (XMLStreamException e) {
                        lErrors.add(e);
                    }
                }
            });
            if (lErrors.size() > 0) throw lErrors.get(0);
            lXMLWriter.flush();
            lWriter.flush();
            return lWriter.toString();
        }
        catch (XMLStreamException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

}
