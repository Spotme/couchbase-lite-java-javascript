package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.ViewCompiler;
import com.couchbase.lite.util.Log;

import java.util.Map;

public class JavaScriptViewCompiler implements ViewCompiler {

	@Override
	public Mapper compileMap(String source, String language, Map<String, Object> ddoc) {
		if (language.equalsIgnoreCase("javascript")) {
            try{
                return new ViewMapRhino(source, ddoc);
            } catch (org.mozilla.javascript.EvaluatorException e) {
                // Error in the JavaScript view - CouchDB swallows  the error and tries the next document
                Log.e(Database.TAG, "Javascript syntax error in view:\n" + source, e);
                return null;
            }
		}

		throw new IllegalArgumentException(language + " is not supported");
	}

	@Override
	public Reducer compileReduce(String source, String language) {
		if (language.equalsIgnoreCase("javascript")) {
			return new ViewReduceRhino(source);
		}

		throw new IllegalArgumentException(language + " is not supported");
	}
}
