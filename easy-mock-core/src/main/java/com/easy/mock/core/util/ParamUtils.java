package com.easy.mock.core.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.easy.mock.anno.EasyMock;
import com.easy.mock.anno.ParamFilter;
import com.easy.mock.anno.ParamMock;
import com.easy.mock.core.EasyMockItem;
import com.easy.mock.core.SupportBaseType;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ParamUtils {
    public static Object[] buildParameter(Method method, JSONObject jsonParam) {
        List<EasyMockItem> easyMockItemList = listParameter(method);
        if (easyMockItemList.size() == 0) {
            return null;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] params = new Object[parameterTypes.length];
        for (EasyMockItem easyMockItem : easyMockItemList) {
            int argIndex = NumberUtils.toInt(easyMockItem.getCode().replaceAll("arg", ""), 0);
            if (easyMockItem.getName().contains(".")) {
                if (params[argIndex] == null) {
                    params[argIndex] = new JSONObject();
                }

                JSONObject json = (JSONObject)params[argIndex];
                json.put(StringUtils.substringAfter(easyMockItem.getName(), "."), jsonParam.getObject(easyMockItem.getName(), easyMockItem.getType()));
            } else {
                params[argIndex] = jsonParam.getObject(easyMockItem.getName(), easyMockItem.getType());
            }
        }

        for (int n = 0; n < params.length; n++) {
            Object param = params[n];
            if (param instanceof JSONObject) {
                params[n] = JSON.toJavaObject((JSONObject)param, parameterTypes[n]);
            }
        }

        return params;
    }

    public static Object[] buildParameter(Method method, String jsonParamString) {
        return buildParameter(method, JSONObject.parseObject(jsonParamString));
    }

    public static boolean isSupportMethod(Method method) {
        if ("private".equals(Modifier.toString(method.getModifiers())) || method.getName().contains("access$")) {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (ArrayUtils.isEmpty(parameterTypes)) {
            return true;
        }

        for (Class cla : parameterTypes) {
            if (Collection.class.isAssignableFrom(cla) || Map.class.isAssignableFrom(cla)) {
                return false;
            }

            if (BigDecimal.class.isAssignableFrom(cla)) {
                return false;
            }
        }

        return true;
    }

    public static List<EasyMockItem> listParameter(Method method) {
        List<EasyMockItem> easyMockItemList = new ArrayList<EasyMockItem>();
        if (!isSupportMethod(method)) {
            return easyMockItemList;
        }

        Map<String, EasyMockItem> mapCacheMockItem = new HashMap<String, EasyMockItem>();
        List<MockParameter> parameterList = getParameters(method);

        for (int n = 0; n < parameterList.size(); n++) {
            MockParameter mockParameter = parameterList.get(n);
            String paramName = mockParameter.getName();
            Class paramClass = mockParameter.getType();
            if (!SupportBaseType.isSupported(paramClass)) {
                List<Field> fieldList = ReflectUtils.getAllDeclaredFields(paramClass);
                for (Field field : fieldList) {
                    EasyMockItem easyMockItem = new EasyMockItem();
                    easyMockItem.setCode("arg" + n);
                    easyMockItem.setName(paramClass.getSimpleName() + "." + field.getName());
                    easyMockItem.setType(field.getType());

                    EasyMock fieldDoc = field.getAnnotation(EasyMock.class);
                    if (fieldDoc != null) {
                        if (StringUtils.isNotBlank(fieldDoc.name())) {
                            easyMockItem.setName(paramClass.getSimpleName() + "." + fieldDoc.name());
                        }
                        easyMockItem.setDoc(fieldDoc.doc());
                        easyMockItem.setOrder(fieldDoc.order());
                    }

                    mapCacheMockItem.put(easyMockItem.getCode() + "@" + easyMockItem.getName(), easyMockItem);
                }
            } else {
                EasyMockItem easyMockItem = new EasyMockItem();
                easyMockItem.setCode("arg" + n);
                easyMockItem.setName(StringUtils.defaultIfBlank(paramName, easyMockItem.getCode()));
                easyMockItem.setType(paramClass);
                mapCacheMockItem.put(easyMockItem.getCode(), easyMockItem);
            }
        }

        Set<String> selectedSet = new HashSet<String>();
        // 检查是否有必填参数
        Annotation[][] arrayAnno = method.getParameterAnnotations();
        if (ArrayUtils.isNotEmpty(arrayAnno)) {
            for (int n = 0; n < arrayAnno.length; n++) {
                Class<?> clas = parameterList.get(n).getType();
                boolean isSupportBaseType = SupportBaseType.isSupported(clas);
                for (int i = 0; i < arrayAnno[n].length; i++) {
                    Annotation anno = arrayAnno[n][i];
                    if (!isSupportBaseType && (anno instanceof ParamFilter)) {
                        ParamFilter paramFilter = (ParamFilter)anno;
                        if (ArrayUtils.isNotEmpty(paramFilter.required())) {
                            for (String required : paramFilter.required()) {
                                String cacheKey = "arg" + n + "@" + clas.getSimpleName() + "." + StringUtils.trim(required);
                                EasyMockItem easyMockItem = mapCacheMockItem.get(cacheKey);
                                if (easyMockItem != null) {
                                    easyMockItem.setRequired(true);
                                }
                            }
                        }

                        if (ArrayUtils.isNotEmpty(paramFilter.selected())) {
                            for (String selected : paramFilter.selected()) {
                                selectedSet.add("arg" + n + "@" + clas.getSimpleName() + "." + StringUtils.trim(selected));
                            }

                            Set<Map.Entry<String, EasyMockItem>> mockItemEntrySet = mapCacheMockItem.entrySet();
                            Set<String> removeKeySet = new HashSet<String>();
                            for (Map.Entry<String, EasyMockItem> entry : mockItemEntrySet) {
                                // 过滤非当前类MockItem
                                if (!StringUtils.startsWith(entry.getKey(), "arg" + n + "@" + clas.getSimpleName())) {
                                    continue;
                                }

                                // 过滤非选择MockItem
                                if (!selectedSet.contains(entry.getKey())) {
                                    removeKeySet.add(entry.getKey());
                                }
                            }

                            for (String removeKey : removeKeySet) {
                                mapCacheMockItem.remove(removeKey);
                            }
                        }

                        if (ArrayUtils.isNotEmpty(paramFilter.excluded())) {
                            for (String excluded : paramFilter.excluded()) {
                                String cacheKey = "arg" + n + "@" + clas.getSimpleName() + "." + StringUtils.trim(excluded);
                                mapCacheMockItem.remove(cacheKey);
                            }
                        }
                    } else if (isSupportBaseType && (anno instanceof ParamMock)) {
                        ParamMock paramDoc = (ParamMock) anno;
                        EasyMockItem easyMockItem = mapCacheMockItem.get("arg" + n);
                        easyMockItem.setName(paramDoc.name());
                        easyMockItem.setDoc(paramDoc.doc());
                        easyMockItem.setOrder(paramDoc.order());
                        easyMockItem.setRequired(paramDoc.required());
                    }
                }
            }
        }

        easyMockItemList.addAll(mapCacheMockItem.values());
        Collections.sort(easyMockItemList, new Comparator<EasyMockItem>() {
            @Override
            public int compare(EasyMockItem o1, EasyMockItem o2) {
                int compare = 0;
                if (o1.isRequired() && !o2.isRequired()) {
                    compare = -1;
                } else if (!o1.isRequired() && o2.isRequired()) {
                    compare = 1;
                }

                if (compare == 0) {
                    compare = o1.getCode().compareTo(o2.getCode());
                }

                if (compare == 0) {
                    compare = o1.compareTo(o2);
                }

                return compare;
            }
        });

        return easyMockItemList;
    }

    private static List<MockParameter> getParameters(Method method) {
        List<MockParameter> parameterList = new ArrayList<MockParameter>();
        Class<?>[] arrayClass = method.getParameterTypes();
        if (arrayClass == null || arrayClass.length == 0) {
            return parameterList;
        }

        for (Class<?> clas : arrayClass) {
            MockParameter parameter = new MockParameter();
            parameter.setType(clas);
            parameterList.add(parameter);
        }

        try {
            Method jdk8GetParameters = method.getClass().getMethod("getParameters");
            Object[] parameters = (Object[]) jdk8GetParameters.invoke(method);
            for (int n = 0; n < parameters.length; n++) {
                Object parameter = parameters[n];
                Method getNameMethod = parameter.getClass().getMethod("getName");
                parameterList.get(n).setName((String)getNameMethod.invoke(parameter));
            }
        } catch (Exception e) {
            // Do Nothing
        }

        return parameterList;
    }

    private static class MockParameter {
        private String name;

        private Class<?> type;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Class<?> getType() {
            return type;
        }

        public void setType(Class<?> type) {
            this.type = type;
        }
    }
}
