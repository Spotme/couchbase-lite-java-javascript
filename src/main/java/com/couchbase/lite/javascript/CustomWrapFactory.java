package com.couchbase.lite.javascript;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
//import org.mozilla.javascript.NativeJavaArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;


import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

//class WowNativeJavaArray extends NativeJavaArray
//{
//    public WowNativeJavaArray(Scriptable scope, Object array) {
//        super(scope, array);
//    }
//
//    @Override
//    public Object get(String id, Scriptable start) {
//        if (id.equals("toJSON")) {
//            return "xxx";
//        }
//        return super.get(id, start);
//    }
//}


/**
 * Wrap Factory for Rhino Script Engine
 */
class CustomWrapFactory extends WrapFactory {

    private final Scriptable mScope;

    public CustomWrapFactory(final Scriptable scope) {
        mScope = scope;
        setJavaPrimitiveWrap(false); // RingoJS does that..., claims its annoying...
    }

    @Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
        if (javaObject instanceof Map) {
            final NativeObject nativeObject = new NativeObject();

            for (Map.Entry<String, Object> entry : ((Map<String, Object>) javaObject).entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();
                final Class<?> valueClass = (value != null) ? value.getClass() : null;

                final String nativeKey = key;//entry.getKey();
                final Object nativeValue = wrap(cx, scope, value, valueClass);

                nativeObject.defineProperty(nativeKey, nativeValue, NativeObject.READONLY);
            }

            return nativeObject;
        } else if (javaObject instanceof List || javaObject instanceof Array) {
            final NativeArray copyList = new NativeArray(((List) javaObject).size());

            int i = 0;
            for (final Object obj : (List<Object>) javaObject) {
                final Class klass = obj != null ? obj.getClass() : null;
                copyList.put(i, copyList, wrap(cx, scope, obj, klass));
                i++;
            }

            return copyList;
        } /*else if (javaObject instanceof String || javaObject instanceof Number || javaObject instanceof Boolean) {
            return (Scriptable)Context.javaToJS(javaObject, scope);
        } else if (javaObject == null) {
            return null;
        }*/

        final Scriptable ret = super.wrapAsJavaObject(cx, scope, javaObject, staticType);
        return ret;
    }
}

