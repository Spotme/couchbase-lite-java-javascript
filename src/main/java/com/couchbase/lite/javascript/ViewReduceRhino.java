package com.couchbase.lite.javascript;

import com.couchbase.lite.Database;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.util.Log;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
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
		try {
			if (isBuiltIn) {
				if (source.equalsIgnoreCase("_sum")) return sum(keys, values, rereduce);
				else if (source.equalsIgnoreCase("_count"))
					return count(keys, values, rereduce);
				else if (source.equalsIgnoreCase("_stats"))
					return stats(keys, values, rereduce);
			} else {
                org.mozilla.javascript.Context ctx = org.mozilla.javascript.Context.enter();
                try {
                    ctx.setOptimizationLevel(-1);
                    ctx.setWrapFactory(wrapFactory);

                    Scriptable localScope = ctx.newObject(globalScope);
                    localScope.setPrototype(globalScope);
                    localScope.setParentScope(null);

                    Object[] args = new Object[3];

                    args[0] = org.mozilla.javascript.Context.javaToJS(keys, localScope);
                    args[1] = org.mozilla.javascript.Context.javaToJS(values, localScope);
                    args[2] = org.mozilla.javascript.Context.javaToJS(rereduce, localScope);

                    return mReduceFunc.call(ctx, localScope, null, args);

                } catch (org.mozilla.javascript.RhinoException e) {
                    // TODO check couchdb behaviour on error in reduce function
                    return null;
                } finally {
                    org.mozilla.javascript.Context.exit();
                }
			}
		} catch (Exception e) {
			Log.e(Database.TAG, "Error while executing reduce function: " + source, e);
		}

		return null;
	}

	protected Object sum(List<Object> keys, List<Object> values, boolean rereduce) throws Exception {
		double count = 0d;
		for (Object value : values) {
			final double doubleValue = (double) Context.jsToJava(value, Double.TYPE);
			count += doubleValue;
		}
		return count;
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
