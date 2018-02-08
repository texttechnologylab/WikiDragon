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

import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.parsing.filter.XOWATierMassParserFilter;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public interface MediaWiki extends WikiObject {

    public String getSiteName();

    public void setSiteName(String pSiteName) throws WikiDragonException;

    public String getDbName();

    public String getBase();

    public void setBase(String pBase) throws WikiDragonException;

    public String getGenerator();

    public void setGenerator(String pGenerator) throws WikiDragonException;

    public String getApiUrl();

    public void setApiUrl(String pApiUrl) throws WikiDragonException;

    public MediaWikiConst.Case getCase();

    public void setCase(MediaWikiConst.Case pCase) throws WikiDragonException;

    public Namespace getNamespace(String pName);

    public Namespace getNamespace(int pNamespaceId) throws WikiDragonException;

    public Map<String, Namespace> getNamespaceMap() throws WikiDragonException;

    public Map<Integer, Namespace> getNamespaceIdMap() throws WikiDragonException;

    public Set<Namespace> getNamespaces() throws WikiDragonException;

    public Contributor createContributor(String pName, long pId) throws WikiDragonException;

    public Contributor getContributor(String pName);

    public Contributor getContributor(long pId);

    public Iterable<Contributor> getContributors();

    public WikiObjectIterator<Contributor> getContributorIterator();

    public Namespace createNamespace(int pId, MediaWikiConst.Case pCase, String pName, String pCanonicalName, Set<String> pAliases, boolean pSubPages, MediaWikiConst.Model pDefaultContentModel) throws WikiDragonException;

    public Page getPage(long pId);

    public Page getPage(int pNamespaceId, String pTitle);

    public Page getPage(String pQualifiedTitle);

    public Iterable<Page> getPages();

    public List<Page> getPagesList();

    public WikiObjectIterator<Page> getPageIterator();

    public Revision getRevision(long pId);

    public Page createPage(long pId, Namespace pNamespace, String pTitle) throws WikiDragonException;

    public Set<Page> getCategorizedPages(boolean pRecursive, Page... pCategoryPage) throws WikiDragonException;

    public void importLinkDump(File pInputFile, MediaWikiConst.LinkType pLinkType) throws WikiDragonException;

    public void createPageTiersHTML(ZonedDateTime pTimestamp, XOWATierMassParserFilter pXOWATierMassParserFilter) throws WikiDragonException;

    public void createPageTiersHTML(ZonedDateTime pTimestamp, XOWATierMassParserFilter pXOWATierMassParserFilter, int pMaxThreads) throws WikiDragonException;

    public boolean isPageTierNetworkExtracted(ZonedDateTime pTimestamp) throws WikiDragonException;

    public List<ZonedDateTime> getPageTierNetworkExtractedTimestamps() throws WikiDragonException;

    public void extractPageTierNetwork(ZonedDateTime pTimestamp) throws WikiDragonException;

    public void importWikiDataEntityUsageDump(File pInputFile) throws WikiDragonException;

}
