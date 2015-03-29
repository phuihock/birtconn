package com.codekaki.odoo.birtrpt.resource;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.birt.core.script.ParameterAttribute;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.ReportRunner;

public class ReportGenerator {
    static protected Logger logger = Logger.getLogger(ReportRunner.class.getName());

    IReportEngine reportEngine;
    String encoding;
    String locale;

    public ReportGenerator(IReportEngine reportEngine, String encoding, String locale) {
        this.reportEngine = reportEngine;
        this.encoding = encoding;
        this.locale = locale;
    }

    void run(IReportRunnable reportRunnable, String format, String htmlType, String targetFile, Map<String, ParameterAttribute> inputValues) throws EngineException {
        IRunAndRenderTask task = reportEngine.createRunAndRenderTask(reportRunnable);
        for (Map.Entry<String, ParameterAttribute> entry : inputValues.entrySet()) {
            String paraName = entry.getKey();
            ParameterAttribute pa = entry.getValue();
            Object valueObject = pa.getValue();
            if (valueObject instanceof Object[]) {
                Object[] valueArray = (Object[]) valueObject;
                String[] displayTextArray = (String[]) pa.getDisplayText();
                task.setParameter(paraName, valueArray, displayTextArray);
            } else {
                task.setParameter(paraName, pa.getValue(), (String) pa.getDisplayText());
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
            if ("ReportletNoCSS".equals(htmlType)){
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

        task.run();
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
