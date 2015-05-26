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
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

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

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.ibm.icu.util.TimeZone;

public class ReportGenerator {
    static protected Logger logger = Logger.getLogger(ReportRunner.class.getName());

    IReportEngine reportEngine;
    String encoding;
    String locale;
    Map<String, Class<?>> paramsType;

    public ReportGenerator(IReportEngine reportEngine, String encoding, String locale) {
        this.reportEngine = reportEngine;
        this.encoding = encoding;
        this.locale = locale;
    }

    private Class<?> lookupParamType(String fieldType) {
        if(paramsType == null){
            paramsType = new HashMap<String, Class<?>>();
            paramsType.put("boolean", Boolean.class); 
            paramsType.put("integer", Integer.class); 
            paramsType.put("float", Float.class);
            paramsType.put("decimal", BigDecimal.class); 
            paramsType.put("datetime", Timestamp.class); 
            paramsType.put("date", Date.class);
            paramsType.put("time", Time.class); 
        }
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
        for (Entry<String, JsonValue> entry : values.entrySet()) {
            String name = entry.getKey();
            setParameter(task, name, map.get(name), entry.getValue());
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
        Class<?> clz = lookupParamType(fieldType);
        Object value = null;
        switch(val.getValueType()){
        case FALSE:
            value = false;
            break;
        case TRUE:
            value = true;
            break;
        case NULL:
            value = null;
            break;
        case NUMBER:
            JsonNumber number = (JsonNumber) val;
            if(number.isIntegral()){
                value = number.intValue();
            }
            else{
                value = number.doubleValue();
            }
            break;
        default:
            JsonString string = (JsonString) val;
            value = string.getString();
            if(!fieldType.equals("char")){
                try {
                    Method mValueOf = clz.getMethod("valueOf", String.class);
                    value = mValueOf.invoke(clz, value);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
            break;
        }
        task.setParameter(name, value, String.valueOf(value));
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
