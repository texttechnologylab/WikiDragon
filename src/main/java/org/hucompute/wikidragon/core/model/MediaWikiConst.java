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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;

import static org.hucompute.wikidragon.core.model.MediaWikiConst.Model.*;

/**
 * @author Rüdiger Gleim
 */
public class MediaWikiConst {

    private static Logger logger = LogManager.getLogger(MediaWikiConst.class);

    public static enum Case {FIRST_LETTER, CASE_SENSITIVE};

    public static enum Model {WIKITEXT,FLOW_BOARD,GADGET_DEFINITION, CSS, JAVASCRIPT, SCRIBUNTO, JSON, MASS_MESSAGE_LIST_CONTENT, UNKNOWN};

    public static enum Format {TEXT_XWIKI, TEXT_CSS, TEXT_JAVASCRIPT, TEXT_PLAIN, TEXT_JSON, APPLICATION_JSON};

    public static enum Space {PRESERVE};

    public static enum LinkType {ARTICLE,REDIRECT,CATEGORIZATION};

    public static Case getCase(String pString) throws WikiDragonException{
        if (pString == null) return null;
        switch (pString) {
            case "first-letter": {
                return MediaWikiConst.Case.FIRST_LETTER;
            }
            case "case-sensitive": {
                return MediaWikiConst.Case.CASE_SENSITIVE;
            }
            default: {
                throw new WikiDragonException("Unexpected Case: " + pString);
            }
        }
    }

    public static Model getModel(String pString) {
        if (pString == null) return null;
        switch (pString) {
            case "GadgetDefinition": {
                return GADGET_DEFINITION;
            }
            case "flow-board": {
                return FLOW_BOARD;
            }
            case "wikitext": {
                return WIKITEXT;
            }
            case "css": {
                return CSS;
            }
            case "javascript": {
                return JAVASCRIPT;
            }
            case "json": {
                return JSON;
            }
            case "Scribunto": {
                return SCRIBUNTO;
            }
            case "MassMessageListContent": {
                return MASS_MESSAGE_LIST_CONTENT;
            }
            default: {
                logger.warn("Unknown model for revision: " + pString);
                return UNKNOWN;
            }
        }
    }

    public static Format getFormat(String pString) throws WikiDragonException {
        if (pString == null) return null;
        switch (pString) {
            case "text/x-wiki": {
                return MediaWikiConst.Format.TEXT_XWIKI;
            }
            case "text/css": {
                return MediaWikiConst.Format.TEXT_CSS;
            }
            case "text/javascript": {
                return MediaWikiConst.Format.TEXT_JAVASCRIPT;
            }
            case "text/plain": {
                return MediaWikiConst.Format.TEXT_PLAIN;
            }
            case "text/json": {
                return MediaWikiConst.Format.TEXT_JSON;
            }
            case "application/json": {
                return MediaWikiConst.Format.APPLICATION_JSON;
            }
            default: {
                throw new WikiDragonException("Unknown format for revision: " + pString);
            }
        }
    }

}
