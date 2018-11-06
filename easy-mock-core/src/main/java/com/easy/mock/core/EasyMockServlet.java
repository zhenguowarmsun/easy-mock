package com.easy.mock.core;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.easy.mock.anno.EasyMock;
import com.easy.mock.anno.MethodFilter;
import com.easy.mock.core.util.ParamUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet(name = "EasyMockServlet",
        value = {"/easy/mock",
                "/easy/mock/",
                "/easy/mock/easyui/jquery.min.js",
                "/easy/mock/easyui/jquery.easyui.min.js",
                "/easy/mock/easyui/easyui-lang-zh_CN.js",
                "/easy/mock/easyui/images/layout_button_up.gif",
                "/easy/mock/easyui/images/panel_title.png",
                "/easy/mock/easyui/images/button_span_bg.gif",
                "/easy/mock/easyui/images/button_a_bg.gif",
                "/easy/mock/easyui/images/blank.gif",
                "/easy/mock/easyui/images/layout_arrows.png",
                "/easy/mock/easyui/images/tree_icons.png",
                "/easy/mock/easyui/images/loading.gif",
                "/easy/mock/easyui/images/panel_tools.png",
                "/easy/mock/easyui/images/validatebox_warning.png",
                "/easy/mock/easyui/images/calendar_arrows.png",
                "/easy/mock/easyui/images/combo_arrow.png",
                "/easy/mock/easyui/images/datagrid_icons.png",
                "/easy/mock/easyui/images/datebox_arrow.png",
                "/easy/mock/easyui/images/spinner_arrows.png",
                "/easy/mock/easyui/images/debug.png",
                "/easy/mock/easyui/images/favicon.png",
                "/easy/mock/easyui/images/class.png",
                "/easy/mock/easyui/images/method.png",
                "/easy/mock/easyui/images/required.png",
                "/easy/mock/easyui/images/unrequired.png",
                "/easy/mock/easyui/images/collapse_all.png",
                "/easy/mock/easyui/images/expand_all.png",
                "/easy/mock/easyui/easyui.css",
                "/easy/mock/easyui/icon.css",
                "/easy/mock/json/classes",
                "/easy/mock/json/methods",
                "/easy/mock/json/parameters",
                "/easy/mock/json/mock"})
public class EasyMockServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(EasyMockServlet.class);

    private static ApplicationContext applicationContext;

    private static final Map<String, Object> MAP_EASY_MOCK = new HashMap<String, Object>();
    private static final Map<String, Class<?>> MAP_EASY_MOCK_REAL_CLASS = new HashMap<String, Class<?>>();
    private static final List<EasyMockItem> CACHE_CALSS = new ArrayList<EasyMockItem>();
    private static final Map<String, List<EasyMockItem>> CACHE_METHODS = new ConcurrentHashMap<String, List<EasyMockItem>>();
    private static final Map<String, List<EasyMockItem>> CACHE_PARAMETERS = new ConcurrentHashMap<String, List<EasyMockItem>>();
    private static final Map<String, Method> CACHE_METHOD_OBJS = new ConcurrentHashMap<String, Method>();

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext servletContext = this.getServletContext();
        applicationContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);

        MAP_EASY_MOCK.putAll(applicationContext.getBeansWithAnnotation(EasyMock.class));
        Set<Map.Entry<String, Object>> entrySet = MAP_EASY_MOCK.entrySet();
        for (Map.Entry<String, Object> entry : entrySet) {
            Class<?> clas = entry.getValue().getClass();
            if (clas.getName().contains("$")) {
                String realClassName = StringUtils.substringBefore(clas.getName(), "$");
                try {
                    clas = Class.forName(realClassName);
                } catch (ClassNotFoundException e) {
                    logger.error("EasyMockMessage error message:", e);
                }
            }

            MethodFilter methodFilter = clas.getAnnotation(MethodFilter.class);
            boolean enableExtend = methodFilter == null || methodFilter.enableExtend();
            String[] selectedMethods = methodFilter != null ? methodFilter.selected() : new String[]{};
            String[] excludedMethods = methodFilter != null ? methodFilter.excluded() : new String[]{};
            List<String> selectedMethodsList = Arrays.asList(selectedMethods);
            List<String> excludedMethodsList = Arrays.asList(excludedMethods);
            //
            Method[] methods = enableExtend ? clas.getMethods() : clas.getDeclaredMethods();
            Method[] baseMethods = Object.class.getMethods();
            Set<String> baseMethodSet = new HashSet<String>(baseMethods.length);
            for (Method method : baseMethods) {
                baseMethodSet.add(method.getName());
            }

            Set<String> setMethods = new HashSet<String>();
            List<EasyMockItem> methodEasyMockItemList = new ArrayList<EasyMockItem>();
            for (Method method : methods) {
                String methodName = method.getName();
                String methodKey = clas.getName() + "." + method.getName();
                //非可支持方法及Object内方法，自动忽略
                if (!ParamUtils.isSupportMethod(method) || baseMethodSet.contains(methodName)) {
                    continue;
                }
                // 选择方法不为空，且不包含当前方法，自动忽略
                if (selectedMethodsList.size() > 0 && !selectedMethodsList.contains(methodName)) {
                    continue;
                }
                // 要排除方法，自动忽略
                if (excludedMethodsList.contains(methodName)) {
                    continue;
                }

                // 重载方法识别
                int index = 1;
                while (setMethods.contains(methodKey)) {
                    methodKey = clas.getName() + "." + method.getName() + String.valueOf(index);
                    methodName = method.getName() + String.valueOf(index);
                    index++;
                }

                setMethods.add(methodKey);

                EasyMockItem easyMockItem = new EasyMockItem();
                easyMockItem.setCode(methodName);
                easyMockItem.setName(methodName);
                EasyMock easyMock = method.getAnnotation(EasyMock.class);
                if (easyMock != null) {
                    if (StringUtils.isNotBlank(easyMock.name())) {
                        easyMockItem.setName(easyMock.name());
                    }
                    easyMockItem.setDoc(easyMock.doc());
                    easyMockItem.setOrder(easyMock.order());
                }

                methodEasyMockItemList.add(easyMockItem);
                // 初始化参数start
                CACHE_PARAMETERS.put(methodKey, ParamUtils.listParameter(method));
                CACHE_METHOD_OBJS.put(methodKey, method);
            }

            // 过滤后方法为空，该类忽略
            if (methodEasyMockItemList.size() == 0) {
                MAP_EASY_MOCK.remove(entry.getKey());
                continue;
            }

            // 加入类缓存
            EasyMockItem easyMockItem = new EasyMockItem();
            String text = clas.getSimpleName();
            easyMockItem.setCode(entry.getKey());
            easyMockItem.setName(text);
            EasyMock classDoc = clas.getAnnotation(EasyMock.class);
            if (classDoc != null) {
                if (StringUtils.isNotBlank(classDoc.name())) {
                    easyMockItem.setName(classDoc.name());
                }
                easyMockItem.setOrder(classDoc.order());
                easyMockItem.setDoc(classDoc.doc());
            }

            if (StringUtils.endsWithIgnoreCase(easyMockItem.getName(), "impl")) {
                easyMockItem.setName(StringUtils.removeEndIgnoreCase(easyMockItem.getName(), "impl"));
            }

            CACHE_CALSS.add(easyMockItem);
            MAP_EASY_MOCK_REAL_CLASS.put(entry.getKey(), clas);
            Collections.sort(methodEasyMockItemList);
            CACHE_METHODS.put(clas.getName(), methodEasyMockItemList);
        }

        Collections.sort(CACHE_CALSS);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("utf-8");
        String url = req.getRequestURI();
        String filterUrl = url.replaceAll("\\W", "").toLowerCase();
        try {
            if ("easymock".equals(filterUrl)) {
                if (!url.endsWith("/")) {
                    resp.sendRedirect(url + "/");
                }
                IOUtils.copy(getInputStream("/html/mock.html"), resp.getOutputStream());
            } else if (url.startsWith("/easy/mock/easyui/")) {
                String suffix = StringUtils.substringAfter(url, "/easy/mock/easyui/");
                if (url.toLowerCase().endsWith(".css")) {
                    resp.setHeader("Content-Type", "text/css");
                }
                IOUtils.copy(getInputStream("/html/easyui/" + suffix), resp.getOutputStream());
            } else if (url.startsWith("/easy/mock/json/classes")) {
                classes(resp);
            } else if (url.startsWith("/easy/mock/json/methods")) {
                methods(resp, req.getParameter("beanName"));
            } else if (url.startsWith("/easy/mock/json/parameters")) {
                parameters(resp, req.getParameter("beanName"), req.getParameter("methodName"));
            } else if (url.startsWith("/easy/mock/json/mock")) {
                mock(resp, req.getParameter("beanName"), req.getParameter("methodName"), req.getParameter("mockParam"));
            }
        } catch (Exception ex) {
            logger.error("EasyMockMessage error message:" + url, ex);
        }
    }

    private InputStream getInputStream(String source) throws IOException {
        String jarPath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
        URL url = new URL("jar:file:" + jarPath + "!" + source);

        return url.openStream();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    /**
     * Mock类列表
     *
     * @param response
     */
    public void classes(HttpServletResponse response) {
        writeMessage(response, JSONArray.toJSONString(buildEasyUIViewClasses(CACHE_CALSS)));
    }

    /**
     * 构建EasyUI视图数据对象
     */
    private List<JSONObject> buildEasyUIViewClasses(List<EasyMockItem> easyMockItemList) {
        List<JSONObject> classList = new ArrayList<JSONObject>(easyMockItemList.size());
        for (int n = 0; n < easyMockItemList.size(); n++) {
            EasyMockItem easyMockItem = easyMockItemList.get(n);
            JSONObject mapClass = new JSONObject();
            mapClass.put("id", easyMockItem.getCode());
            mapClass.put("state", "closed");
            mapClass.put("iconCls", "icon-class");
            String text = easyMockItem.getName();
            if (StringUtils.isNotBlank(easyMockItem.getDoc())) {
                text += "(" + easyMockItem.getDoc() + ")";
            }

            mapClass.put("text", text);
            if (n == 0) {
                mapClass.put("checked", true);
            }
            classList.add(mapClass);
        }

        return classList;
    }

    /**
     * Mock类方法列表
     *
     * @param response
     * @param beanName
     */
    public void methods(HttpServletResponse response, String beanName) {
        if (StringUtils.isBlank(beanName)) {
            throw new RuntimeException("beanName不能为空");
        }

        if (!MAP_EASY_MOCK.containsKey(beanName)) {
            throw new RuntimeException("beanName[" + beanName + "]不存在对应实例");
        }

        Class mockServiceClas = MAP_EASY_MOCK_REAL_CLASS.get(beanName);
        List<EasyMockItem> easyMockItemList = CACHE_METHODS.get(mockServiceClas.getName());
        if (easyMockItemList == null || easyMockItemList.size() == 0) {
            writeMessage(response, new JSONArray().toJSONString());
            return;
        }

        try {
            writeMessage(response, JSONArray.toJSONString(buildEasyUIViewMethods(beanName, easyMockItemList)));
        } catch (Exception ex) {
            buildEasyUIViewErrorMessage(response, ex);
        }
    }

    /**
     * 构建EasyUI视图数据对象
     */
    private List<Map<String, String>> buildEasyUIViewMethods(String beanName, List<EasyMockItem> easyMockItemList) {
        List<Map<String, String>> methodList = new ArrayList<Map<String, String>>(easyMockItemList.size());
        for (EasyMockItem easyMockItem : easyMockItemList) {
            Map<String, String> mapMethod = new TreeMap<String, String>();
            JSONObject attributes = new JSONObject();
            String text = easyMockItem.getName();
            attributes.put("beanName", beanName);
            mapMethod.put("id", easyMockItem.getCode());
            mapMethod.put("attributes", attributes.toJSONString());
            if (StringUtils.isNotBlank(easyMockItem.getDoc())) {
                text += "(" + easyMockItem.getDoc() + ")";
            }

            mapMethod.put("iconCls", "icon-method");
            mapMethod.put("text", text);
            methodList.add(mapMethod);
        }

        return methodList;
    }

    /**
     * 获取参数列表
     */
    public void parameters(HttpServletResponse response, String beanName, String methodName) {
        if (StringUtils.isBlank(beanName)) {
            throw new RuntimeException("beanName不能为空");
        }

        if (!MAP_EASY_MOCK.containsKey(beanName)) {
            throw new RuntimeException("beanName[" + beanName + "]不存在对应实例");
        }

        if (StringUtils.isBlank(methodName)) {
            throw new RuntimeException("methodName不能为空");
        }

        Class mockServiceClas = MAP_EASY_MOCK_REAL_CLASS.get(beanName);
        List<EasyMockItem> easyMockItemList = CACHE_PARAMETERS.get(mockServiceClas.getName() + "." + methodName);
        if (easyMockItemList == null || easyMockItemList.size() == 0) {
            writeMessage(response, new JSONArray().toJSONString());
            return;
        }

        try {
            writeMessage(response, JSONArray.toJSONString(buildEasyUIViewParameters(beanName, methodName, easyMockItemList)));
        } catch (Exception ex) {
            buildEasyUIViewErrorMessage(response, ex);
        }
    }

    /**
     * 构建EasyUI视图数据对象
     */
    private List<JSONObject> buildEasyUIViewParameters(String beanName, String methodName, List<EasyMockItem> easyMockItemList) {
        List<JSONObject> parameterList = new ArrayList<JSONObject>();
        for (EasyMockItem easyMockItem : easyMockItemList) {
            if (!SupportBaseType.isSupported(easyMockItem.getType())) {
                continue;
            }

            JSONObject jsonParamter = new JSONObject();
            jsonParamter.put("required", easyMockItem.isRequired());
            jsonParamter.put("beanName", beanName);
            jsonParamter.put("methodName", methodName);
            jsonParamter.put("parameterType", easyMockItem.getType().getName());

            // 编辑器
            JSONObject jsonEditor = new JSONObject();
            JSONObject jsonEditorOptions = new JSONObject();

            if (isNumberType(easyMockItem.getType())) {
                jsonEditor.put("type", "numberbox");
            } else if (Date.class.isAssignableFrom(easyMockItem.getType())) {
                jsonEditor.put("type", "datetimebox");
            } else if (Boolean.class.isAssignableFrom(easyMockItem.getType()) || Boolean.TYPE.isAssignableFrom(easyMockItem.getType())) {
                jsonEditor.put("type", "combobox");
                // 选择器
                JSONArray jsonArraySelect = new JSONArray();
                JSONObject jsonSelectTrue = new JSONObject();
                jsonSelectTrue.put("value", "true");
                jsonSelectTrue.put("text", "true");
                JSONObject jsonSelectFalse = new JSONObject();
                jsonSelectFalse.put("value", "false");
                jsonSelectFalse.put("text", "false");
                jsonArraySelect.add(jsonSelectTrue);
                jsonArraySelect.add(jsonSelectFalse);
                //
                jsonEditorOptions.put("valueField", "value");
                jsonEditorOptions.put("textField", "text");
                jsonEditorOptions.put("data", jsonArraySelect);
                jsonEditor.put("options", jsonEditorOptions);
            } else if (easyMockItem.getType().isEnum()) {
                JSONArray jsonArraySelect = new JSONArray();
                Object[] enums = easyMockItem.getType().getEnumConstants();
                for (Object obj : enums) {
                    JSONObject jsonSelect = new JSONObject();
                    jsonSelect.put("value", obj);
                    jsonSelect.put("text", obj);
                    jsonArraySelect.add(jsonSelect);
                }

                jsonEditorOptions.put("valueField", "value");
                jsonEditorOptions.put("textField", "text");
                jsonEditorOptions.put("data", jsonArraySelect);
                jsonEditor.put("options", jsonEditorOptions);
                jsonEditor.put("type", "combobox");
            } else {
                jsonEditor.put("type", "textbox");
            }

            if (easyMockItem.isRequired()) {
                jsonEditorOptions.put("required", true);
                jsonEditor.put("options", jsonEditorOptions);
                jsonParamter.put("required", true);
            }

            jsonParamter.put("editor", jsonEditor);
            //
            String text = easyMockItem.getName();
            if (StringUtils.isNotBlank(easyMockItem.getDoc())) {
                text += "(" + easyMockItem.getDoc() + ")";
                jsonParamter.put("doc", easyMockItem.getDoc());
            }

            jsonParamter.put("fieldName", easyMockItem.getName());
            jsonParamter.put("name", text);
            parameterList.add(jsonParamter);
        }

        return parameterList;
    }

    /**
     * 执行Mock
     */
    public void mock(HttpServletResponse response, String beanName, String methodName, String mockParam) {
        if (StringUtils.isBlank(beanName)) {
            throw new RuntimeException("beanName不能为空");
        }

        if (!MAP_EASY_MOCK.containsKey(beanName)) {
            throw new RuntimeException("beanName[" + beanName + "]不存在对应实例");
        }

        if (StringUtils.isBlank(methodName)) {
            throw new RuntimeException("methodName不能为空");
        }

        try {
            Object mockService = MAP_EASY_MOCK.get(beanName);
            Class mockServiceClas = MAP_EASY_MOCK_REAL_CLASS.get(beanName);
            String methodKey = mockServiceClas.getName() + "." + methodName;
            if (!CACHE_METHOD_OBJS.containsKey(methodKey)) {
                buildEasyUIViewErrorMessage(response, "方法不存在");
                return;
            }

            Method requireMethod = CACHE_METHOD_OBJS.get(methodKey);
            if (requireMethod == null) {
                buildEasyUIViewErrorMessage(response, "方法不存在");
                return;
            }

            Object objResult = requireMethod.invoke(mockService, ParamUtils.buildParameter(requireMethod, mockParam));
            if (requireMethod.getReturnType().getName().equals("void")) {
                JSONObject success = new JSONObject();
                success.put("isSuccess", true);
                success.put("message", "执行成功");
                objResult = success;
            } else if (SupportBaseType.isSupported(requireMethod.getReturnType())) {
                JSONObject json = new JSONObject();
                json.put("result", objResult);
                objResult = json;
            }


            writeMessage(response, buildEasyUIViewReturnResult(objResult).toJSONString());
        } catch (Throwable ex) {
            logger.error("EasyMockMessage error:Mock方法执行异常beanName:" + beanName + ",methodName:" + methodName + ",mockParam:" + mockParam, ex);
            buildEasyUIViewErrorMessage(response, ex);
        }
    }

    /**
     * 构建EasyUI视图数据对象
     */
    private JSONObject buildEasyUIViewReturnResult(Object objResult) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("columns", this.buildDataGridColumns(objResult));
        jsonObject.put("data", this.buildDataGridData(objResult));
        return jsonObject;
    }

    /**
     * 构建EasyUI视图数据对象
     */
    private void buildEasyUIViewErrorMessage(HttpServletResponse response, Throwable ex) {
        JSONObject error = new JSONObject();
        error.put("isSuccess", false);
        if (StringUtils.isNotBlank(ex.getMessage())) {
            error.put("message", ex.getMessage());
        }
        error.put("stackTrace", getStackTrace(ex));
        writeMessage(response, buildEasyUIViewReturnResult(error).toJSONString());
    }

    /**
     * 构建EasyUI视图数据对象
     */
    private void buildEasyUIViewErrorMessage(HttpServletResponse response, String message) {
        JSONObject error = new JSONObject();
        error.put("isSuccess", false);
        error.put("message", message);
        writeMessage(response, buildEasyUIViewReturnResult(error).toJSONString());
    }


    /**
     * ajax直接输出结果
     */
    private void writeMessage(HttpServletResponse response, String message) {
        PrintWriter out = null;
        try {
            response.setContentType("text/html;charset=utf-8");
            out = response.getWriter();
            out.print(message);//修改成功
            out.flush();
        } catch (Throwable e) {
            logger.error("EasyMockMessage error:writeMessage.message=" + message + "  " + e.getMessage(), e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private boolean isNumberType(Class dataType) {
        if (dataType.isAssignableFrom(Integer.class) || dataType.isAssignableFrom(Long.class) || dataType.isAssignableFrom(Double.class)
                || dataType.isAssignableFrom(Float.class)) {
            return true;
        }

        return false;// 基本类型按数字类型
    }

    /**
     * 获取列属性信息
     */
    private JSONArray buildDataGridColumns(Object object) {
        Object objTmp = object;
        JSONArray jsonArrayColumns = new JSONArray();
        if (objTmp == null) {
            return jsonArrayColumns;
        } else if (object instanceof Collection) {
            Collection tmpCollection = (Collection) object;
            if (tmpCollection.size() == 0) {
                return jsonArrayColumns;
            }

            objTmp = tmpCollection.iterator().next();
            if (SupportBaseType.isSupported(objTmp.getClass()) || (objTmp instanceof Collection)) {
                JSONObject json = new JSONObject();
                json.put("result", "");
                objTmp = json;
            }
        }

        Map<String, Object> map = (JSONObject) JSONObject.toJSON(objTmp);
        Map<String, Object> treeMap = new TreeMap<String, Object>();
        treeMap.putAll(map);
        if (treeMap.size() > 0) {
            Set<Map.Entry<String, Object>> entrySet = treeMap.entrySet();
            for (Map.Entry<String, Object> entry : entrySet) {
                JSONObject jsonColumn = new JSONObject();
                jsonColumn.put("field", entry.getKey());
                jsonColumn.put("title", entry.getKey());
                jsonColumn.put("width", 100);
                jsonColumn.put("align", "left");
                jsonArrayColumns.add(jsonColumn);
            }
        }

        return jsonArrayColumns;
    }

    private String buildDataGridData(Object object) {
        Object objTmp = object;
        JSONArray jsonArrayData = new JSONArray();
        List<Object> listData = new ArrayList<Object>();
        if (objTmp == null) {
            return jsonArrayData.toJSONString();
        } else if (object instanceof Collection) {
            Collection tmpCollection = (Collection) object;
            if (tmpCollection.size() == 0) {
                return jsonArrayData.toJSONString();
            }

            Iterator iterator = tmpCollection.iterator();
            objTmp = iterator.next();
            if (SupportBaseType.isSupported(objTmp.getClass()) || (objTmp instanceof Collection)) {
                JSONObject json = new JSONObject();
                json.put("result", objTmp);
                listData.add(json);

                while (iterator.hasNext()) {
                    objTmp = iterator.next();
                    json = new JSONObject();
                    json.put("result", objTmp);
                    listData.add(json);
                }
            } else {
                listData.addAll(tmpCollection);
            }
        } else {
            listData.add(objTmp);
        }

        return JSONArray.toJSONStringWithDateFormat(listData, "yyyy-MM-dd HH:mm:ss", SerializerFeature.WriteNonStringValueAsString);
    }

    private static String getStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        e.printStackTrace(pw);
        String stackTraceString = sw.getBuffer().toString();
        pw.close();
        try {
            sw.close();
        } catch (IOException e1) {
            // Do Nothing
        }

        return stackTraceString;
    }
}
