package org.coolreader.crengine;

import android.content.Context;
import android.content.SharedPreferences;

import org.coolreader.BuildConfig;
import org.coolreader.db.CRDBService;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages optional local OPDS catalog presets and mirror probing/rotation.
 */
public class OPDSMirrorManager {
	private static final Logger log = L.create("opdsm");
	private static final String PREFS_NAME = "opds_mirrors";
	private static final String KEY_FLIBUSTA_ACTIVE = "flibusta_active_url";
	private static final String KEY_LOCAL_PRESETS_SIG = "local_presets_sig";
	private static final int PROBE_TIMEOUT_MS = 5000;
	private static final int ORBOT_CHECK_TIMEOUT_MS = 500;
	private static final String ORBOT_HOST = "127.0.0.1";
	private static final int ORBOT_PORT = 8118;

	public interface ProbeCallback {
		void onMirrorFound(String workingBaseUrl);
		void onNoMirrorFound();
	}

	public static List<String> getFlibustaMirrors() {
		ArrayList<String> list = new ArrayList<>();
		String raw = BuildConfig.FLIBUSTA_MIRRORS;
		if (raw == null || raw.trim().isEmpty())
			return list;
		for (String part : raw.split(",")) {
			String url = part.trim();
			if (!url.isEmpty())
				list.add(trimTrailingSlash(url));
		}
		return list;
	}

	public static boolean hasFlibustaPreset() {
		return !getFlibustaMirrors().isEmpty();
	}

	public static String getActiveFlibustaMirror(Context context) {
		List<String> mirrors = getFlibustaMirrors();
		if (mirrors.isEmpty())
			return null;
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String saved = prefs.getString(KEY_FLIBUSTA_ACTIVE, null);
		if (saved != null && !saved.trim().isEmpty())
			return trimTrailingSlash(saved);
		return mirrors.get(0);
	}

	public static void setActiveFlibustaMirror(Context context, String url) {
		if (context == null || url == null || url.trim().isEmpty())
			return;
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		prefs.edit().putString(KEY_FLIBUSTA_ACTIVE, trimTrailingSlash(url)).apply();
	}

	public static boolean isFlibustaMirrorUrl(String url) {
		if (url == null)
			return false;
		String clean = trimTrailingSlash(url);
		for (String mirror : getFlibustaMirrors()) {
			if (clean.equals(mirror) || clean.startsWith(mirror + "/"))
				return true;
			try {
				String mHost = new URL(mirror).getHost();
				String uHost = new URL(clean).getHost();
				if (mHost != null && mHost.equalsIgnoreCase(uHost))
					return true;
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	public static String replaceMirrorBase(String fullUrl, String newBaseUrl) {
		if (fullUrl == null || newBaseUrl == null)
			return fullUrl;
		String cleanNew = trimTrailingSlash(newBaseUrl);
		for (String mirror : getFlibustaMirrors()) {
			if (fullUrl.equals(mirror) || fullUrl.equals(mirror + "/"))
				return cleanNew;
			if (fullUrl.startsWith(mirror + "/"))
				return cleanNew + fullUrl.substring(mirror.length());
		}
		try {
			URL oldU = new URL(fullUrl);
			URL newU = new URL(cleanNew);
			String oldRoot = oldU.getProtocol() + "://" + oldU.getAuthority();
			String newRoot = newU.getProtocol() + "://" + newU.getAuthority();
			if (fullUrl.startsWith(oldRoot))
				return newRoot + fullUrl.substring(oldRoot.length());
		} catch (Exception ignored) {
		}
		return cleanNew;
	}

	/**
	 * Seeds local-only OPDS presets once per configuration signature so user edits/deletions are preserved.
	 */
	public static void ensureLocalPresets(Context context, CRDBService.LocalBinder db) {
		if (context == null || db == null)
			return;
		String sig = BuildConfig.FLIBUSTA_MIRRORS + "|" + BuildConfig.LOCAL_OPDS_CATALOGS;
		if (sig.equals("|"))
			return;
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String prevSig = prefs.getString(KEY_LOCAL_PRESETS_SIG, "");
		if (sig.equals(prevSig))
			return;
		prefs.edit().putString(KEY_LOCAL_PRESETS_SIG, sig).apply();

		String activeFlibusta = getActiveFlibustaMirror(context);
		if (activeFlibusta != null) {
			db.saveOPDSCatalog(null, activeFlibusta, "Flibusta", null, null);
		}
		String rawCatalogs = BuildConfig.LOCAL_OPDS_CATALOGS;
		if (rawCatalogs != null && !rawCatalogs.trim().isEmpty()) {
			for (String entry : rawCatalogs.split(";")) {
				String[] parts = entry.split("\\|", 2);
				if (parts.length == 2) {
					String url = parts[0].trim();
					String name = parts[1].trim();
					if (!url.isEmpty() && !name.isEmpty())
						db.saveOPDSCatalog(null, url, name, null, null);
				}
			}
		}
	}

	public static void probeMirrorsAsync(final Context context, final String failedUrl, final ProbeCallback callback) {
		final List<String> mirrors = getFlibustaMirrors();
		if (mirrors.isEmpty()) {
			if (callback != null)
				BackgroundThread.instance().executeGUI(callback::onNoMirrorFound);
			return;
		}
		BackgroundThread.instance().executeBackground(() -> {
			boolean orbotUp = isOrbotAvailable();
			for (String mirror : mirrors) {
				if (failedUrl != null && isSameHost(failedUrl, mirror))
					continue;
				if (isOnionUrl(mirror) && !orbotUp) {
					log.d("Skipping .onion mirror (ORobot not running): " + mirror);
					continue;
				}
				if (probeSingleMirror(mirror)) {
					setActiveFlibustaMirror(context, mirror);
					if (callback != null)
						BackgroundThread.instance().executeGUI(() -> callback.onMirrorFound(mirror));
					return;
				}
			}
			if (callback != null)
				BackgroundThread.instance().executeGUI(callback::onNoMirrorFound);
		});
	}

	private static boolean probeSingleMirror(String mirrorUrl) {
		HttpURLConnection conn = null;
		try {
			URL url = normalizeOrobotUrl(mirrorUrl);
			Proxy proxy = isOnionUrl(mirrorUrl)
					? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ORBOT_HOST, ORBOT_PORT))
					: null;
			URLConnection raw = proxy == null ? url.openConnection() : url.openConnection(proxy);
			if (!(raw instanceof HttpURLConnection))
				return false;
			conn = (HttpURLConnection) raw;
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "CoolReader/3(Android)");
			conn.setConnectTimeout(PROBE_TIMEOUT_MS);
			conn.setReadTimeout(PROBE_TIMEOUT_MS);
			conn.setInstanceFollowRedirects(true);
			int code = conn.getResponseCode();
			log.d("Probe " + mirrorUrl + " -> HTTP " + code);
			return (code >= 200 && code < 400) || code == HttpURLConnection.HTTP_UNAUTHORIZED;
		} catch (Exception e) {
			log.d("Probe failed for " + mirrorUrl + ": " + e.getMessage());
			return false;
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}

	public static boolean isOrbotAvailable() {
		try (Socket s = new Socket()) {
			s.connect(new InetSocketAddress(ORBOT_HOST, ORBOT_PORT), ORBOT_CHECK_TIMEOUT_MS);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean isOnionUrl(String url) {
		if (url == null)
			return false;
		if (url.startsWith("orobot://") || url.startsWith("orobots://"))
			return true;
		try {
			String host = new URL(url).getHost();
			return host != null && host.endsWith(".onion");
		} catch (Exception e) {
			return false;
		}
	}

	private static URL normalizeOrobotUrl(String url) throws Exception {
		if (url.startsWith("orobot://"))
			return new URL("http://" + url.substring(9));
		if (url.startsWith("orobots://"))
			return new URL("https://" + url.substring(10));
		return new URL(url);
	}

	private static boolean isSameHost(String urlA, String urlB) {
		try {
			String hA = new URL(normalizeOrobotUrl(urlA).toString()).getHost();
			String hB = new URL(normalizeOrobotUrl(urlB).toString()).getHost();
			return hA != null && hA.equalsIgnoreCase(hB);
		} catch (Exception e) {
			return false;
		}
	}

	private static String trimTrailingSlash(String s) {
		while (s.endsWith("/") && s.length() > 1)
			s = s.substring(0, s.length() - 1);
		return s;
	}
}

