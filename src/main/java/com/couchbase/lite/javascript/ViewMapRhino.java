package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.SpecialKey;
import com.couchbase.lite.util.Log;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ViewMapRhino implements Mapper {

	private String mapSrc;
	private MapperFunctionContainer mSharedScope;

	private Function mMapFunction = null;

	private WrapFactory mWrapFactory;

	private Emitter mEmitter;

	protected Map<String, Object> mDesignDoc;

    protected Context mContext;


    protected Map<Long, Long> sequences = Collections.synchronizedMap(new HashMap<Long, Long>());


    static class MyFactory extends ContextFactory
    {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex)
        {
            if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
                return true;
            }
            return super.hasFeature(cx, featureIndex);
        }
    }


    static {
        ContextFactory.initGlobal(new MyFactory());
    }

    /**
     * @throws EvaluatorException when compileFunction() failed. Normally this means, that JS code is not valid/malformed.
     */
    public ViewMapRhino(final String src, final Map<String, Object> ddoc) throws EvaluatorException {
        mapSrc = src;
        mDesignDoc = ddoc;

        mContext = Context.enter();

        mSharedScope = new MapperFunctionContainer(Context.getCurrentContext(), true);
        mWrapFactory = new CustomWrapFactory(mSharedScope);

        // Android dex won't allow us to create our own classes
        mContext.setOptimizationLevel(-1);
        mContext.setWrapFactory(mWrapFactory);

        try {
            final Method emitMethod = mSharedScope.getClass().getMethod("emit", Object.class, Object.class);
            final FunctionObject emitFunction = new FixedScopeFunctionObject("emit", emitMethod, mSharedScope, mSharedScope);

            mSharedScope.put("emit", mSharedScope, emitFunction);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        try {
            final Method emitFtsMethod = mSharedScope.getClass().getMethod("emit_fts", Object.class, Object.class);
            final FunctionObject emitFtsFunction = new FixedScopeFunctionObject("emit_fts", emitFtsMethod, mSharedScope, mSharedScope);

            mSharedScope.put("emit_fts", mSharedScope, emitFtsFunction);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        try {
            final Method isArray = mSharedScope.getClass().getMethod("isArray", Object.class);
            final FunctionObject isArrayFunction = new FixedScopeFunctionObject("isArray", isArray, mSharedScope, mSharedScope);

            mSharedScope.put("isArray", mSharedScope, isArrayFunction);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        try {
            final ModuleSourceProvider sourceProvider = new DesignDocumentModuleProvider(mDesignDoc);
            final ModuleScriptProvider scriptProvider = new SoftCachingModuleScriptProvider(sourceProvider);
            final RequireBuilder builder = new RequireBuilder();

            builder.setModuleScriptProvider(scriptProvider);

            final Require require = builder.createRequire(mContext, mSharedScope);

            require.setParentScope(mSharedScope);
            require.setPrototype(mSharedScope);
            require.install(mSharedScope);
        } catch (Exception e) {
            Log.e(Database.TAG, "Unable to load require function!", e);
        }

        mMapFunction = mContext.compileFunction(mSharedScope, mapSrc, "map", 1, null); // compile the map function

        Context.exit();
	}

	@Override
	public void map(final Map<String, Object> document, final Emitter emitter, final long sequence) {
        Context cx = Context.enter();
        // Android dex won't allow us to create our own classes
        cx.setOptimizationLevel(-1);

        sequences.put(Thread.currentThread().getId(), sequence);
        mEmitter = emitter;

        Scriptable threadScope = cx.newObject(mSharedScope);
        threadScope.setPrototype(mSharedScope);
        threadScope.setParentScope(null);

        final Object[] args = new Object[] {
                mWrapFactory.wrapNewObject(cx, threadScope, document)
        };

        try {
            mMapFunction.call(cx, threadScope, threadScope, args);
        } catch (org.mozilla.javascript.RhinoException | NullPointerException e) {
            // Error in the JavaScript view - CouchDB swallows the error and tries the next document
            Log.e(Database.TAG, "Error in javascript view " + e.getMessage() +",\n" + mapSrc + "\n with document:\n" + document, e);
            return;
        }

        cx.exit();
    }

	class MapperFunctionContainer extends MapReduceFunctionContainer {

		public MapperFunctionContainer(Context cx, boolean sealed) {
			super(cx, sealed);
		}

		public boolean emit(Object key, Object value) {
			if (key instanceof Undefined) key = null;
			if (value instanceof Undefined) value = null;

            String keyJson = null;
            String valueJson = null;
            try {
                keyJson = Manager.getObjectMapper().writeValueAsString(key);
                if (value==null) {
                    valueJson = null;
                } else{
                    valueJson = Manager.getObjectMapper().writeValueAsString(value);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

			mEmitter.emitJSON(keyJson, valueJson, sequences.get(Thread.currentThread().getId()));
            return true;
		}

        public void emit_fts(Object key, Object value) {
            if (key instanceof Undefined) key = null;
            if (value instanceof Undefined) value = null;

            String keyJson = null;
            String valueJson = null;
            try {
                keyJson = Manager.getObjectMapper().writeValueAsString(key);
                if (value==null) {
                    valueJson = null;
                } else{
                    valueJson = Manager.getObjectMapper().writeValueAsString(value);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            mEmitter.emitJSON(new SpecialKey(keyJson), valueJson, sequences.get(Thread.currentThread().getId()));
        }
	}
}
