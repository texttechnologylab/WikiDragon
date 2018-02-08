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

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Rüdiger Gleim
 */
public class IOUtil {

    private static Logger logger = LogManager.getLogger(IOUtil.class);

    public static InputStream getInputStream(File pFile) throws IOException {
        return getInputStream(pFile, false);
    }

    public static void copyFile(File pSource, File pTarget) throws IOException {
        byte[] lBuffer = new byte[1024*1024];
        int lRead = 0;
        FileInputStream lInput = new FileInputStream(pSource);
        FileOutputStream lOutput = new FileOutputStream(pTarget);
        while ((lRead = lInput.read(lBuffer)) > 0) {
            lOutput.write(lBuffer, 0, lRead);
        }
        lOutput.flush();
        lOutput.close();
        lInput.close();
    }

    public static InputStream getInputStream(File pFile, boolean pPreferNativeDecompressor) throws IOException {
        if (pFile.getName().toLowerCase().endsWith(".gzip") || pFile.getName().toLowerCase().endsWith(".gz")) {
            logger.info("Using Java for gzip decompression");
            return new GZIPInputStream(new BufferedInputStream(new FileInputStream(pFile), 134217728)); // 128 MB
        }
        else if (pFile.getName().toLowerCase().endsWith(".7z")) {
            if (pPreferNativeDecompressor) {
                if (System.getProperty("os.name").contains("Windows")) {
                    Process lProcess = Runtime.getRuntime().exec(new String[]{"tools/7z.exe", "x", "-so", pFile.getAbsolutePath()});
                    logger.info("Using native 7z for decompression");
                    return lProcess.getInputStream();
                } else if (System.getProperty("os.name").equals("Linux")) {
                    Process lProcess = Runtime.getRuntime().exec(new String[]{"tools/7z", "x", "-so", pFile.getAbsolutePath()});
                    logger.info("Using native 7z for decompression");
                    return lProcess.getInputStream();
                } else {
                    logger.warn("Cannot execute 7z. Unsupported OS: " + System.getProperty("os.name"));
                    logger.info("Using Java for 7z decompression");
                    return new SevenZFileInputStream(pFile);
                }
            }
            else {
                logger.info("Using Java for 7z decompression");
                return new SevenZFileInputStream(pFile);
            }
        }
        else if (pFile.getName().endsWith(".bz2")) {
            logger.info("Using Java for bz2 decompression");
            return new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(pFile), 134217728)); // 128 MB
        }
        else {
            return new BufferedInputStream(new FileInputStream(pFile), 134217728); // 128 MB
        }
    }

    public static Document getDocument(String pURL) throws IOException, JDOMException {
        return getDocument(pURL, false);
    }

    public static Document getDocument(String pURL, boolean pForceAlternativeDownload) throws IOException, JDOMException {
        int lRetries = 3;
        boolean lUseLegacy = pForceAlternativeDownload;
        for (int i=0; i<lRetries; i++) {
            try {
                if (lUseLegacy) {
                    /*HttpClient client = new DefaultHttpClient();
                    client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "Mozilla/5.0 Firefox/26.0");
                    HttpGet request = new HttpGet(pURL.replace("|", "%7C"));
                    HttpResponse response = client.execute(request);
                    StringBuilder lBuilder = new StringBuilder();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                    String line = "";
                    while ((line = rd.readLine()) != null) {
                        lBuilder.append(line + "\n");
                    }*/
                    HttpURLConnection connection = ((HttpURLConnection)new URL(pURL).openConnection());
                    connection.addRequestProperty("User-Agent", "Mozilla/4.0");
                    InputStream input;
                    if (connection.getResponseCode() == 200)  // this must be called before 'getErrorStream()' works
                        input = connection.getInputStream();
                    else input = connection.getErrorStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                    String msg;
                    StringBuilder lBuilder = new StringBuilder();
                    while ((msg =reader.readLine()) != null) {
                        lBuilder.append(msg+"\n");
                    }
                    Document lDocument = new SAXBuilder().build(new StringReader(lBuilder.toString()));
                    if (!lDocument.getRootElement().getName().equals("api")) {
                        throw new IOException("Not an API Document");
                    }
                    return lDocument;
                }
                else {
                    SAXBuilder sax = new SAXBuilder();
                    Document lDocument = sax.build(pURL);
                    if (!lDocument.getRootElement().getName().equals("api")) {
                        logger.warn("Not an API Document - attempting legacy mode for download");
                        lUseLegacy = true;
                        lRetries--;
                        continue;
                    }
                    return lDocument;
                }
            }
            catch (IOException e) {
                if (i < lRetries-1) {
                    logger.error(e.getMessage(), e);
                    logger.error(pURL);
                    logger.error("Retrying: "+(i+1)+"/"+lRetries);
                    // Wair 30s
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                else {
                    throw e;
                }
            }
            catch (JDOMException e) {
                if (i < lRetries-1) {
                    logger.error(e.getMessage(), e);
                    logger.error(pURL);
                    logger.error("Retrying: "+(i+1)+"/"+lRetries);
                    // Wair 30s
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                else {
                    throw e;
                }
            }
        }
        return null;
    }

    public static String uncompress(byte[] pData, WikiDragonConst.Compression pCompression) throws IOException {
        switch (pCompression) {
            case NONE: {
                return new String(pData, Charset.forName("UTF-8"));
            }
            case LZMA2: {
                XZInputStream lXZInputStream = new XZInputStream(new ByteArrayInputStream(pData));
                StringBuilder lText = new StringBuilder();
                InputStreamReader lReader = new InputStreamReader(lXZInputStream, Charset.forName("UTF-8"));
                char[] lBuffer = new char[1048576];
                int lRead = 0;
                while ((lRead = lReader.read(lBuffer)) > 0) {
                    lText.append(lBuffer, 0, lRead);
                }
                lReader.close();
                return lText.toString();
            }
            case GZIP: {
                GZIPInputStream lGZIPInputStream = new GZIPInputStream(new ByteArrayInputStream(pData));
                StringBuilder lText = new StringBuilder();
                InputStreamReader lReader = new InputStreamReader(lGZIPInputStream, Charset.forName("UTF-8"));
                char[] lBuffer = new char[1048576];
                int lRead = 0;
                while ((lRead = lReader.read(lBuffer)) > 0) {
                    lText.append(lBuffer, 0, lRead);
                }
                lReader.close();
                return lText.toString();
            }
            case BZIP2: {
                BZip2CompressorInputStream lBZip2CompressorInputStream = new BZip2CompressorInputStream(new ByteArrayInputStream(pData));
                StringBuilder lText = new StringBuilder();
                InputStreamReader lReader = new InputStreamReader(lBZip2CompressorInputStream, Charset.forName("UTF-8"));
                char[] lBuffer = new char[1048576];
                int lRead = 0;
                while ((lRead = lReader.read(lBuffer)) > 0) {
                    lText.append(lBuffer, 0, lRead);
                }
                lReader.close();
                return lText.toString();
            }
            default: {
                return null;
            }
        }
    }

    public static byte[] compress(String pString, WikiDragonConst.Compression pCompression) throws IOException {
        switch (pCompression) {
            case NONE: {
                return pString.getBytes(Charset.forName("UTF-8"));
            }
            case LZMA2: {
                ByteArrayOutputStream lOutput = new ByteArrayOutputStream();
                XZOutputStream lXZOutputStream = new XZOutputStream(lOutput, new LZMA2Options(6));
                lXZOutputStream.write(pString.getBytes(Charset.forName("UTF-8")));
                lXZOutputStream.flush();
                lXZOutputStream.close();
                return lOutput.toByteArray();
            }
            case GZIP: {
                ByteArrayOutputStream lOutput = new ByteArrayOutputStream();
                GZIPOutputStream lGZIPOutputStream = new GZIPOutputStream(lOutput);
                lGZIPOutputStream.write(pString.getBytes(Charset.forName("UTF-8")));
                lGZIPOutputStream.flush();
                lGZIPOutputStream.close();
                return lOutput.toByteArray();
            }
            case BZIP2: {
                ByteArrayOutputStream lOutput = new ByteArrayOutputStream();
                BZip2CompressorOutputStream lGZIPOutputStream = new BZip2CompressorOutputStream(lOutput);
                lGZIPOutputStream.write(pString.getBytes(Charset.forName("UTF-8")));
                lGZIPOutputStream.flush();
                lGZIPOutputStream.close();
                return lOutput.toByteArray();
            }
            default: {
                return null;
            }
        }
    }

    public static void delete(File pFile, boolean pRecursively) {
        if (!pRecursively) pFile.delete();
        if (pFile.isDirectory()) {
            for (File lFile:pFile.listFiles()) {
                delete(lFile, true);
            }
        }
        pFile.delete();
    }

}
