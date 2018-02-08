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

package org.hucompute.wikidragon.core.revcompression.gzip;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressionTask;

import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

/**
 * @author Rüdiger Gleim
 */
public class GZipCompressionRunnable implements Runnable {

    private static Logger logger = LogManager.getLogger(GZipCompressionRunnable.class);

    protected GZipRevisionCompressor revisionCompressor;
    protected RevisionCompressionTask revisionCompressionTask;
    protected Exception exception;

    public GZipCompressionRunnable(GZipRevisionCompressor pRevisionCompressor, RevisionCompressionTask pRevisionCompressionTask) {
        revisionCompressor = pRevisionCompressor;
        revisionCompressionTask = pRevisionCompressionTask;
    }

    public RevisionCompressionTask getRevisionCompressionTask() {
        return revisionCompressionTask;
    }

    public Exception getException() {
        return exception;
    }

    public void run() {
        exception = null;
        try {
            String lTarget = revisionCompressionTask.getRawText();
            ByteArrayOutputStream lByteArrayOutputStream = new ByteArrayOutputStream();
            GZIPOutputStream lGZIPOutputStream = new GZIPOutputStream(lByteArrayOutputStream);
            byte[] lData = lTarget.getBytes(Charset.forName("UTF-8"));
            lGZIPOutputStream.write(lData);
            lGZIPOutputStream.flush();
            lGZIPOutputStream.close();
            revisionCompressionTask.setCompressedText(lByteArrayOutputStream.toByteArray());
            revisionCompressionTask.setCompression(WikiDragonConst.Compression.GZIP);
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            exception = e;
        }
        try {
            revisionCompressor.finishGZipCompressionRunnable(this);
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            exception = e;
        }
    }

}
