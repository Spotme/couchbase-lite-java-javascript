package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;

import java.lang.reflect.Method;
import java.util.Map;

public class ViewMapRhino implements Mapper {

	private final String mapSrc;
	private final MapperFunctionContainer mScope;

	private Context mContext;

	private Function mMapFunction = null;

	private final WrapFactory mWrapFactory;

	private Emitter mEmitter;

	public ViewMapRhino(String src) {
		mapSrc = src;

		mContext = Context.enter();

		mScope = new MapperFunctionContainer(Context.getCurrentContext(), true);
		mWrapFactory = new CustomWrapFactory(mScope);

		// Android dex won't allow us to create our own classes
		mContext.setOptimizationLevel(-1);
		mContext.setWrapFactory(mWrapFactory);

		try {
			final Method emitMethod = mScope.getClass().getMethod("emit", Object.class, Object.class);
			final FunctionObject emitFunction = new FixedScopeFunctionObject("emit", emitMethod, mScope, mScope);

			mScope.put("emit", mScope, emitFunction);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}

		//	    try {
		//		    final ModuleSourceProvider sourceProvider = new DesignDocumentModuleProvider(ddoc);
		//		    final ModuleScriptProvider scriptProvider = new SoftCachingModuleScriptProvider(sourceProvider);
		//		    final RequireBuilder builder = new RequireBuilder();
		//
		//		    builder.setModuleScriptProvider(scriptProvider);
		//
		//		    final Require require = builder.createRequire(ctx, mScope);
		//
		//		    require.setParentScope(mScope);
		//		    require.setPrototype(mScope);
		//		    require.install(mScope);
		//	    } catch (Exception e) {
		//		    Log.e(Database.TAG, "Unable to load require function!", e);
		//	    }

		try {
			mMapFunction = mContext.compileFunction(mScope, mapSrc, "map", 1, null); // compile the map function
		} catch (org.mozilla.javascript.EvaluatorException e) {
			// Error in the JavaScript view - CouchDB swallows  the error and tries the next document
			Log.e(Database.TAG, "Javascript syntax error in view:\n" + src, e);
			return;
		}

		Context.exit();
	}

	@Override
	public void map(Map<String, Object> document, Emitter emitter) {
		mContext = Context.enter();
		mEmitter = emitter;

		try {
			final Object[] args = new Object[] {
					mWrapFactory.wrapNewObject(mContext, mScope, document)
			};

			mMapFunction.call(mContext, mScope, mScope, args);
		} catch (org.mozilla.javascript.RhinoException e) {
			// Error in the JavaScript view - CouchDB swallows the error and tries the next document
			Log.e(Database.TAG, "Error in javascript view:\n" + mapSrc + "\n with document:\n" + document, e);
			return;
		}

		Context.exit();
	}

	class MapperFunctionContainer extends MapReduceFunctionContainer {

		public MapperFunctionContainer(Context cx, boolean sealed) {
			super(cx, sealed);
		}

		public void emit(Object key, Object value) {
			if (key instanceof Undefined) key = null;
			if (value instanceof Undefined) value = null;

			String keyJSON = (String) NativeJSON.stringify(mContext, mScope, key, null, null);
			String valueJSON = (String) NativeJSON.stringify(mContext, mScope, value, null, null);

			mEmitter.emitJSON(keyJSON, valueJSON);
		}
	}
}
