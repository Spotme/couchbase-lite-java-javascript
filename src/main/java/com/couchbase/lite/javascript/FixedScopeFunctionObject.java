package com.couchbase.lite.javascript;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;

import java.lang.reflect.Member;

/**
 * Handles functions with a fixed scope (the top-level one)
 */
public class FixedScopeFunctionObject extends FunctionObject {

	private final Scriptable mFixedScope;

	public FixedScopeFunctionObject(String name, Member methodOrConstructor, Scriptable parentScope, Scriptable fixedScope) {
		super(name, methodOrConstructor, parentScope);

		mFixedScope = fixedScope;
	}

	@Override
	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		return super.call(cx, scope, mFixedScope, args);
	}
}
