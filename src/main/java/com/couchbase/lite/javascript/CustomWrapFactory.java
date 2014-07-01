package com.couchbase.lite.javascript;

import org.elasticsearch.script.javascript.support.NativeList;
import org.elasticsearch.script.javascript.support.NativeMap;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            final NativeObject nativeObject = new NativeObject();

            for (Map.Entry<String, Object> entry : ((Map<String, Object>) javaObject).entrySet()) {

                final Class<?> valueClass = (entry.getValue() != null) ? entry.getValue().getClass() : null;

                final String nativeKey = entry.getKey();
                final Object nativeValue = wrap(cx, scope, entry.getValue(), valueClass);

                nativeObject.defineProperty(nativeKey, nativeValue, NativeObject.READONLY);
            }

            return new NativeMap(scope, nativeObject);
        } else if (javaObject instanceof List) {
            final List<Object> copyList = new ArrayList<Object>();

            for (final Object obj : (List<Object>) javaObject) {
                copyList.add(wrapAsJavaObject(cx, scope, obj, obj.getClass()));
            }

            return new NativeList(scope, copyList);
        } else if (javaObject == null) {
            return null;
        }

        return super.wrapAsJavaObject(cx, scope, javaObject, staticType);
    }
}