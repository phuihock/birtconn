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
    
    private String setDefaultValue(JsonObjectBuilder jsonObjectBuilder, IParameterDefn param, Object defaultValue){
        /*
        _type = 'unknown'
        _type = 'boolean'
        _type = 'integer'
        _type = 'reference'
        _type = 'char'
        _type = 'text'
        _type = 'html'
        _type = 'float'
        _type = 'date'
        _type = 'datetime'
        _type = 'binary'
        _type = 'selection'
         */
        String dataType = "";
        switch(param.getDataType()){
        case IParameterDefn.TYPE_ANY:
            dataType = "unknown";
            break;
        case IParameterDefn.TYPE_STRING:
            dataType = "char";
            break;
        case IParameterDefn.TYPE_FLOAT:
            dataType = "float";
            break;
        case IParameterDefn.TYPE_DECIMAL:
            dataType = "float";
            break;
        case IParameterDefn.TYPE_DATE_TIME:
            dataType = "datetime";
            defaultValue = timestampFormat.format(defaultValue);  // ISO 8601
            break;
        case IParameterDefn.TYPE_BOOLEAN:
            dataType = "boolean";
            break;
        case IParameterDefn.TYPE_INTEGER:
            dataType = "integer";
            break;
        case IParameterDefn.TYPE_DATE:
            dataType = "date";
            defaultValue = dateFormat.format(defaultValue);  // ISO 8601
            break;
        case IParameterDefn.TYPE_TIME:
            dataType = "datetime";
            defaultValue = timestampFormat.format(defaultValue);  // ISO 8601
            break;
        }
        
        if(defaultValue != null){
            try {
                Method method = JsonObjectBuilder.class.getMethod("add", String.class, defaultValue.getClass());
                method.invoke(jsonObjectBuilder, "defaultValue", defaultValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        else{
            jsonObjectBuilder.addNull("defaultValue");
        }
        return dataType;
    }
    
    private void push(JsonArrayBuilder jsonArrayBuilder, Object obj){
        if(obj == null){
            jsonArrayBuilder.addNull();
        }
        else{
            Class<?>[] argCls = {BigDecimal.class, BigInteger.class, Boolean.class, Double.class, Integer.class, Long.class, String.class};
            boolean hasSuchMethod = false;
            for(Class<?> cls : argCls){
                if(obj.getClass() == cls){
                    hasSuchMethod = true;
                }
            }
            
            if(hasSuchMethod){
                try{
                    Method method = JsonArrayBuilder.class.getMethod("add", obj.getClass());
                    method.invoke(jsonArrayBuilder, obj);
                } catch(NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e){
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void inspectParameter(IGetParameterDefinitionTask paramDefTask, IParameterDefnBase paramDefBase, JsonObjectBuilder paramObjBuilder) {
        String name = paramDefBase.getName();
        String promptText = paramDefBase.getPromptText();
        String type = "";
        String controlType = "";
        String dataType = "";
        Boolean isRequired = true;;
        String helpText = paramDefBase.getHelpText();
        Object defaultValue = null;
        Collection<IParameterSelectionChoice> selectionList = null;

        switch(paramDefBase.getParameterType()){
        case IParameterDefn.SCALAR_PARAMETER:
            type = "scalar";
            
            IScalarParameterDefn param = (IScalarParameterDefn) paramDefBase;
            defaultValue = paramDefTask.getDefaultValue(param);
            dataType = setDefaultValue(paramObjBuilder, param, defaultValue);
            
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
            type = "Unknown <" + paramDefBase.getParameterType() + ">";
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
        paramObjBuilder.add("dataType", dataType);
        logger.log(Level.FINE, name + ", " + type + "<" + dataType + ">, "  + controlType + ",  [" + defaultValue + "]");
    }
}
