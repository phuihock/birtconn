package com.codekaki.odoo.birtrpt.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IParameterDefn;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IParameterGroupDefn;
import org.eclipse.birt.report.engine.api.IParameterSelectionChoice;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.ReportRunner;
import org.eclipse.core.internal.registry.RegistryProviderFactory;

/**
 * @author phuihock
 * @see http://eclipse.org/birt/documentation/integrating/reapi.php
 */
public class ReportParametersInspector {
    static protected Logger logger = Logger.getLogger( ReportRunner.class .getName( ));
    
    IReportEngine reportEngine = null;
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    DateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    
    public ReportParametersInspector(IReportEngine reportEngine) {
        this.reportEngine = reportEngine;
    }
    
    IReportRunnable openReportDesign(String report_file) throws EngineException{
        return reportEngine.openReportDesign(report_file);
    }
    
    void shutdown(){
        reportEngine.destroy();
        Platform.shutdown();
        RegistryProviderFactory.releaseDefault();  // bugzilla 351052
    }
    
    JsonArray inspectParameters(IReportRunnable reportRunnable){
        IGetParameterDefinitionTask paramDefTask = reportEngine.createGetParameterDefinitionTask(reportRunnable);
        Collection<IParameterDefnBase> coll = paramDefTask.getParameterDefns(true);
        java.util.Iterator<IParameterDefnBase> it = coll.iterator();
        JsonArrayBuilder parametersBuilder = Json.createArrayBuilder();
        
        while(it.hasNext()){
            JsonObjectBuilder paramObjBuilder = Json.createObjectBuilder();
            IParameterDefnBase paramDefBase = it.next();
            if(paramDefBase instanceof IParameterGroupDefn){
                IParameterGroupDefn paramGroupDef = (IParameterGroupDefn) paramDefBase;
                List<IParameterDefn> params = paramGroupDef.getContents();
                Iterator<IParameterDefn> paramsIt = params.iterator();
                while(paramsIt.hasNext()){
                    inspectParameter(paramDefTask, paramsIt.next(), paramObjBuilder);
                } 
            }
            else{
                inspectParameter(paramDefTask, paramDefBase, paramObjBuilder);
            }
            parametersBuilder.add(paramObjBuilder);
        }
        
        return parametersBuilder.build();
    }
    
    private String lookupFieldType(IParameterDefn param){
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
        switch(param.getDataType()){
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
    
    private Class<?> lookupJsonMethodValueArgClass(Object v){
        Class<?>[][] classes = {
                {BigDecimal.class, BigDecimal.class},
                {BigInteger.class, BigInteger.class}, 
                {Boolean.class, boolean.class}, 
                {Double.class, double.class}, 
                {Integer.class, int.class}, 
                {Long.class, long.class}, 
                {String.class, String.class}
        };
        
        Class<?> clz = Object.class;
        for(Class<?>[] map : classes){
            if(v.getClass() == map[0]){
                return map[1];
            }
        }
        
        return null;
    }
    
    private void setDefaultValue(JsonObjectBuilder jsonObjectBuilder, IParameterDefn param, Object defaultValue){
        if(defaultValue != null){
            Class<?> clz = lookupJsonMethodValueArgClass(defaultValue);
            if(clz != null){
                try{
                    Method method = JsonObjectBuilder.class.getMethod("add", String.class, clz);
                    method.invoke(jsonObjectBuilder, "defaultValue", defaultValue);
                } catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e){
                    e.printStackTrace();
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
    
    private void push(JsonArrayBuilder jsonArrayBuilder, Object obj){
        if(obj == null){
            jsonArrayBuilder.addNull();
        }
        else{
            Class<?> clz = lookupJsonMethodValueArgClass(obj);
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
    
    private void inspectParameter(IGetParameterDefinitionTask paramDefTask, IParameterDefnBase paramDefBase, JsonObjectBuilder paramObjBuilder) {
        String name = paramDefBase.getName();
        String promptText = paramDefBase.getPromptText();
        String type = "";
        String controlType = "text box";
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
            fieldType = lookupFieldType(param); 
            setDefaultValue(paramObjBuilder, param, defaultValue);
            
            switch(param.getControlType()){
            case IScalarParameterDefn.LIST_BOX:
                controlType = "list box";
                break;
            case IScalarParameterDefn.CHECK_BOX:
                controlType = "check box";
                break;
            case IScalarParameterDefn.TEXT_BOX:
                controlType = "text box";
                break;
            case IScalarParameterDefn.RADIO_BUTTON:
                controlType = "radio button";
                break;
            default:
                controlType = "text box";
                break;
            }
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
        paramObjBuilder.add("type", type);
        paramObjBuilder.add("required", isRequired);
        paramObjBuilder.add("helpText", helpText != null? helpText : "");
        
        selectionList = paramDefTask.getSelectionList(name);
        if(selectionList != null){
            JsonArrayBuilder selectionListBuilder = Json.createArrayBuilder();
            JsonArrayBuilder choiceBuilder = Json.createArrayBuilder();
            for(IParameterSelectionChoice choice : selectionList){
                push(choiceBuilder, choice.getValue());
                push(choiceBuilder, choice.getLabel());
                logger.log(Level.FINE, "\t" + choice.getValue() + ": " + choice.getLabel());
                selectionListBuilder.add(choiceBuilder);
            }
            paramObjBuilder.add("selection", selectionListBuilder);
        }
        paramObjBuilder.add("controlType", controlType);
        paramObjBuilder.add("fieldType", fieldType);
        logger.log(Level.FINE, name + ", " + type + "<" + fieldType + ">, "  + controlType + ",  [" + defaultValue + "]");
    }
}
