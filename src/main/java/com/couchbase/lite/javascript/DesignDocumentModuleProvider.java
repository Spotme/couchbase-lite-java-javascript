package com.couchbase.lite.javascript;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 *  Handles require() (imports) from JS code of list/map, using other JS functions from the same design document.
 */
public class DesignDocumentModuleProvider implements ModuleSourceProvider {
	protected final Map<String, Object> mDesignDoc;

	public DesignDocumentModuleProvider(final Map<String, Object> ddoc) {
		mDesignDoc = ddoc;
	}

	@Override
	public ModuleSource loadSource(String moduleId, Scriptable paths, Object validator) throws IOException, URISyntaxException {
		return getModule(moduleId, validator);
	}

    @Override
    public ModuleSource loadSource(URI uri, URI baseUri, Object validator) throws IOException, URISyntaxException {
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
			throw new IOException("Unable to perform 'require()' for module id: " + moduleId, e);
		}
	}
};
