package org.coolreader.crengine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.Assert.*;

public class OPDSUtilTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void isXmlFeedContentTypeHandlesNullAndVariants() {
        assertFalse(OPDSUtil.DownloadTask.isXmlFeedContentType(null));
        assertFalse(OPDSUtil.DownloadTask.isXmlFeedContentType("text/html; charset=utf-8"));
        assertTrue(OPDSUtil.DownloadTask.isXmlFeedContentType("application/atom+xml;profile=opds-catalog"));
        assertTrue(OPDSUtil.DownloadTask.isXmlFeedContentType("Application/XML"));
        assertTrue(OPDSUtil.DownloadTask.isXmlFeedContentType("text/xml; charset=utf-8"));
    }

    @Test
    public void sha1HexProducesConsistent40CharDigest() {
        String h1 = OPDSUtil.DownloadTask.sha1Hex("https://example.org/opds");
        String h2 = OPDSUtil.DownloadTask.sha1Hex("https://example.org/opds");
        String h3 = OPDSUtil.DownloadTask.sha1Hex("https://example.org/opds2");
        assertEquals(40, h1.length());
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
    }

    @Test
    public void cacheEntryRoundTripPreservesBodyAndMetadata() throws Exception {
        File bodyFile = tempFolder.newFile("entry.body");
        File metaFile = tempFolder.newFile("entry.meta");
        byte[] payload = "<feed/>".getBytes(StandardCharsets.UTF_8);

        OPDSUtil.DownloadTask.CacheMeta meta = new OPDSUtil.DownloadTask.CacheMeta();
        meta.timestamp = 1700000000000L;
        meta.etag = "\"abc123\"";
        meta.lastModified = "Wed, 21 Oct 2015 07:28:00 GMT";
        meta.contentType = "application/atom+xml";

        OPDSUtil.writeCacheEntry(bodyFile, metaFile, payload, meta);

        OPDSUtil.DownloadTask.CacheMeta readBack = OPDSUtil.readCacheMeta(metaFile);
        assertNotNull(readBack);
        assertEquals(meta.timestamp, readBack.timestamp);
        assertEquals(meta.etag, readBack.etag);
        assertEquals(meta.lastModified, readBack.lastModified);
        assertEquals(meta.contentType, readBack.contentType);
        assertEquals(payload.length, bodyFile.length());
    }

    @Test
    public void opdsHandlerParsesSearchLinkRelativeUrlsCoversAndAcquisition() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
                + "  <title>Test Catalog</title>\n"
                + "  <link rel=\"search\" type=\"application/atom+xml\" href=\"/opds/search?q={searchTerms}\" />\n"
                + "  <entry>\n"
                + "    <title>Book &amp; Title</title>\n"
                + "    <author><name>Author One</name></author>\n"
                + "    <link rel=\"http://opds-spec.org/image\" type=\"image/png\" href=\"/covers/1_full.png\" />\n"
                + "    <link rel=\"http://opds-spec.org/image/thumbnail\" type=\"image/jpeg\" href=\"/covers/1_thumb.jpg\" />\n"
                + "    <link rel=\"http://opds-spec.org/acquisition\" type=\"application/epub+zip\" href=\"/books/1.epub\" />\n"
                + "    <link rel=\"http://opds-spec.org/acquisition\" type=\"application/fb2+zip\" href=\"/books/1.fb2.zip\" />\n"
                + "  </entry>\n"
                + "</feed>";

        URL feedUrl = new URL("https://example.org/opds/root");
        OPDSUtil.OPDSHandler handler = new OPDSUtil.OPDSHandler(feedUrl);
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);

        assertNotNull(handler.docInfo);
        assertNotNull(handler.docInfo.searchLink);
        assertEquals("https://example.org/opds/search?q={searchTerms}", handler.docInfo.searchLink.href);

        assertEquals(1, handler.entries.size());
        OPDSUtil.EntryInfo entry = handler.entries.get(0);
        assertEquals("Book & Title", entry.title);
        assertEquals("Author One", entry.getAuthors());
        // Thumbnail is preferred over full image when both are present
        assertEquals("https://example.org/covers/1_thumb.jpg", entry.icon);

        OPDSUtil.LinkInfo best = entry.getBestAcquisitionLink();
        assertNotNull(best);
        assertEquals("application/fb2+zip", best.type);
        assertEquals("https://example.org/books/1.fb2.zip", best.href);
    }
}

