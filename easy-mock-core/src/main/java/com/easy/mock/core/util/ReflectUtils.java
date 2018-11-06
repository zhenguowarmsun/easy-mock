package com.easy.mock.core.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectUtils {

    private static final Map<Class, List<Field>> CACHE = new ConcurrentHashMap<Class, List<Field>>();

    public static List<Field> getAllDeclaredFields(Class clas) {
        if (CACHE.containsKey(clas)) {
            return CACHE.get(clas);
        }

        Class tmpClass = clas;
        List<Field> fieldList = new ArrayList<Field>();
        Set<String> fieldNameSet = new HashSet<String>();

        while (tmpClass != null && !tmpClass.equals(Object.class)) {
            Field[] fields = tmpClass.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                if (fieldNameSet.contains(field.getName())) {
                    continue;
                }

                fieldList.add(field);
                fieldNameSet.add(field.getName());
            }

            tmpClass = tmpClass.getSuperclass();
        }

        CACHE.put(clas, fieldList);

        return fieldList;
    }
}
