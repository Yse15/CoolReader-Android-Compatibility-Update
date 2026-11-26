package org.coolreader.crengine;

import org.coolreader.BuildConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class OPDSMirrorManagerTest {

    @Test
    public void publicBuildHasNoLocalPresets() {
        assertNotNull(BuildConfig.FLIBUSTA_MIRRORS);
        assertNotNull(BuildConfig.LOCAL_OPDS_CATALOGS);
        if ("true".equals(System.getProperty("publicBuild"))) {
            assertEquals("PUBLIC_BUILD must not embed FLIBUSTA_MIRRORS", "", BuildConfig.FLIBUSTA_MIRRORS);
            assertEquals("PUBLIC_BUILD must not embed LOCAL_OPDS_CATALOGS", "", BuildConfig.LOCAL_OPDS_CATALOGS);
        }
    }

    @Test
    public void replaceMirrorBasePreservesSubpathAndQuery() {
        String replaced = OPDSMirrorManager.replaceMirrorBase(
                "https://mirror-one.example/opds/search?searchTerm=test",
                "https://mirror-two.example/opds/"
        );
        assertEquals("https://mirror-two.example/opds/search?searchTerm=test", replaced);
    }

    @Test
    public void replaceMirrorBaseHandlesRootUrl() {
        String replaced = OPDSMirrorManager.replaceMirrorBase(
                "https://mirror-one.example/opds",
                "https://mirror-two.example/opds/"
        );
        assertEquals("https://mirror-two.example/opds", replaced);
    }
}

