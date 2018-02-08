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

package org.hucompute.wikidragon;

import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.Revision;
import org.hucompute.wikidragon.core.model.WikiDragonDatabase;
import org.hucompute.wikidragon.core.model.neo.NeoWikiDragonDatabase;
import org.hucompute.wikidragon.core.model.neobat.NeoBatWikiDragonDatabase;
import org.hucompute.wikidragon.core.revcompression.NoneRevisionCompressor;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class WikiDragonTest {

    public static void main(String[] args) throws Exception {
        new WikiDragonTest().plainNeoBatXMLDumpImportTest();
    }

    private File prepareTmpDir() {
        File lResult = new File("test_tmp");
        if (lResult.exists()) IOUtil.delete(lResult, true);
        lResult.mkdirs();
        return lResult;
    }

    @Test
    public void plainNeoXMLDumpImportTest() throws WikiDragonException {
        String lSampleDumpFilename = "/simplewiki-20180201-pages-meta-history-sample.xml";
        File lTestTmpDir = prepareTmpDir();
        File lDBPath = new File(lTestTmpDir.getAbsolutePath()+File.separator+"db");
        try (WikiDragonDatabase lDB = new NeoWikiDragonDatabase(lDBPath, true)) {
            // Import Dump
            MediaWiki lMediaWiki = lDB.getMediaWikiCollection().importMediaWiki(this.getClass().getResourceAsStream(lSampleDumpFilename), "UTF-8", new NoneRevisionCompressor());
            // Check Completeness of Pages via PageList
            List<Page> lPages = lMediaWiki.getPagesList();
            Assert.assertEquals(4, lPages.size());
            // Check Completeness of Pages via Namespaces
            Assert.assertEquals(2, lMediaWiki.getNamespace(0).getPagesList().size());
            Assert.assertEquals(1, lMediaWiki.getNamespace(10).getPagesList().size());
            Assert.assertEquals(1, lMediaWiki.getNamespace(14).getPagesList().size());
            // Check query for qualified names
            Assert.assertNotNull(lMediaWiki.getPage("April"));
            Assert.assertNotNull(lMediaWiki.getPage("August"));
            Assert.assertNotNull(lMediaWiki.getPage("Template:Stub"));
            Assert.assertNotNull(lMediaWiki.getPage("Category:Computer science"));
            // Check completeness and order of revisions
            {
                List<Revision> lRevisions = lMediaWiki.getPage("April").getRevisionsList();
                Assert.assertEquals(4, lRevisions.size());
                Assert.assertEquals(2130, lRevisions.get(0).getId());
                Assert.assertEquals(4183, lRevisions.get(1).getId()); // wrong position in XML dump
                Assert.assertEquals(5043, lRevisions.get(2).getId());
                Assert.assertEquals(5715, lRevisions.get(3).getId()); // from continued page
            }
            // Check Ad hoc HTML Parsing
            {
                Revision lRevision = lMediaWiki.getPage("April").getLatestRevision();
                String lHTML  = lRevision.getHtml();
                Assert.assertNotNull(lHTML);
                System.out.println(lHTML);
            }
        }
        finally {
            IOUtil.delete(lTestTmpDir, true);
        }
    }

    @Test
    public void plainNeoBatXMLDumpImportTest() throws WikiDragonException {
        String lSampleDumpFilename = "/simplewiki-20180201-pages-meta-history-sample.xml";
        File lTestTmpDir = prepareTmpDir();
        File lDBPath = new File(lTestTmpDir.getAbsolutePath()+File.separator+"db");
        try (WikiDragonDatabase lDB = new NeoBatWikiDragonDatabase(lDBPath, true)) {
            // Import Dump
            MediaWiki lMediaWiki = lDB.getMediaWikiCollection().importMediaWiki(this.getClass().getResourceAsStream(lSampleDumpFilename), "UTF-8", new NoneRevisionCompressor());
            // Check Completeness of Pages via PageList
            List<Page> lPages = lMediaWiki.getPagesList();
            Assert.assertEquals(4, lPages.size());
            // Check Completeness of Pages via Namespaces
            Assert.assertEquals(2, lMediaWiki.getNamespace(0).getPagesList().size());
            Assert.assertEquals(1, lMediaWiki.getNamespace(10).getPagesList().size());
            Assert.assertEquals(1, lMediaWiki.getNamespace(14).getPagesList().size());
            // Check query for qualified names
            Assert.assertNotNull(lMediaWiki.getPage("April"));
            Assert.assertNotNull(lMediaWiki.getPage("August"));
            Assert.assertNotNull(lMediaWiki.getPage("Template:Stub"));
            Assert.assertNotNull(lMediaWiki.getPage("Category:Computer science"));
            // Check completeness and order of revisions
            {
                List<Revision> lRevisions = lMediaWiki.getPage("April").getRevisionsList();
                Assert.assertEquals(4, lRevisions.size());
                Assert.assertEquals(2130, lRevisions.get(0).getId());
                Assert.assertEquals(4183, lRevisions.get(1).getId()); // wrong position in XML dump
                Assert.assertEquals(5043, lRevisions.get(2).getId());
                Assert.assertEquals(5715, lRevisions.get(3).getId()); // from continued page
            }
        }
        finally {
            IOUtil.delete(lTestTmpDir, true);
        }
    }

}
