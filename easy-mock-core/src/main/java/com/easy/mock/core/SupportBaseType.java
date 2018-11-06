package com.easy.mock.core;

import java.util.Date;

public enum SupportBaseType {
    em_boolean(Boolean.TYPE),
    em_Boolean(Boolean.class),
    em_int(Integer.TYPE),
    em_Integer(Integer.class),
    em_long(Long.TYPE),
    em_Long(Long.class),
    em_double(Double.TYPE),
    em_Double(Double.class),
    em_float(Float.TYPE),
    em_Float(Float.class),
    em_String(String.class),
    em_Date(Date.class),
    em_Enum(Enum.class);

    private Class supportParam;

    SupportBaseType(Class supportParam) {
        this.supportParam = supportParam;
    }

    public static boolean isSupported(Class paramClass) {
        if (paramClass == null) {
            return false;
        }

        SupportBaseType[] params = SupportBaseType.values();
        for (SupportBaseType param : params) {
            if (param.getSupportParam().isAssignableFrom(paramClass)) {
                return true;
            }
        }

        return false;
    }

    public static SupportBaseType parseOf(Class paramClass) {
        if (paramClass == null) {
            return null;
        }

        SupportBaseType[] params = SupportBaseType.values();
        for (SupportBaseType param : params) {
            if (param.getSupportParam().isAssignableFrom(paramClass)) {
                return param;
            }
        }

        return null;
    }

    public Class getSupportParam() {
        return supportParam;
    }
}
