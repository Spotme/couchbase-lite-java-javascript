package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.FunctionContainer;
import com.couchbase.lite.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.NativeJSON;

import java.util.List;

public class MapReduceFunctionContainer extends ImporterTopLevel implements FunctionContainer {
	private final Context mContext;

	public MapReduceFunctionContainer(Context cx, boolean sealed) {
		super(cx, sealed);

		mContext = cx;
	}

	/**
	 * Implements the equivalent of the {@code getRow()} function in list functions
	 *
	 * @return The next object in the current collection.
	 */
	public Object getRow() {
		throw new RuntimeException("Illegal method call");
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
		throw new RuntimeException("Illegal method call");
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