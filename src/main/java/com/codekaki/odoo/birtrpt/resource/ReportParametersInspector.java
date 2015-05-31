package com.codekaki.odoo.birtrpt.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.eclipse.birt.report.engine.api.ICascadingParameterGroup;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IParameterDefn;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IParameterGroupDefn;
import org.eclipse.birt.report.engine.api.IParameterSelectionChoice;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.ReportRunner;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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

    private final Map<Class<?>, Class<?>> mappings;

    public ReportParametersInspector(IReportEngine reportEngine) {
        this.reportEngine = reportEngine;

        mappings = new HashMap<Class<?>, Class<?>>();
        mappings.put(BigDecimal.class, BigDecimal.class);
        mappings.put(BigInteger.class, BigInteger.class);
        mappings.put(Boolean.class, boolean.class);
        mappings.put(Double.class, double.class);
        mappings.put(Integer.class, int.class);
        mappings.put(Long.class, long.class);
        mappings.put(String.class, String.class);
    }

    /**
     * Given value v, this method lookups the matching javax.json (JSR-353 JSONP) class.
     * @param param report parameter
     * @param v value
     * @return the class the value v mapped to
     */
    private Class<?> lookupJsonArgType(Object v){
        if(mappings.containsKey(v.getClass())){
            return mappings.get(v.getClass());
        }
        return String.class;
    }

    List<IParameterDefnBase> getParameters(IReportRunnable reportRunnable){
        IGetParameterDefinitionTask paramDefTask = reportEngine.createGetParameterDefinitionTask(reportRunnable);

        @SuppressWarnings("unchecked")
        Collection<IParameterDefnBase> coll = paramDefTask.getParameterDefns(true);
        java.util.Iterator<IParameterDefnBase> it = coll.iterator();
        List<IParameterDefnBase> parameters = new ArrayList<IParameterDefnBase>();
        while(it.hasNext()){
            IParameterDefnBase paramDef = it.next();
            if(paramDef instanceof IParameterGroupDefn){
                IParameterGroupDefn paramGroupDef = (IParameterGroupDefn) paramDef;
                if(paramGroupDef instanceof ICascadingParameterGroup){
                    throw new NotImplementedException();
                }
                parameters.add(paramGroupDef);
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
        paramDefTask.close();
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

    private void setDefaultValue(JsonObjectBuilder jsonObjectBuilder, IParameterDefn param, Object defaultValue){
        if(defaultValue instanceof Object[]){
            JsonArrayBuilder ab = Json.createArrayBuilder();
            for(Object v : (Object[]) defaultValue){
                push(ab, v);
            }
            jsonObjectBuilder.add("defaultValue", ab);
        }
        else if(defaultValue == null){
            jsonObjectBuilder.addNull("defaultValue");
        }
        else{
            Class<?> clz = lookupJsonArgType(defaultValue);
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
    }

    private void push(JsonArrayBuilder jsonArrayBuilder, Object obj){
        if(obj == null){
            jsonArrayBuilder.addNull();
        }
        else{
            Class<?> clz = lookupJsonArgType(obj);
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

    @SuppressWarnings("unchecked")
    private void enumParameterGroup(IGetParameterDefinitionTask paramDefTask, IParameterGroupDefn paramGroupDefn, JsonObjectBuilder paramObjBuilder) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for(IParameterDefnBase paramDef : (List<IParameterDefnBase>) paramGroupDefn.getContents()){
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            enumParameter(paramDefTask, paramDef, objectBuilder);
            arrayBuilder.add(objectBuilder);
        }
        paramObjBuilder.add("parameters", arrayBuilder);
    }

    @SuppressWarnings("unchecked")
    private void enumParameter(IGetParameterDefinitionTask paramDefTask, IParameterDefnBase paramDefBase, JsonObjectBuilder paramObjBuilder) {
        String name = paramDefBase.getName();
        String displayName = paramDefBase.getDisplayName();
        String promptText = paramDefBase.getPromptText();
        String type = "";
        String fieldType = "";
        Boolean isRequired = true;;
        String helpText = paramDefBase.getHelpText();
        Object defaultValue = null;
        Collection<IParameterSelectionChoice> selectionList = null;

        paramObjBuilder.add("name", name);
        paramObjBuilder.add("promptText", (promptText != null)? promptText : (displayName != null)? displayName : name);
        paramObjBuilder.add("helpText", helpText != null? helpText : "");

        switch(paramDefBase.getParameterType()){
        case IParameterDefn.CASCADING_PARAMETER_GROUP:
            throw new NotImplementedException();
        case IParameterDefn.PARAMETER_GROUP:
            type = "group";
            break;
        case IParameterDefn.LIST_PARAMETER:
            type = "list";
            break;
        case IParameterDefn.FILTER_PARAMETER:
            type = "filter";
            break;
        case IParameterDefn.SCALAR_PARAMETER:
            IScalarParameterDefn param = (IScalarParameterDefn) paramDefBase;

            type = "scalar/" + param.getScalarParameterType();
            defaultValue = paramDefTask.getDefaultValue(param);
            fieldType = lookupFieldType(param.getDataType());
            setDefaultValue(paramObjBuilder, param, defaultValue);
            isRequired = param.isRequired();

            paramObjBuilder.add("fieldType", fieldType);
            paramObjBuilder.add("required", isRequired);

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
            break;
        default:
            type = "unknown <" + paramDefBase.getParameterType() + ">";
        }

        paramObjBuilder.add("type", type);
        if(paramDefBase instanceof IParameterGroupDefn){
            enumParameterGroup(paramDefTask, (IParameterGroupDefn) paramDefBase, paramObjBuilder);
        }

        logger.log(Level.FINE, name + ", " + type + "<" + fieldType + ">, [" + defaultValue + "]");
    }
}
