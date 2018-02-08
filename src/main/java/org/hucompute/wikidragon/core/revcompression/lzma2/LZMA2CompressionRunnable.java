package org.hucompute.wikidragon.core.revcompression.lzma2;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressionTask;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.tukaani.xz.*;

import java.nio.charset.Charset;

public class LZMA2CompressionRunnable implements Runnable {

    private static Logger logger = LogManager.getLogger(LZMA2CompressionRunnable.class);

    public static final int DEFAULT_PRESET = 6;

    protected LZMA2RevisionCompressor diffRevisionCompressor;
    protected RevisionCompressionTask revisionCompressionTask;
    protected Exception exception;
    protected int compressionPreset;

    public LZMA2CompressionRunnable(LZMA2RevisionCompressor pDiffRevisionCompressor, RevisionCompressionTask pRevisionCompressionTask) {
        this (pDiffRevisionCompressor, pRevisionCompressionTask, DEFAULT_PRESET);
    }

    public LZMA2CompressionRunnable(LZMA2RevisionCompressor pDiffRevisionCompressor, RevisionCompressionTask pRevisionCompressionTask, int pCompressionPreset) {
        diffRevisionCompressor = pDiffRevisionCompressor;
        revisionCompressionTask = pRevisionCompressionTask;
        compressionPreset = pCompressionPreset;
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
            XZOutputStream lXZOutputStream = new XZOutputStream(lByteArrayOutputStream, new LZMA2Options(compressionPreset));
            byte[] lData = lTarget.getBytes(Charset.forName("UTF-8"));
            lXZOutputStream.write(lData);
            lXZOutputStream.flush();
            lXZOutputStream.close();
            revisionCompressionTask.setCompressedText(lByteArrayOutputStream.toByteArray());
            revisionCompressionTask.setCompression(WikiDragonConst.Compression.LZMA2);
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            exception = e;
        }
        try {
            diffRevisionCompressor.finishXZCompressionRunnable(this);
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            exception = e;
        }
    }

}
