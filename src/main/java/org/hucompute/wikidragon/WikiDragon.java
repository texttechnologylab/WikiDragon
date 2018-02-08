package org.hucompute.wikidragon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.neo.NeoWikiDragonDatabase;
import org.hucompute.wikidragon.core.parsing.XOWAPageMassParser;
import org.hucompute.wikidragon.core.parsing.filter.XOWAPageMassParserAllFilter;
import org.hucompute.wikidragon.core.revcompression.NoneRevisionCompressor;
import org.hucompute.wikidragon.core.util.IOUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WikiDragon {

    private static Logger logger = LogManager.getLogger(WikiDragon.class);

    public static void printSyntax() {
        System.out.println("WikiDragon <-db DBDirectory> [-r] <command>");
        System.out.println("WikiDragon <-db DBDirectory> [-r] importdumps [dumpfile1] [dumpfile2] [dumpfile3] ...");
        System.out.println("WikiDragon <-db DBDirectory> [-r] importlinks [dumpfile1] [dumpfile2] [dumpfile3] ...");
        System.out.println("WikiDragon <-db DBDirectory> [-r] parsehtml");
        System.out.println("WikiDragon <-db DBDirectory> [-r] getpageinfo <title>");
        System.exit(0);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) printSyntax();
        File lDBDirectory = null;
        boolean lReset = false;
        String lCommand = null;
        int lCommandIndex = -1;
        String lMediaWiki = null;
        String lLanguage = null;
        String lPageTitle = null;
        for (int i=0; i<args.length; i++) {
            switch (args[i]) {
                case "-db": {
                    lDBDirectory = new File(args[++i]);
                    break;
                }
                case "-mw": {
                    lMediaWiki = args[++i];
                    break;
                }
                case "-l": {
                    lLanguage = args[++i];
                    break;
                }
                case "-r": {
                    lReset = true;
                    break;
                }
                case "importdumps": {
                    lCommand = args[i];
                    lCommandIndex = i;
                    break;
                }
                case "importlinks": {
                    lCommand = args[i];
                    lCommandIndex = i;
                    break;
                }
                case "parsehtml": {
                    lCommand = args[i];
                    lCommandIndex = i;
                    break;
                }
                case "getpageinfo": {
                    lCommand = args[i];
                    lCommandIndex = i;
                    break;
                }
            }
        }
        if (lCommand == null) printSyntax();
        switch (lCommand) {
            case "importdumps": {
                try (NeoWikiDragonDatabase lDatabase = new NeoWikiDragonDatabase(lDBDirectory, lReset)) {
                    List<File> lFiles = new ArrayList<>();
                    for (int i=lCommandIndex+1; i<args.length; i++) {
                        lFiles.add(new File(args[i]));
                    }
                    for (File lFile:lFiles) {
                        //lDatabase.getMediaWikiCollection().importMediaWiki(IOUtil.getInputStream(lFile), "UTF-8", new DiffRevisionCompressor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors()*2));
                        //lDatabase.getMediaWikiCollection().importMediaWiki(IOUtil.getInputStream(lFile), "UTF-8", new BZip2RevisionCompressor());
                        lDatabase.getMediaWikiCollection().importMediaWiki(IOUtil.getInputStream(lFile), "UTF-8", new NoneRevisionCompressor());
                    }
                }
                break;
            }
            case "importlinks": {
                try (NeoWikiDragonDatabase lDatabase = new NeoWikiDragonDatabase(lDBDirectory, lReset)) {
                    List<File> lFiles = new ArrayList<>();
                    for (int i=lCommandIndex+1; i<args.length; i++) {
                        lFiles.add(new File(args[i]));
                    }
                    for (File lFile:lFiles) {
                        if (lMediaWiki == null) {
                            lMediaWiki = lFile.getName().substring(0, lFile.getName().indexOf("-"));
                        }
                        MediaWiki lMW = lDatabase.getMediaWikiCollection().getMediaWiki(lMediaWiki);
                        if (lFile.getName().contains("categorylinks")) {
                            lMW.importLinkDump(lFile, MediaWikiConst.LinkType.CATEGORIZATION);
                        }
                        else if (lFile.getName().contains("pagelinks")) {
                            lMW.importLinkDump(lFile, MediaWikiConst.LinkType.ARTICLE);
                        }
                        else if (lFile.getName().contains("redirect")) {
                            lMW.importLinkDump(lFile, MediaWikiConst.LinkType.REDIRECT);
                        }
                    }
                }
                break;
            }
            case "parsehtml": {
                try (NeoWikiDragonDatabase lDatabase = new NeoWikiDragonDatabase(lDBDirectory, lReset)) {
                    for (MediaWiki lWiki:lDatabase.getMediaWikiCollection().getMediaWikis()) {
                        logger.info("Processing Wiki: "+lWiki.getDbName());
                        logger.info("Collecting Pages");
                        List<Page> lPages = new ArrayList<>();
                        for (Page lPage:lWiki.getPages()) {
                            lPages.add(lPage);
                            logger.info("Collecting Pages: "+lPages.size());
                        }
                        //lWiki.createPageTiersHTML(WikiDragonConst.NULLDATETIME, new XOWATierMassParserNSFilter(0), Runtime.getRuntime().availableProcessors());
                        XOWAPageMassParser lXOWAPageMassParser = new XOWAPageMassParser(lWiki, lPages, Runtime.getRuntime().availableProcessors()/2, new XOWAPageMassParserAllFilter());
                        lXOWAPageMassParser.parse();
                    }
                }
                catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
                break;
            }
        }
    }

}
