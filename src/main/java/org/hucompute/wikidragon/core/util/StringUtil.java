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

import gnu.trove.map.hash.TObjectIntHashMap;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public class StringUtil {

    public static String encodeXml(String pString) {
        if (pString == null) return "";
        return pString.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String encodeBF(String pString) {
        return pString.replace("[", "(").replace("]", ")").replace("¤", " ").replace("\t", " ").replace("\r", " ").replace("\n", " ");
    }

    public static String zonedDateTime2String(ZonedDateTime pZonedDateTime) {
        return pZonedDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static String zonedDateTime2String(ZonedDateTime pZonedDateTime, ZoneId pTargetZoneId) {
        return pZonedDateTime.withZoneSameInstant(pTargetZoneId).format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static ZonedDateTime string2ZonedDateTime(String pString) {
        return ZonedDateTime.parse(pString, DateTimeFormatter.ISO_DATE_TIME);
    }

    public static long zonedDateTime2Long(ZonedDateTime pZonedDateTime) {
        return pZonedDateTime.toInstant().toEpochMilli();
    }

    public static String escapeCassandraString(String pString) {
        return pString.replaceAll("'", "''");
    }

    public static String clearControlCharacters(String pString) {
        StringBuilder lResult = new StringBuilder();
        for (char c:pString.toCharArray()) {
            if (!Character.isISOControl(c)) {
                lResult.append(c);
            }
        }
        return lResult.toString();
    }

    public static String getLongestCommonSubstring(String pA, String pB) {
        int lStart = 0;
        int lMax = 0;
        for (int i=0; i<pA.length(); i++) {
            for (int j=0; j<pB.length(); j++) {
                int x = 0;
                while (pA.charAt(i+x) == pB.charAt(j+x)) {
                    x++;
                    if (((i+x) >= pA.length()) || ((j+x) >= pB.length())) break;
                }
                if (x > lMax) {
                    lMax = x;
                    lStart = i;
                }
            }
        }
        return pA.substring(lStart, (lStart+lMax));
    }

    public static int getLongestCommonSubstringLength(String pA, String pB) {
        int lStart = 0;
        int lMax = 0;
        for (int i=0; i<pA.length(); i++) {
            for (int j=0; j<pB.length(); j++) {
                int x = 0;
                while (pA.charAt(i+x) == pB.charAt(j+x)) {
                    x++;
                    if (((i+x) >= pA.length()) || ((j+x) >= pB.length())) break;
                }
                if (x > lMax) {
                    lMax = x;
                    lStart = i;
                }
            }
        }
        return lMax;
    }

    public static double getCosineSimilarity(TObjectIntHashMap<String> pTokensA, TObjectIntHashMap<String> pTokensB) {
        Set<String> lTokens = new HashSet<>(pTokensA.keySet());
        lTokens.addAll(pTokensB.keySet());
        double lZaehler = 0;
        double lASquared = 0;
        double lBSquared = 0;
        for (String lToken:lTokens) {
            int lFreqA = pTokensA.containsKey(lToken) ? pTokensA.get(lToken) : 0;
            int lFreqB = pTokensB.containsKey(lToken) ? pTokensB.get(lToken) : 0;
            lZaehler += lFreqA*lFreqB;
            lASquared += lFreqA*lFreqA;
            lBSquared += lFreqB*lFreqB;
        }
        double lNenner = Math.sqrt(lASquared) * Math.sqrt(lBSquared);
        return lNenner <= 0 ? 0 : lZaehler/lNenner;
    }

    public static boolean isAlphaNumeric(String pString) {
        if (pString.length() == 0) return false;
        for (char c:pString.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    public static boolean isNumber(String pString) {
        try {
            Double.parseDouble(pString);
            return true;
        }
        catch (NumberFormatException e) {
            try {
                Long.parseLong(pString);
                return true;
            }
            catch (NumberFormatException f) {
                return false;
            }
        }
    }

}
