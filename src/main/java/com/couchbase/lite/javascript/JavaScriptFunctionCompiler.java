package com.couchbase.lite.javascript;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.FunctionCompiler;
import com.couchbase.lite.FunctionContainer;
import com.couchbase.lite.Status;
import com.couchbase.lite.router.URLConnection;
import com.couchbase.lite.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.script.javascript.support.NativeList;
import org.elasticsearch.script.javascript.support.NativeMap;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
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

	// Function container
	protected JavaScriptFunctionContainer mFunctions = new JavaScriptFunctionContainer();

	protected final ObjectMapper mMapper = new ObjectMapper();

	// request object
	protected Map<String, Object> mRequestProperties;

	// response contents
	protected final StringBuilder mResponse = new StringBuilder();

	// global shared scope with initialized functions
	protected final Scriptable mScope;

	// result of the view this request called on, if any
	protected List<Map<String, Object>> mViewResult = new ArrayList<Map<String, Object>>();

	// JS query server functions
	protected static final List<String> mGlobalFunctions = Arrays.asList("isArray", "log", "sum", "start");
	protected static final List<String> mMapFunctions = Arrays.asList("require", "emit");
	protected static final List<String> mListFunctions = Arrays.asList("require", "getRow", "send"); //, "provides", "registerType");
	protected static final List<String> mShowFunctions = Arrays.asList("require"); //, "provides", "registerType");
	protected static final List<String> mReduceFunctions = Arrays.asList();

	protected static final List<List<String>> mAllFunctions = Arrays.asList(mMapFunctions, mListFunctions, mShowFunctions, mReduceFunctions);

	/**
	 * Create a new Context and
	 */
	public JavaScriptFunctionCompiler() {
		mContext = Context.enter();

		// Android dex won't allow us to create our own classes
		mContext.setOptimizationLevel(-1);
		mContext.setWrapFactory(new CustomWrapFactory());

		mScope = mContext.initStandardObjects();
		mFunctions.setParentScope(mScope);

		registerFunctions(mGlobalFunctions);

		mMapper.getJsonFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);

		mContext.exit();
	}

	/**
	 * Builds the request object from the connection, which is then passed into list and show functions.
	 */
	public void buildRequestObject() {

		final String requestMethod = mConnection.getRequestMethod();
		final String[] path = mConnection.getURL().getPath().split("/");

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
					final FunctionObject fn = new FunctionObject(name, method, mFunctions);
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

		final Map<String, Object> requestProperties = getRequestProperties();

		String listSrc = "";

		try {
			mContext.enter();

			unregisterFunctions(mListFunctions);
			registerFunctions(mListFunctions);

			listSrc = (String) ((Map<String, Object>) mDesignDoc.get("lists")).get(listName);

			// compile the list function and call it
			final Function listFunc = mContext.compileFunction(mScope, listSrc, listName, 1, null);
			mContext.callFunctionWithContinuations(listFunc, mScope, new Object[] { head, requestProperties });

			Context.exit();

			return mResponse.toString();
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
			final Object result = showFunc.call(mContext, mScope, mScope, new Object[] { document, requestProperties });

			Context.exit();

			return mResponse.append(mMapper.writeValueAsString(result)).toString();
		} catch (EvaluatorException eval) {
			Log.e(Database.TAG, "Javascript syntax error in list function:\n" + showSrc, eval);
			throw new CouchbaseLiteException(new Status(Status.INTERNAL_SERVER_ERROR));
		} catch (Exception e) {
			Log.e(Database.TAG, "Javascript error in list function:\n" + showSrc, e);
			throw new CouchbaseLiteException(new Status(Status.BAD_REQUEST));
		}
	}

	///////////////////////////////////// FunctionContainer
	class JavaScriptFunctionContainer extends ScriptableObject implements FunctionContainer {

		@Override
		public String getClassName() {
			return JavaScriptFunctionContainer.class.getSimpleName();
		}

		/**
		 * Implements the equivalent of the {@code getRow()} function in list functions
		 *
		 * @return The next object in the current collection.
		 */
		public Object getRow() {
			return ++mCurrentListIndex < mItems.size()
					? mItems.get(mCurrentListIndex)
					: null;
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
			return mScope.get("JSON", mScope);
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
		public String require(final String path) {
			// TODO implement
			return "";
		}

		/**
		 * @inheritDoc
		 */
		public void send(final String contents) {
			mResponse.append(contents);
			mResponse.append('\n');
		}

		/**
		 * Alias function for {@code JSON.stringify()}
		 *
		 * @return The function definition that can be evaluated into a {@link org.mozilla.javascript.Context} object
		 */
		public String toJSON(final Object obj) {
			return (String) NativeJSON.stringify(mContext, mScope, obj, null, null);
		}
	}

	/**
	 * Wrap Factory for Rhino Script Engine
	 */
	class CustomWrapFactory extends WrapFactory {

		public CustomWrapFactory() {
			setJavaPrimitiveWrap(false); // RingoJS does that..., claims its annoying...
		}

		@Override
		public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
			if (javaObject instanceof Map) {
				return new NativeMap(scope, (Map) javaObject);
			} else if (javaObject instanceof List) {
				return new NativeList(scope, (List<Object>)javaObject);
			}

			return super.wrapAsJavaObject(cx, scope, javaObject, staticType);
		}
	}
}
