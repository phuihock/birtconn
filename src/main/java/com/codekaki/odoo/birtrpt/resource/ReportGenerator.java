package com.codekaki.odoo.birtrpt.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.ICascadingParameterGroup;
import org.eclipse.birt.report.engine.api.IParameterDefn;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IParameterGroupDefn;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.ReportRunner;

import com.ibm.icu.util.TimeZone;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class ReportGenerator {
    static protected Logger logger = Logger.getLogger(ReportRunner.class.getName());

    IReportEngine reportEngine;
    String encoding;
    String locale;
    final Map<String, Class<?>> paramsType;

    public ReportGenerator(IReportEngine reportEngine, String encoding, String locale) {
        this.reportEngine = reportEngine;
        this.encoding = encoding;
        this.locale = locale;

        paramsType = new HashMap<String, Class<?>>();
        paramsType.put("boolean", Boolean.class);
        paramsType.put("integer", Integer.class);
        paramsType.put("float", Float.class);
        paramsType.put("decimal", BigDecimal.class);
        paramsType.put("datetime", Timestamp.class);
        paramsType.put("date", Date.class);
        paramsType.put("time", Time.class);
    }

    private Class<?> lookupParameterType(String fieldType) {
        Class<?> type = paramsType.get(fieldType);
        if(type == null){
            return String.class;
        }
        return type;
    }

    void run(IReportRunnable reportRunnable, String format, TimeZone timeZone, String htmlType, String targetFile, JsonObject values)
            throws EngineException {

        ReportParametersInspector inspector = new ReportParametersInspector(reportEngine);
        List<IParameterDefnBase> parameters = inspector.getParameters(reportRunnable);

        Map<String, String> map = new HashMap<String, String>(parameters.size());
        flattenParameters(inspector, parameters, map);

        IRunAndRenderTask task = reportEngine.createRunAndRenderTask(reportRunnable);
        for(IParameterDefnBase param: parameters){
            String name = param.getName();
            if(values.containsKey(name)){
                setParameter(task, name, map.get(name), values.get(name));
            }
        }

        // set report render options
        IRenderOption options = new RenderOption();

        options.setOutputFormat(format);

        // setup the output file
        options.setOutputFileName(targetFile);

        // setup the application context
        if (format.equalsIgnoreCase("html")) {
            HTMLRenderOption htmlOptions = new HTMLRenderOption(options);
            if ("ReportletNoCSS".equals(htmlType)) {
                htmlOptions.setEmbeddable(true);
            }
            // setup the output encoding
            htmlOptions.setUrlEncoding(encoding);
            htmlOptions.setHtmlPagination(true);
            htmlOptions.setImageDirectory("image"); //$NON-NLS-1$
        }

        // set the render options
        task.setRenderOption(options);

        // setup the locale
        task.setLocale(getLocale(locale));
        task.setTimeZone(timeZone);
        task.run();
        task.close();
    }

    @SuppressWarnings("unchecked")
    private void flattenParameters(ReportParametersInspector inspector, List<IParameterDefnBase> parameters, Map<String, String> map) {
        for(IParameterDefnBase param : parameters){
            if(param instanceof IParameterGroupDefn){
                IParameterGroupDefn p = (IParameterGroupDefn) param;
                if(p instanceof ICascadingParameterGroup){
                    throw new NotImplementedException();
                }
                flattenParameters(inspector, p.getContents(), map);
            }
            else{
                IParameterDefn p = (IParameterDefn) param;
                map.put(param.getName(), inspector.lookupFieldType(p.getDataType()));
            }
        }
    }

    private void setParameter(IRunAndRenderTask task, String name, String fieldType, JsonValue val) {
        Class<?> clz = lookupParameterType(fieldType);
        Object value = extractJsonValue(val, clz);
        if(value instanceof Object[]){
            Object[] values = (Object[]) value;
            String[] displayText = new String[values.length];
            for(int i = 0; i < displayText.length; i++){
                displayText[i] = String.valueOf(values[i]);
            }
            task.setParameter(name, values, displayText);
        }
        else {
            task.setParameter(name, value, String.valueOf(value));
        }
    }

    private Object extractJsonValue(JsonValue val, Class<?> clz) {
        Object actualValue;
        switch(val.getValueType()){
        case FALSE:
            actualValue = false;
            break;
        case TRUE:
            actualValue = true;
            break;
        case NULL:
            actualValue = null;
            break;
        case NUMBER:
            JsonNumber number = (JsonNumber) val;
            if(number.isIntegral()){
                actualValue = number.intValue();
            }
            else{
                actualValue = number.doubleValue();
            }
            break;
        case ARRAY:
            JsonArray arr = (JsonArray) val;
            Object[] values = new Object[arr.size()];

            for(int i = 0, j = arr.size(); i < j; i++){
                values[i] = extractJsonValue(arr.get(i), clz);
            }
            actualValue = values;
            break;
        default:
            JsonString unknown = (JsonString) val;
            actualValue = unknown.getString();
            if(clz != String.class){
                try {
                    Method mValueOf = clz.getMethod("valueOf", String.class);
                    actualValue = mValueOf.invoke(clz, actualValue);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        }
        return actualValue;
    }

    private Locale getLocale(String locale) {
        int index = locale.indexOf('_');
        if (index != -1) {
            // e.g, zh_CN (language_country)
            String language = locale.substring(0, index);
            String country = locale.substring(index + 1);
            return new Locale(language, country);
        }

        // e.g, en (language)
        return new Locale(locale);
    }
}
