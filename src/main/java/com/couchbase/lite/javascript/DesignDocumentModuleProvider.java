package com.couchbase.lite.javascript;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProviderBase;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Handles imports from a design document itself
 */
class DesignDocumentModuleProvider extends ModuleSourceProviderBase {

	protected final Map<String, Object> mDesignDoc;

	public DesignDocumentModuleProvider(final Map<String, Object> ddoc) {
		mDesignDoc = ddoc;
	}

	@Override
	public ModuleSource loadSource(String moduleId, Scriptable paths, Object validator) throws IOException, URISyntaxException {
		return getModule(moduleId, validator);
	}

	@Override
	protected ModuleSource loadFromUri(URI uri, URI base, Object validator) throws IOException, URISyntaxException {
		return getModule(uri.toString(), validator);
	}

	protected ModuleSource getModule(String moduleId, Object validator) throws IOException {
		try {
			final String[] parts = moduleId.split("/");

			Object currentObject = mDesignDoc;
			for (final String part : parts) {
				currentObject = ((Map<String, Object>) currentObject).get(part);
			}

			final Reader srcReader = new StringReader((String) currentObject);
			return new ModuleSource(srcReader, null, new URI(moduleId).resolve(""), new URI(moduleId), validator);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
};
