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

package org.hucompute.wikidragon.core.nlp;

import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class XMI2TEIConverter extends DefaultHandler2 implements Runnable {

    public static final String ATTR_GENDER = "gender";
    public static final String ATTR_CASE = "case";
    public static final String ATTR_MOOD = "mood";
    public static final String ATTR_NUMBER = "number";
    public static final String ATTR_PERSON = "person";
    public static final String ATTR_TENSE = "tense";
    public static final String ATTR_VOICE = "voice";
    public static final String ATTR_DEGREE = "degree";
    public static final String ATTR_ANIMACY = "animacy";
    public static final String ATTR_ASPECT = "aspect";
    public static final String ATTR_DEFINITNESS = "definitness";
    public static final String ATTR_NEGATIVE = "negative";
    public static final String ATTR_NUMTYPE = "numtype";
    public static final String ATTR_POSSESSIVE = "possessive";
    public static final String ATTR_PRONOUNTYPE = "pronountype";
    public static final String ATTR_REFLEX = "reflex";
    public static final String ATTR_VERBFORM = "verbform";

    protected static String[] ldsPriority = new String[]{"div", "head", "p", "s"};
    protected static TObjectIntHashMap<String> ldsPriorityMap;

    protected StringBuilder characters;
    protected String sofa;
    protected List<Token> tokenList;
    protected List<LDSElement> ldsList;
    protected TLongObjectHashMap<String> lemmaMap;
    protected TLongObjectHashMap<String> posMap;
    protected TLongObjectHashMap<String> morphMap;
    protected PrintWriter writer;
    protected Reader reader;
    protected File sourceFile;
    protected Exception exception;

    static {
        ldsPriorityMap = new TObjectIntHashMap<>();
        for (int i=0; i<ldsPriority.length; i++) {
            ldsPriorityMap.put(ldsPriority[i], i);
        }
    }

    public XMI2TEIConverter(Reader pReader, Writer pWriter) throws SAXException, ParserConfigurationException, IOException {
        reader = pReader;
        if (!(pWriter instanceof PrintWriter)) {
            writer = new PrintWriter(pWriter);
        }
        else {
            writer = (PrintWriter)pWriter;
        }
    }

    public XMI2TEIConverter(File pSource, File pTarget) throws SAXException, ParserConfigurationException, IOException {
        writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(pTarget), Charset.forName("UTF-8")));
        sourceFile = pSource;
    }

    public Exception getException() {
        return exception;
    }

    public void run() {
        try {
            if (sourceFile != null) {
                SAXParser lParser = SAXParserFactory.newInstance().newSAXParser();
                lParser.getXMLReader().setContentHandler(this);
                lParser.getXMLReader().setErrorHandler(this);
                lParser.getXMLReader().parse(new InputSource(new FileInputStream(sourceFile)));
            } else {
                SAXParser lParser = SAXParserFactory.newInstance().newSAXParser();
                lParser.getXMLReader().setContentHandler(this);
                lParser.getXMLReader().setErrorHandler(this);
                lParser.getXMLReader().parse(new InputSource(reader));
            }
            writer.flush();
            writer.close();
        }
        catch (Exception e) {
            exception = e;
        }
    }

    public static boolean isAlphaNumeric(String pString) {
        for (int i=0; i<pString.length(); i++) {
            if (Character.isLetterOrDigit(pString.codePointAt(i))) return true;
        }
        return false;
    }

    public static String escapeXML(String pString) {
        StringBuilder lResult = new StringBuilder();
        for (char c:pString.toCharArray()) {
            switch (c) {
                case '\t': {
                    lResult.append(' ');
                    break;
                }
                case '\r': {
                    lResult.append(' ');
                    break;
                }
                case '\n': {
                    lResult.append(' ');
                    break;
                }
                case '&': {
                    lResult.append("&amp;");
                    break;
                }
                case '<': {
                    lResult.append("&lt;");
                    break;
                }
                case '>': {
                    lResult.append("&gt;");
                    break;
                }
                case '"': {
                    lResult.append("&quot;");
                    break;
                }
                default: {
                    lResult.append(c);
                }
            }
        }
        return lResult.toString();
    }

    @Override
    public void startDocument() throws SAXException {
        tokenList = new ArrayList<>();
        ldsList = new ArrayList<>();
        lemmaMap = new TLongObjectHashMap<>();
        posMap = new TLongObjectHashMap<>();
        morphMap = new TLongObjectHashMap<>();
        ldsPriorityMap = new TObjectIntHashMap<>();
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            List<Comparable> lElements = new ArrayList<>(tokenList);
            lElements.addAll(ldsList);
            Collections.sort(lElements);
            writer.println("<?xml version=\"1.0\" ?>");
            writer.println("<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">");
            writer.println("<teiHeader/>");
            writer.println("<text>");
            writer.println("<body>");
            List<LDSElement> lElementStack = new ArrayList<>();
            for (Comparable lElement:lElements) {
                if (lElement instanceof LDSElement) {
                    LDSElement lLDSElement = (LDSElement)lElement;
                    while ((lElementStack.size() > 0) && (!lElementStack.get(lElementStack.size()-1).contains(lLDSElement))) {
                        writer.println("</"+lElementStack.get(lElementStack.size()-1).label+">");
                        lElementStack.remove(lElementStack.size()-1);
                    }
                    writer.println("<"+lLDSElement.label+">");
                    lElementStack.add(lLDSElement);
                }
                else {
                    Token lToken = (Token)lElement;
                    String lTokenString = sofa.substring(lToken.start, lToken.end);
                    boolean lAlphaNumeric = isAlphaNumeric(lTokenString);
                    TreeMap<String, String> attributeMap = new TreeMap<>();
                    if ((lToken.lemmaID != -1) && lAlphaNumeric) {
                        attributeMap.put("lemma", lemmaMap.get(lToken.lemmaID));
                    }
                    if (lToken.posID != -1) {
                        attributeMap.put("type", posMap.get(lToken.posID));
                    }
                    if (lToken.morphID != -1) {
                        String[] lFields = morphMap.get(lToken.morphID).split("\\|");
                        for (String lField : lFields) {
                            if (lField.contains("=")) {
                                String[] lSub = lField.split("=", -1);
                                switch (lSub[0]) {
                                    case "gender": {
                                        attributeMap.put(ATTR_GENDER, lSub[1]);
                                        break;
                                    }
                                    case "case": {
                                        attributeMap.put(ATTR_CASE, lSub[1]);
                                        break;
                                    }
                                    case "mood": {
                                        attributeMap.put(ATTR_MOOD, lSub[1]);
                                        break;
                                    }
                                    case "number": {
                                        attributeMap.put(ATTR_NUMBER, lSub[1]);
                                        break;
                                    }
                                    case "person": {
                                        attributeMap.put(ATTR_PERSON, lSub[1]);
                                        break;
                                    }
                                    case "tense": {
                                        attributeMap.put(ATTR_TENSE, lSub[1]);
                                        break;
                                    }
                                    case "voice": {
                                        attributeMap.put(ATTR_VOICE, lSub[1]);
                                        break;
                                    }
                                    case "degree": {
                                        attributeMap.put(ATTR_DEGREE, lSub[1]);
                                        break;
                                    }
                                    case "animacy": {
                                        attributeMap.put(ATTR_ANIMACY, lSub[1]);
                                        break;
                                    }
                                    case "aspect": {
                                        attributeMap.put(ATTR_ASPECT, lSub[1]);
                                        break;
                                    }
                                    case "definitness": {
                                        attributeMap.put(ATTR_DEFINITNESS, lSub[1]);
                                        break;
                                    }
                                    case "negative": {
                                        attributeMap.put(ATTR_NEGATIVE, lSub[1]);
                                        break;
                                    }
                                    case "numtype": {
                                        attributeMap.put(ATTR_NUMTYPE, lSub[1]);
                                        break;
                                    }
                                    case "possessive": {
                                        attributeMap.put(ATTR_POSSESSIVE, lSub[1]);
                                        break;
                                    }
                                    case "pronountype": {
                                        attributeMap.put(ATTR_PRONOUNTYPE, lSub[1]);
                                        break;
                                    }
                                    case "reflex": {
                                        attributeMap.put(ATTR_REFLEX, lSub[1]);
                                        break;
                                    }
                                    case "verbform": {
                                        attributeMap.put(ATTR_VERBFORM, lSub[1]);
                                        break;
                                    }
                                    default: {
                                        throw new SAXException(new Exception("Unknown Morph-Key: " + lSub[0]));
                                    }
                                }
                            }
                        }
                    }
                    while ((lElementStack.size() > 0) && (!lElementStack.get(lElementStack.size()-1).contains(lToken))) {
                        writer.println("</"+lElementStack.get(lElementStack.size()-1).label+">");
                        lElementStack.remove(lElementStack.size()-1);
                    }
                    if (lAlphaNumeric) {
                        writer.print("<w");
                    }
                    else {
                        writer.print("<c");
                    }

                    for (Map.Entry<String, String > entry:attributeMap.entrySet()) {
                        writer.print(" "+entry.getKey()+"=\""+escapeXML(entry.getValue())+"\"");
                    }

                    writer.print(">"+escapeXML(lTokenString));
                    if (lAlphaNumeric) {
                        writer.println("</w>");
                    }
                    else {
                        writer.println("</c>");
                    }
                }
            }
            while (lElementStack.size() > 0) {
                writer.println("</"+lElementStack.get(lElementStack.size()-1).label+">");
                lElementStack.remove(lElementStack.size()-1);
            }
            writer.println("</body>");
            writer.println("</text>");
            writer.println("</TEI>");
            writer.flush();
        }
        catch (Exception e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.startsWith("pos:")) {
            String lPOS = attributes.getValue("PosValue");
            if (lPOS.startsWith("pos=")) lPOS = lPOS.substring(4);
            posMap.put(Long.parseLong(attributes.getValue("xmi:id")), lPOS);
        }
        else {
            if (qName.contains(":")) qName = qName.substring(qName.indexOf(":") + 1);
            characters = new StringBuilder();
            switch (qName) {
                case "Sofa": {
                    sofa = attributes.getValue("sofaString");
                    break;
                }
                case "Token": {
                    String lLemmaIDString = attributes.getValue("lemma");
                    String lPOSIDString = attributes.getValue("pos");
                    String lMorphIDString = attributes.getValue("morph");
                    tokenList.add(new Token(Integer.parseInt(attributes.getValue("begin")), Integer.parseInt(attributes.getValue("end")), lLemmaIDString == null ? -1 : Long.parseLong(lLemmaIDString), lPOSIDString == null ? -1 : Long.parseLong(lPOSIDString), lMorphIDString == null ? -1 : Long.parseLong(lMorphIDString)));
                    break;
                }
                case "Lemma": {
                    lemmaMap.put(Long.parseLong(attributes.getValue("xmi:id")), attributes.getValue("value"));
                    break;
                }
                case "MorphologicalFeatures": {
                    morphMap.put(Long.parseLong(attributes.getValue("xmi:id")), attributes.getValue("value"));
                    break;
                }
                case "Sentence": {
                    ldsList.add(new LDSElement("s", Integer.parseInt(attributes.getValue("begin")), Integer.parseInt(attributes.getValue("end"))));
                    break;
                }
                case "Paragraph": {
                    ldsList.add(new LDSElement("p", Integer.parseInt(attributes.getValue("begin")), Integer.parseInt(attributes.getValue("end"))));
                    break;
                }
                case "Div": {
                    ldsList.add(new LDSElement("div", Integer.parseInt(attributes.getValue("begin")), Integer.parseInt(attributes.getValue("end"))));
                    break;
                }
                case "Head": {
                    ldsList.add(new LDSElement("head", Integer.parseInt(attributes.getValue("begin")), Integer.parseInt(attributes.getValue("end"))));
                    break;
                }
                case "XMI": {
                    break;
                }
                case "NULL": {
                    break;
                }
                case "DocumentMetaData": {
                    break;
                }
                case "View": {
                    break;
                }
                case "TagsetDescription": {
                    break;
                }
                case "TagDescription": {
                    break;
                }
                case "WikipediaInformation": {
                    break;
                }
                case "categories": {
                    break;
                }
                default: {
                    throw new SAXException("Unhandled Element: " + qName);
                }
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        characters = new StringBuilder();
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        characters.append(ch, start, length);
    }

    protected class Token implements Comparable {
        protected int start;
        protected int end;
        protected long lemmaID;
        protected long posID;
        protected long morphID;

        public Token(int start, int end, long lemmaID, long posID, long morphID) {
            this.start = start;
            this.end = end;
            this.lemmaID = lemmaID;
            this.posID = posID;
            this.morphID = morphID;
        }

        @Override
        public int compareTo(@NotNull Object o) {
            if (o instanceof Token) {
                Token lOther = (Token)o;
                if (start != lOther.start) {
                    return Integer.compare(start, lOther.start);
                }
                else {
                    return Integer.compare(lOther.end, end);
                }
            }
            else if (o instanceof LDSElement) {
                LDSElement lOther = (LDSElement)o;
                if (start != lOther.start) {
                    return Integer.compare(start, lOther.start);
                }
                else {
                    return 1; // LDSElement before Token
                }
            }
            return 0;
        }
    }

    protected class LDSElement implements Comparable {
        protected String label;
        protected int start;
        protected int end;

        public LDSElement(String label, int start, int end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }

        public boolean contains(Object o) {
            if (o instanceof Token) {
                Token lToken = (Token)o;
                return ((start <= lToken.start) && (end >= lToken.end));
            }
            else if (o instanceof LDSElement) {
                LDSElement lLDSElement = (LDSElement)o;
                return ((start <= lLDSElement.start) && (end >= lLDSElement.end));
            }
            else {
                return false;
            }
        }

        @Override
        public int compareTo(@NotNull Object o) {
            if (o instanceof Token) {
                Token lOther = (Token)o;
                if (start != lOther.start) {
                    return Integer.compare(start, lOther.start);
                }
                else {
                    return -1; // LDSElement before Token
                }
            }
            else if (o instanceof LDSElement) {
                LDSElement lOther = (LDSElement)o;
                if (start != lOther.start) {
                    return Integer.compare(start, lOther.start);
                }
                else {
                    if (end != lOther.end) {
                        return Integer.compare(lOther.end, end);
                    }
                    else {
                        return Integer.compare(ldsPriorityMap.get(label), ldsPriorityMap.get(lOther.label));
                    }
                }
            }
            return 0;
        }
    }

}

