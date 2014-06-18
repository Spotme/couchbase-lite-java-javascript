package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.FunctionCompiler;
import com.couchbase.lite.FunctionContainer;
import com.couchbase.lite.router.URLConnection;
import com.couchbase.lite.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A list and show JavaScript function compiler that provides the
 * required JavaScript methods to call these functions.
 */
public class JavaScriptFunctionCompiler implements FunctionCompiler, FunctionContainer {

	// Items output by the view
	protected List<Map<String, Object>> mItems;

	// HTTP request that triggered this call
	protected URLConnection mConnection;

	// thread context
	protected final Context mContext;

	// view collection index for getRow()
	protected int mCurrentListIndex = -1;

	// functions object container
	protected final Scriptable mFunctions;

	// request object
	protected String mRequest;

	// response contents
	protected final StringBuilder mResponse = new StringBuilder();

	// global shared scope with initialized functions
	protected final Scriptable mScope;

	// result of the view this request called on, if any
	protected List<Map<String, Object>> mViewResult = new ArrayList<Map<String, Object>>();

	// JS query server functions
	protected static final List<String> mGlobalFunctions = Arrays.asList("isArray", "log", "sum", "start");
	protected static final List<String> mMapFunctions = Arrays.asList("require", "emit");
	protected static final List<String> mListFunctions = Arrays.asList("require", "getRow", "provides", "registerType");
	protected static final List<String> mShowFunctions = Arrays.asList("require", "provides", "registerType");
	protected static final List<String> mReduceFunctions = Arrays.asList();

	protected static final List<List<String>> mAllFunctions = Arrays.asList(mMapFunctions, mListFunctions, mShowFunctions, mReduceFunctions);

	/**
	 * Create a new Context and
	 */
	public JavaScriptFunctionCompiler() {
		mContext = Context.enter();

		mScope = mContext.initStandardObjects();
		mFunctions = mContext.newObject(mScope);

		registerFunctions(mGlobalFunctions);

		mContext.exit();
	}

	/**
	 * Builds the request object from the connection, which is then passed into list and show functions.
	 */
	public void buildRequestObject() {
		final String requestMethod = mConnection.getRequestMethod();


		return;
//{
//	"info": {
//	"db_name": "test_suite_db",
//			"doc_count": 11,
//			"doc_del_count": 0,
//			"update_seq": 11,
//			"purge_seq": 0,
//			"compact_running": false,
//			"disk_size": 4930,
//			"instance_start_time": "1250046852578425",
//			"disk_format_version": 4
//},
//	"method": "GET",
//		"path": [
//	"test_suite_db",
//			"_design",
//			"lists",
//			"_list",
//			"basicJSON",
//			"basicView"
//	],
//	"query": {
//	"foo": "bar"
//},
//	"headers": {
//	"Accept": "text/html,application/xhtml+xml ,application/xml;q=0.9,*/*;q=0.8",
//			"Accept-Charset": "ISO-8859-1,utf-8;q=0.7,*;q=0.7",
//			"Accept-Encoding": "gzip,deflate",
//			"Accept-Language": "en-us,en;q=0.5",
//			"Connection": "keep-alive",
//			"Cookie": "_x=95252s.sd25; AuthSession=",
//			"Host": "127.0.0.1:5984",
//			"Keep-Alive": "300",
//			"Referer": "http://127.0.0.1:5984/_utils/couch_tests.html?script/couch_tests.js",
//			"User-Agent": "Mozilla/5.0 Gecko/20090729 Firefox/3.5.2"
//},
//	"cookie": {
//	"_x": "95252s.sd25",
//			"AuthSession": ""
//}
//}

	}

	/**
	 * Registers a Java method within the current JavaScript context.
	 * Parameters of the Java method
	 *
	 * @param name The name of the method to define
	 * @param functionContainer The container object where functions are defined
	 */
	protected void registerFunction(final String name, final Class<?> functionContainer) {
		try {
			for (final Method method : functionContainer.getMethods()) {
				if (method.getName().equals(name)) {
					final FunctionObject fn = new FunctionObject(name, method, mFunctions);
					mFunctions.put(name, mFunctions, fn);

					break;
				}
			}
		} catch (SecurityException e) {
			Log.w(Database.TAG, String.format("Unable to get method '%s' from class '%s'.", name, functionContainer.getSimpleName()));
		}
	}

	protected void registerFunctions(final List<String> functions) {
		if (functions == null) return;

		for (final String fn : functions) {
			registerFunction(fn, this.getClass());
		}
	}

	/**
	 * Unregister a function from the current global scope
	 *
	 * @param name      The function name to remove
	 */
	protected void unregisterFunction(final String name) {
		try {
			if (mFunctions.has(name, mFunctions)) mFunctions.delete(name);
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
	 * @param conn The connection where to set the response
	 */
	public void setConnection(final URLConnection conn) {
		mConnection = conn;
		buildRequestObject();
	}

	/**
	 * @param result The result of the view where this list function was invoked
	 */
	public void setViewResult(final Map<String, Object> result) {
		mItems = (List<Map<String, Object>>) result.get("rows");
	}

	///////////////////////////////////// FunctionCompiler

	/**
	 * @inheritDoc
	 */
	public List<Map<String, Object>> list(final Map<String, Object> head, final Map<String, Object> request) {
		unregisterFunctions(mListFunctions);
		registerFunctions(mListFunctions);



		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Object show(Map<String, Object> document, Map<String, Object> request) {
		unregisterFunctions(mShowFunctions);
		registerFunctions(mShowFunctions);



		return null;
	}

	///////////////////////////////////// FunctionContainer

	/**
	 * Implements the equivalent of the {@code getRow()} function in list functions
	 *
	 * @return The next object in the current collection
	 */
	public Object getRow() {
		return mViewResult.size() < ++mCurrentListIndex
				? mViewResult.get(mCurrentListIndex)
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
