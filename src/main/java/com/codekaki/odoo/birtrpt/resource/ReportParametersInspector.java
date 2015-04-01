package com.codekaki.odoo.birtrpt.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IParameterDefn;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IParameterGroupDefn;
import org.eclipse.birt.report.engine.api.IParameterSelectionChoice;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.ReportRunner;

/**
 * @author phuihock
 * @see http://eclipse.org/birt/documentation/integrating/reapi.php
 */
public class ReportParametersInspector {
    private static final DateFormat DEFAULT_SERVER_DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final DateFormat DEFAULT_SERVER_DATE = new SimpleDateFormat("yyyy-MM-dd");
    
    private static final DateFormat DEFAULT_SERVER_TIME = new SimpleDateFormat("HH:mm:ss");
    
    static protected Logger logger = Logger.getLogger( ReportRunner.class .getName( ));
    
    IReportEngine reportEngine = null;
    
    public ReportParametersInspector(IReportEngine reportEngine) {
        this.reportEngine = reportEngine;
    }

    List<IParameterDefn> getParameters(IReportRunnable reportRunnable){
        IGetParameterDefinitionTask paramDefTask = reportEngine.createGetParameterDefinitionTask(reportRunnable);
        Collection<IParameterDefn> coll = paramDefTask.getParameterDefns(true);
        java.util.Iterator<IParameterDefn> it = coll.iterator();
        List<IParameterDefn> parameters = new ArrayList<IParameterDefn>();
        while(it.hasNext()){
            IParameterDefn paramDef = it.next();
            if(paramDef instanceof IParameterGroupDefn){
                IParameterGroupDefn paramGroupDef = (IParameterGroupDefn) paramDef;
                List<IParameterDefn> params = paramGroupDef.getContents();
                Iterator<IParameterDefn> paramsIt = params.iterator();
                while(paramsIt.hasNext()){
                    parameters.add(paramsIt.next());
                } 
            }
            else{
                parameters.add(paramDef);
            }
        }
        return parameters;
    }
    
    JsonArray enumParameters(IReportRunnable reportRunnable){
        IGetParameterDefinitionTask paramDefTask = reportEngine.createGetParameterDefinitionTask(reportRunnable);
        JsonArrayBuilder parametersBuilder = Json.createArrayBuilder();
        for(IParameterDefnBase param : getParameters(reportRunnable)){
            JsonObjectBuilder paramObjBuilder = Json.createObjectBuilder();
            enumParameter(paramDefTask, param, paramObjBuilder);
            parametersBuilder.add(paramObjBuilder);
        }
        return parametersBuilder.build();
    }
    
    String lookupFieldType(int dataType){
        /*
        _type = 'unknown'
        _type = 'boolean'
        _type = 'integer'
        _type = 'char'
        _type = 'text'
        _type = 'float'
        _type = 'date'
        _type = 'datetime'
         */
        switch(dataType){
        case IParameterDefn.TYPE_BOOLEAN:
            return "boolean";
        case IParameterDefn.TYPE_INTEGER:
            return "integer";
        case IParameterDefn.TYPE_DECIMAL:
        case IParameterDefn.TYPE_FLOAT:
            return "float";
        case IParameterDefn.TYPE_DATE_TIME:
            return "datetime";
        case IParameterDefn.TYPE_DATE:
            return "date";
        case IParameterDefn.TYPE_TIME:
            return "time";
        case IParameterDefn.TYPE_STRING:
            return "char";
        default:
            return "char";
        }
    }
        
    /**
     * Given value v, this method lookups the matching javax.json (JSR-353 JSONP) class. 
     * @param param report parameter
     * @param v value
     * @return the class the value v mapped to
     */
    private Class<?> lookupJsonMethodValueArgClass(IParameterDefnBase param, Object v){
        Class[][] mappings = {
                {BigDecimal.class, BigDecimal.class},
                {BigInteger.class, BigInteger.class}, 
                {Boolean.class, boolean.class}, 
                {Double.class, double.class}, 
                {Integer.class, int.class}, 
                {Long.class, long.class}, 
                {String.class, String.class}
        };
        
        for(Class<?>[] map : mappings){
            if(v.getClass() == map[0]){
                return map[1];
            }
        }
                
        return String.class;
    }
    
    private void setDefaultValue(JsonObjectBuilder jsonObjectBuilder, IParameterDefn param, Object defaultValue){
        if(defaultValue != null){
            Class<?> clz = lookupJsonMethodValueArgClass(param, defaultValue);
            if(clz != null){
                try{
                    Method method = JsonObjectBuilder.class.getMethod("add", String.class, clz);
                    if(clz == String.class){
                        // There is no date/time datatype for json object. They are represented as normal string.
                        switch(param.getDataType()){
                        case IParameterDefn.TYPE_DATE_TIME:
                            defaultValue = DEFAULT_SERVER_DATETIME.format(defaultValue);
                            break;
                        case IParameterDefn.TYPE_DATE:
                            defaultValue = DEFAULT_SERVER_DATE.format(defaultValue);
                            break;
                        case IParameterDefn.TYPE_TIME:
                            defaultValue = DEFAULT_SERVER_TIME.format(defaultValue);
                            break;
                        }
                    }
                    
                    method.invoke(jsonObjectBuilder, "defaultValue", defaultValue);
                } catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e){
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
            else{
                logger.log(Level.WARNING, "Can't set default value. There is no add(String, " + defaultValue.getClass().getName() + ") method.");
            }
        }
        else{
            jsonObjectBuilder.addNull("defaultValue");
        }
    }
    
    private void push(JsonArrayBuilder jsonArrayBuilder, IParameterDefnBase param, Object obj){
        if(obj == null){
            jsonArrayBuilder.addNull();
        }
        else{
            Class<?> clz = lookupJsonMethodValueArgClass(param, obj);
            if(clz != null){
                try{
                    Method method = JsonArrayBuilder.class.getMethod("add", clz);
                    method.invoke(jsonArrayBuilder, obj);
                } catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e){
                    e.printStackTrace();
                }
            }
            else{
                logger.log(Level.WARNING, "Can't add array item. There is no add(String, " + obj.getClass().getName() + ") method.");
            }
        }
    }
    
    private void enumParameter(IGetParameterDefinitionTask paramDefTask, IParameterDefnBase paramDefBase, JsonObjectBuilder paramObjBuilder) {
        String name = paramDefBase.getName();
        String promptText = paramDefBase.getPromptText();
        String type = "";
        String fieldType = "";
        Boolean isRequired = true;;
        String helpText = paramDefBase.getHelpText();
        Object defaultValue = null;
        Collection<IParameterSelectionChoice> selectionList = null;

        switch(paramDefBase.getParameterType()){
        case IParameterDefn.SCALAR_PARAMETER:
            type = "scalar";
            
            IScalarParameterDefn param = (IScalarParameterDefn) paramDefBase;
            defaultValue = paramDefTask.getDefaultValue(param);
            fieldType = lookupFieldType(param.getDataType()); 
            setDefaultValue(paramObjBuilder, param, defaultValue);
            
            isRequired = param.isRequired();
        case IParameterDefn.LIST_PARAMETER:
            type = "list";
            break;
        case IParameterDefn.FILTER_PARAMETER:
            type = "filter";
            break;
        default:
            type = "unknown <" + paramDefBase.getParameterType() + ">";
        }
        
        paramObjBuilder.add("name", name);
        paramObjBuilder.add("promptText", promptText != null? promptText : name);
        paramObjBuilder.add("fieldType", fieldType);
        paramObjBuilder.add("required", isRequired);
        paramObjBuilder.add("helpText", helpText != null? helpText : "");
        
        selectionList = paramDefTask.getSelectionList(name);
        if(selectionList != null){
            JsonArrayBuilder selectionListBuilder = Json.createArrayBuilder();
            JsonArrayBuilder choiceBuilder = Json.createArrayBuilder();
            for(IParameterSelectionChoice choice : selectionList){
                push(choiceBuilder, paramDefBase, choice.getValue());
                push(choiceBuilder, paramDefBase, choice.getLabel());
                logger.log(Level.FINE, "\t" + choice.getValue() + ": " + choice.getLabel());
                selectionListBuilder.add(choiceBuilder);
            }
            paramObjBuilder.add("selection", selectionListBuilder);
        }
        logger.log(Level.FINE, name + ", " + type + "<" + fieldType + ">, [" + defaultValue + "]");
    }
}
