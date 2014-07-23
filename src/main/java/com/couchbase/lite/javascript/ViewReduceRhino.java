package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.util.Log;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewReduceRhino implements Reducer {

	private final Scriptable globalScope;
	private final WrapFactory wrapFactory;
	private final String source;

	private Function mReduceFunc = null;

	private boolean isBuiltIn = false;

	private static final ObjectMapper mapper = new ObjectMapper();

	private static final List<String> builtIn = new ArrayList<String>() {{
		add("_sum");
		add("_count");
		add("_stats");
	}};

	public ViewReduceRhino(String src) {
		final Context ctx = Context.enter();

		// Android dex won't allow us to create our own classes
		ctx.setOptimizationLevel(-1);

		mapper.getFactory().enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);

		globalScope = new MapReduceFunctionContainer(Context.getCurrentContext(), true);
		wrapFactory = new CustomWrapFactory(globalScope);
		source = src;

		ctx.setWrapFactory(wrapFactory);

		try {
			if (!builtIn.contains(src)) {
				// register the reduce function
				mReduceFunc = ctx.compileFunction(globalScope, src, "reduce", 1, null);
			} else {
				isBuiltIn = true;
			}
		} finally {
			Context.exit();
		}
	}

	@Override
	public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
		Context ctx = Context.enter();
		try {
			ctx.setOptimizationLevel(-1);
			ctx.setWrapFactory(wrapFactory);

			if (isBuiltIn) {
				if (source.equalsIgnoreCase("_sum")) return sum(keys, values, rereduce);
				else if (source.equalsIgnoreCase("_count"))
					return count(keys, values, rereduce);
				else if (source.equalsIgnoreCase("_stats"))
					return stats(keys, values, rereduce);
			} else {
				Object[] functionArgs = { // execute the reduce func with the args
						wrapFactory.wrapNewObject(ctx, globalScope, keys),
						wrapFactory.wrapNewObject(ctx, globalScope, values),
						wrapFactory.wrapNewObject(ctx, globalScope, rereduce)
				};

				return mReduceFunc.call(ctx, globalScope, globalScope, functionArgs);
			}
		} catch (Exception e) {
			Log.e(Database.TAG, "Error while executing reduce function: " + source, e);
		} finally {
			Context.exit();
		}

		return null;
	}

	protected Object sum(List<Object> keys, List<Object> values, boolean rereduce) throws Exception {
		// not really sure?
		return values.size();
	}

	protected Object count(List<Object> keys, List<Object> values, boolean rereduce) throws Exception {
		return (rereduce) ? sum(keys, values, rereduce) : values.size();
	}

	protected Object stats(List<Object> keys, List<Object> values, boolean rereduce) throws Exception {
		final Map<String, Object> props = new HashMap<String, Object>();

		props.put("sum", sum(keys, values, rereduce));
		props.put("count", count(keys, values, rereduce));
		props.put("min", 0);
		props.put("max", 1);
		props.put("sumsqr", 0);

		return mapper.writeValueAsString(props);
	}
}
