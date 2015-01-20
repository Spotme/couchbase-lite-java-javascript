package com.couchbase.lite.javascript;

import com.couchbase.lite.Mapper;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.ViewCompiler;

import java.util.Map;

public class JavaScriptViewCompiler implements ViewCompiler {

	@Override
	public Mapper compileMap(String source, String language, Map<String, Object> ddoc) {
		if (language.equalsIgnoreCase("javascript")) {
			return new ViewMapRhino(source, ddoc);
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
