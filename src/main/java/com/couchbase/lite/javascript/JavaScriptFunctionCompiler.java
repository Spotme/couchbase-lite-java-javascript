package com.couchbase.lite.javascript;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.FunctionCompiler;
import com.couchbase.lite.FunctionContainer;
import com.couchbase.lite.Status;
import com.couchbase.lite.router.URLConnection;
import com.couchbase.lite.util.Log;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A list and show JavaScript function compiler that provides the
 * required JavaScript methods to call these functions.
 */
public class JavaScriptFunctionCompiler implements FunctionCompiler {

	// Items output by the view
	protected List<Map<String, Object>> mItems;

	// HTTP request that triggered this call
	protected URLConnection mConnection;

	// thread context
	protected final Context mContext;

	// view collection index for getRow()
	protected int mCurrentListIndex = -1;

	// The design doc containing the list and show functions
	protected Map<String, Object> mDesignDoc;

	protected final ObjectMapper mMapper = new ObjectMapper();

	// request object
	protected Map<String, Object> mRequestProperties;

	// response contents
	protected final StringBuilder mListResponse = new StringBuilder();

	// global shared scope with initialized functions
	protected final ScriptableObject mScope;

    public long getRowDuration;

	// JS query server functions
	protected static final List<String> mGlobalFunctions = Arrays.asList("isArray", "log", "sum", "start");
	protected static final List<String> mMapFunctions = Arrays.asList("emit");
	protected static final List<String> mListFunctions = Arrays.asList("getRow", "send"); //, "provides", "registerType");
	protected static final List<String> mShowFunctions = Arrays.asList(); //, "provides", "registerType");
	protected static final List<String> mReduceFunctions = Arrays.asList();

	protected static final List<List<String>> mAllFunctions = Arrays.asList(mMapFunctions, mListFunctions, mShowFunctions, mReduceFunctions);

	/**
	 * Create a new Context and
	 */
	public JavaScriptFunctionCompiler() {
		mContext = Context.enter();

		// Android dex won't allow us to create our own classes
		mContext.setOptimizationLevel(-1);

        mScope = new JavaScriptFunctionContainer(Context.getCurrentContext());

		mContext.setWrapFactory(new CustomWrapFactory(mScope));

		registerFunctions(mGlobalFunctions);

		mMapper.getFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);

		mContext.exit();
	}

	/**
	 * Builds the request object from the connection, which is then passed into list and show functions.
	 */
	public void buildRequestObject() {

		final String requestMethod = mConnection.getRequestMethod();
		//final String[] path = mConnection.getURL().getPath().split("/")
        final ArrayList<String> path = new ArrayList<String>(Arrays.asList(mConnection.getURL().getPath().split("/")));

        final Map<String, Object> queryParams = new HashMap<String, Object>();

		try {
			final List<NameValuePair> items = URLEncodedUtils.parse(mConnection.getURL().toURI(), "UTF-8");

			for (final NameValuePair pair : items) {
				queryParams.put(pair.getName(), pair.getValue());
			}
		} catch (URISyntaxException ex) { } // do nothing

		final Map<String, Object> requestHeaders = new HashMap<String, Object>();

		for (final Map.Entry<String, List<String>> entry : mConnection.getRequestProperties().entrySet()) {
			try { requestHeaders.put(entry.getKey(), entry.getValue().get(0)); } catch (Exception e) { } // nothing
		}

		final Map<String, Object> requestObj = new HashMap<String, Object>();

		requestObj.put("method", requestMethod);
		requestObj.put("path", path);
		requestObj.put("query", queryParams);
		requestObj.put("headers", requestHeaders);

		if (mConnection.getRequestMethod().equalsIgnoreCase("POST")) {
			try {
				final Map<String, Object> postData = mMapper.readValue(mConnection.getRequestInputStream(), Map.class);
				requestObj.put("body", postData);
			} catch (IOException e) {
			} // Ignore
		}

		mRequestProperties = requestObj;

//		{
//		    "info": {
//		        "db_name": "test_suite_db",
//		        "doc_count": 11,
//		        "doc_del_count": 0,
//		        "update_seq": 11,
//		        "purge_seq": 0,
//		        "compact_running": false,
//		        "disk_size": 4930,
//		        "instance_start_time": "1250046852578425",
//		        "disk_format_version": 4
//		    },
//		    "method": "GET",
//		    "path": [
//		        "test_suite_db",
//		        "_design",
//		        "lists",
//		        "_list",
//		        "basicJSON",
//		        "basicView"
//		    ],
//		    "query": {
//		        "foo": "bar"
//		    },
//		    "headers": {
//		        "Accept": "text/html,application/xhtml+xml ,application/xml;q=0.9,*/*;q=0.8",
//		        "Accept-Charset": "ISO-8859-1,utf-8;q=0.7,*;q=0.7",
//		        "Accept-Encoding": "gzip,deflate",
//		        "Accept-Language": "en-us,en;q=0.5",
//		        "Connection": "keep-alive",
//		        "Cookie": "_x=95252s.sd25; AuthSession=",
//		        "Host": "127.0.0.1:5984",
//		        "Keep-Alive": "300",
//		        "Referer": "http://127.0.0.1:5984/_utils/couch_tests.html?script/couch_tests.js",
//		        "User-Agent": "Mozilla/5.0 Gecko/20090729 Firefox/3.5.2"
//		    },
//		    "cookie": {
//		        "_x": "95252s.sd25",
//		        "AuthSession": ""
//		    }
//		}
	}

	/**
	 * @inheritDoc
	 */
	public Map<String, Object> getRequestProperties() {
		if (mRequestProperties == null) buildRequestObject();

		return mRequestProperties;
	}

	/**
	 * Registers a Java method within the current JavaScript context.
	 * Parameters of the Java method
	 *
	 * @param name The name of the method to define
	 */
	protected void registerFunction(final String name) {
		try {
			for (final Method method : JavaScriptFunctionContainer.class.getDeclaredMethods()) {
				if (method.getName().equals(name)) {
					final FunctionObject fn = new FixedScopeFunctionObject(name, method, mScope, mScope);
					mScope.put(name, mScope, fn);

					break;
				}
			}
		} catch (SecurityException e) {
			Log.w(Database.TAG, String.format("Unable to get method '%s' from class '%s'.", name));
		}
	}

	protected void registerFunctions(final List<String> functions) {
		if (functions == null) return;

		for (final String fn : functions) {
			registerFunction(fn);
		}
	}

	/**
	 * Unregister a function from the current global scope
	 *
	 * @param name      The function name to remove
	 */
	protected void unregisterFunction(final String name) {
		try {
			if (mScope.has(name, mScope)) mScope.delete(name);
		} catch (Exception e) {
			Log.e(Database.TAG, String.format("Unable to unregister function '%s'!", name));
		}
	}

	/**
	 * Un-registers all functions from the global scope with the exception of
	 * the ones passed into this method.
	 *
	 * @param except    The list of functions to keep.
	 */
	protected void unregisterFunctions(final List<String> except) {
		for (final List<String> list : mAllFunctions) {
			for (final String function : list) {
				if (except == null || !except.contains(function)) {
					unregisterFunction(function);
				}
			}
		}
	}

	/**
	 * @inheritDoc
	 */
	public void setViewResult(final Map<String, Object> result) {
		mItems = (List<Map<String, Object>>) result.get("rows");
	}

	///////////////////////////////////// FunctionCompiler

	/**
	 * @inheritDoc
	 */
	public String list(final String listName, final Map<String, Object> head) throws CouchbaseLiteException {
        if (mDesignDoc == null) return null;

        Date d1 = new Date();
        Log.i(Log.TAG_VIEW, "Executing list function %s", listName);
        final Map<String, Object> requestProperties = getRequestProperties();
        final WrapFactory wrapper = mContext.getWrapFactory();

        String listSrc = "";

        try {
            mContext.enter();

            unregisterFunctions(mListFunctions);
            registerFunctions(mListFunctions);

            try {
                final ModuleSourceProvider sourceProvider = new DesignDocumentModuleProvider(mDesignDoc);
                final ModuleScriptProvider scriptProvider = new SoftCachingModuleScriptProvider(sourceProvider);
                final RequireBuilder builder = new RequireBuilder();

                builder.setModuleScriptProvider(scriptProvider);

                final Require require = builder.createRequire(mContext, mScope);

                require.setParentScope(mScope);
                require.setPrototype(mScope);
                require.install(mScope);
            } catch (Exception e) {
                Log.e(Database.TAG, "Unable to load require function!", e);
            }

            listSrc = (String) ((Map<String, Object>) mDesignDoc.get("lists")).get(listName);
            getRowDuration = 0;
            // compile the list function and call it
            final Function listFunc = mContext.compileFunction(mScope, listSrc, listName, 1, null);
            final Object[] params = new Object[] {
                    wrapper.wrapNewObject(mContext, mScope, head),
                    wrapper.wrapNewObject(mContext, mScope, requestProperties)
            };
	        final Object listFuncResult = listFunc.call(mContext, mScope, mScope, params);
            Date d2 = new Date();

	        Context.exit();

	        final String resultString;
	        if (listFuncResult instanceof Undefined) { //was no return
		        resultString = "";
	        } else if (listFuncResult instanceof String) {
                resultString = (String) listFuncResult;
            } else if (listFuncResult instanceof NativeObject) {
                Object json = ((NativeObject)listFuncResult).get("json");
                resultString = mMapper.writeValueAsString(json);
	        } else { //XXX: Check if we could have not a String here
                //Log.e("victor", "TU PEUX PAS TEST");
		        resultString = mMapper.writeValueAsString(listFuncResult);
	        }

            Date d3 = new Date();
            Log.i(Log.TAG_VIEW, "... Finished executing list function %s, took %s ms, getRow took %s ms, JSON conversion took %s ms", listName, (d3.getTime() - d1.getTime()), getRowDuration, (d3.getTime() - d2.getTime()));
            return mListResponse.append(resultString).toString();
		} catch (EvaluatorException eval) {
			Log.e(Database.TAG, "Javascript syntax error in list function:\n" + listSrc, eval);
			throw new CouchbaseLiteException(new Status(Status.INTERNAL_SERVER_ERROR));
		} catch (Exception e) {
			Log.e(Database.TAG, "Javascript error in list function:\n" + listSrc, e);
			throw new CouchbaseLiteException(new Status(Status.BAD_REQUEST));
		}
	}

	/**
	 * @inheritDoc
	 */
	public JavaScriptFunctionCompiler newInstance() {
		return new JavaScriptFunctionCompiler();
	}

	/**
	 * @inheritDoc
	 */
	public void setDesignDocument(final Map<String, Object> document) {
		mDesignDoc = document;
	}

	/**
	 * @param conn The connection where to set the response
	 */
	public void setRequestObject(final URLConnection conn) {
		mConnection = conn;
	}

	/**
	 * @inheritDoc
	 */
	public String show(final String showName, final Map<String, Object> document) throws CouchbaseLiteException {
		if (mDesignDoc == null) return null;

		final Map<String, Object> requestProperties = getRequestProperties();

		String showSrc = "";

		try {
			mContext.enter();

			unregisterFunctions(mShowFunctions);
			registerFunctions(mShowFunctions);

			showSrc = (String) ((Map<String, Object>) mDesignDoc.get("shows")).get(showName);

			// compile the show function and call it
			final Function showFunc = mContext.compileFunction(mScope, showSrc, showName, 1, null);
			final Object showFuncResult = showFunc.call(mContext, mScope, mScope, new Object[] { document, requestProperties });

			Context.exit();

			final String resultString;
			if (showFuncResult instanceof String) {
				resultString = (String) showFuncResult;
			} else { //TODO Check how could we have not a String here
				resultString = mMapper.writeValueAsString(showFuncResult);
			}

			//TODO check why we need StringBuilder here?
			return resultString;
		} catch (EvaluatorException eval) {
			Log.e(Database.TAG, "Javascript syntax error in show function '" + mDesignDoc.get("_id") + "/" + showName + "-" + mDesignDoc.get("_rev") + "':\n" + showSrc, eval);
			throw new CouchbaseLiteException(new Status(Status.INTERNAL_SERVER_ERROR));
		} catch (Exception e) {
			Log.e(Database.TAG, "Javascript error in show function: '" + mDesignDoc.get("_id") + "/" + showName + "-" + mDesignDoc.get("_rev")+ "'\n" + showSrc, e);
			throw new CouchbaseLiteException(new Status(Status.BAD_REQUEST));
		}
	}

	///////////////////////////////////// FunctionContainer
    class JavaScriptFunctionContainer extends ImporterTopLevel implements FunctionContainer {
        private final ObjectMapper mMapper = new ObjectMapper();

        public JavaScriptFunctionContainer(Context cx) {
            super(cx);
        }

		/**
		 * Implements the equivalent of the {@code getRow()} function in list functions
		 *
		 * @return The next object in the current collection.
		 */
		public Object getRow() {
			final WrapFactory wrapper = mContext.getWrapFactory();

            Object row = null;
//            ++mCurrentListIndex;
//            if (mCurrentListIndex < mItems.size()   ) {
//                Object item = mItems.get(mCurrentListIndex);
//                row = wrapper.wrapNewObject(mContext, this, item);
//
//                //Log.d(Database.TAG, mCurrentListIndex + ": " + item.toString() + " -> " + row.toString());
//            } else {
//                row = mContext.getUndefinedValue();
//                //Log.d(Database.TAG, mCurrentListIndex + ": " +  row.toString());
//            }
            Date d1 = new Date();
            if (mItems.size() > 0) {
                Object item = mItems.remove(0);
                row = wrapper.wrapNewObject(mContext, this, item);

                //Log.d(Database.TAG, mCurrentListIndex + ": " + item.toString() + " -> " + row.toString());
            } else {
                row = mContext.getUndefinedValue();
                //Log.d(Database.TAG, mCurrentListIndex + ": " +  row.toString());
            }
            Date d2 = new Date();

            getRowDuration = getRowDuration + (d2.getTime() - d1.getTime());

            return row;
		}

		/**
		 * @inheritDoc
		 */
		public boolean isArray(final Object obj) {
			try {
				final Object result = mContext.jsToJava(obj, List.class);
				return result instanceof List;
			} catch (ClassCastException e) {
				return false;
			}
		}

		/**
		 * @return The object that can be evaluated into a {@link org.mozilla.javascript.Context} object
		 */
		public Object getJSON() {
			return get("JSON", this);
		}

		/**
		 * @inheritDoc
		 */
		public void log(final String msg) {
			Log.i(Database.TAG, msg);
		}

		/**
		 * @inheritDoc
		 */
		public void send(final String contents) {
			mListResponse.append(contents);
		}

		/**
		 * Alias function for {@code JSON.stringify()}
		 *
		 * @return The function definition that can be evaluated into a {@link org.mozilla.javascript.Context} object
		 */
		public String toJSON(final Object obj) {
			return (String) NativeJSON.stringify(mContext, this, obj, null, null);
		}
	}
}
